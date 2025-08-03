package com.safesnap.backend.service

import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class GoogleVisionService(
    @Value("\${google.vision.enabled:true}") private val visionEnabled: Boolean,
    @Value("\${google.vision.mock-mode:true}") private val mockMode: Boolean,
    @Value("\${google.vision.project-id:}") private val projectId: String,
    @Value("\${google.vision.credentials-path:}") private val credentialsPath: String,
    private val metricsService: MetricsService
) {
    
    private val logger = LoggerFactory.getLogger(GoogleVisionService::class.java)
    

    fun analyzeImage(imageBytes: ByteArray): ImageAnalysisResult {
        // Record API call metric
        metricsService.recordVisionApiCall()
        
        return if (!visionEnabled || mockMode) {
            logger.info("Using mock Google Vision API analysis")
            getMockAnalysis()
        } else {
            analyzeImageWithRealApi(imageBytes)
        }
    }
    
    /**
     * Real Google Vision API analysis with credentials from application.properties
     */
    private fun analyzeImageWithRealApi(imageBytes: ByteArray): ImageAnalysisResult {
        return metricsService.timeImageProcessing {
            try {
                logger.info("Analyzing image with Google Vision API (project: $projectId)")
                logger.info("Using credentials from: $credentialsPath")
                
                // Create client with explicit credentials if path is provided
                val vision = if (credentialsPath.isNotBlank()) {
                    val credentialsFile = java.io.File(credentialsPath)
                    if (credentialsFile.exists()) {
                        val credentials = com.google.auth.oauth2.ServiceAccountCredentials
                            .fromStream(java.io.FileInputStream(credentialsFile))
                        ImageAnnotatorClient.create(
                            ImageAnnotatorSettings.newBuilder()
                                .setCredentialsProvider { credentials }
                                .build()
                        )
                    } else {
                        logger.warn("Credentials file not found at: $credentialsPath, using default credentials")
                        ImageAnnotatorClient.create()
                    }
                } else {
                    logger.info("No credentials path specified, using default credentials")
                    ImageAnnotatorClient.create()
                }

                vision.use { client ->

                    val imgBytes = ByteString.copyFrom(imageBytes)
                    val img = Image.newBuilder().setContent(imgBytes).build()
                    

                    val features = listOf(
                        Feature.newBuilder()
                            .setType(Feature.Type.LABEL_DETECTION)
                            .setMaxResults(20)
                            .build(),
                        Feature.newBuilder()
                            .setType(Feature.Type.OBJECT_LOCALIZATION)
                            .setMaxResults(10)
                            .build(),
                        Feature.newBuilder()
                            .setType(Feature.Type.TEXT_DETECTION)
                            .setMaxResults(5)
                            .build(),
                        Feature.newBuilder()
                            .setType(Feature.Type.SAFE_SEARCH_DETECTION)
                            .build()
                    )
                    
                    val request = AnnotateImageRequest.newBuilder()
                        .addAllFeatures(features)
                        .setImage(img)
                        .build()
                    
                    // Perform analysis
                    val response = client.batchAnnotateImages(listOf(request))
                    val imageResponse = response.responsesList[0]
                    
                    if (imageResponse.hasError()) {
                        logger.error("Vision API error: ${imageResponse.error.message}")
                        return@timeImageProcessing ImageAnalysisResult(
                            success = false,
                            safetyTags = emptyList(),
                            allLabels = emptyList(),
                            objectsDetected = emptyList(),
                            textDetected = "",
                            confidenceScore = 0.0,
                            errorMessage = imageResponse.error.message
                        )
                    }
                    
                    // Extract and filter safety-relevant labels
                    val allLabels = imageResponse.labelAnnotationsList.map { 
                        LabelInfo(it.description, it.score) 
                    }
                    
                    val safetyTags = filterSafetyRelevantLabels(allLabels)
                    
                    // Extract objects
                    val objectsDetected = imageResponse.localizedObjectAnnotationsList.map {
                        ObjectInfo(it.name, it.score)
                    }
                    
                    // Extract text (safety signs, warnings, etc.)
                    val textDetected = imageResponse.textAnnotationsList
                        .firstOrNull()?.description ?: ""
                    
                    // Calculate overall confidence
                    val avgConfidence = if (allLabels.isNotEmpty()) {
                        allLabels.map { it.confidence.toDouble() }.average()
                    } else 0.0
                    
                    logger.info("Vision API analysis completed: ${safetyTags.size} safety tags, ${allLabels.size} total labels, ${objectsDetected.size} objects")
                    
                    return@timeImageProcessing ImageAnalysisResult(
                        success = true,
                        safetyTags = safetyTags,
                        allLabels = allLabels,
                        objectsDetected = objectsDetected,
                        textDetected = textDetected.take(500), // Limit text length
                        confidenceScore = avgConfidence,
                        errorMessage = null
                    )
                }
                
            } catch (e: IOException) {
                logger.error("Failed to analyze image with Google Vision API", e)
                ImageAnalysisResult(
                    success = false,
                    safetyTags = emptyList(),
                    allLabels = emptyList(),
                    objectsDetected = emptyList(),
                    textDetected = "",
                    confidenceScore = 0.0,
                    errorMessage = "Vision API connection failed: ${e.message}"
                )
            } catch (e: Exception) {
                logger.error("Unexpected error during image analysis", e)
                ImageAnalysisResult(
                    success = false,
                    safetyTags = emptyList(),
                    allLabels = emptyList(),
                    objectsDetected = emptyList(),
                    textDetected = "",
                    confidenceScore = 0.0,
                    errorMessage = "Analysis failed: ${e.message}"
                )
            }
        }
    }
    
    /**
     * Filter labels to identify safety-relevant tags
     */
    private fun filterSafetyRelevantLabels(allLabels: List<LabelInfo>): List<String> {
        val safetyKeywords = setOf(
            // PPE & Safety Equipment
            "hard hat", "helmet", "safety vest", "safety glasses", "gloves", "boots", 
            "harness", "safety gear", "protective equipment", "high visibility", "vest",
            "protective clothing", "ear protection", "face shield",
            
            // Construction & Industrial
            "construction", "building", "scaffold", "scaffolding", "ladder", "crane", 
            "excavator", "bulldozer", "machinery", "equipment", "tool", "industrial", 
            "factory", "warehouse", "construction site", "work site", "job site",
            "construction worker", "worker", "operator",
            
            // Hazards & Dangers
            "hazard", "danger", "warning", "caution", "spill", "leak", "fire",
            "electrical", "chemical", "toxic", "slippery", "wet floor", "falling",
            "sharp", "broken", "damaged", "unsafe", "risk", "accident", "injury",
            "exposed", "unprotected", "unstable",
            
            // Infrastructure & Safety Measures
            "barrier", "fence", "sign", "cone", "tape", "rope", "guard rail",
            "safety barrier", "warning sign", "caution tape", "safety cone",
            "barricade", "perimeter", "restricted area", "authorized personnel",
            
            // Workplace Areas
            "workplace", "office", "floor", "ceiling", "wall", "door", "window",
            "stairs", "ramp", "platform", "walkway", "entrance", "exit",
            "loading dock", "storage area", "confined space",
            
            // Vehicles & Transport
            "vehicle", "truck", "forklift", "cart", "conveyor", "transport",
            "heavy machinery", "mobile equipment", "lifting equipment",
            
            // General Safety Terms
            "safety", "security", "protection", "emergency", "first aid",
            "evacuation", "procedure", "compliance", "regulation", "inspection",
            "maintenance", "repair", "installation"
        )
        
        return allLabels
            .filter { label ->
                val labelLower = label.description.lowercase()
                safetyKeywords.any { keyword ->
                    labelLower.contains(keyword) || keyword.contains(labelLower)
                }
            }
            .filter { it.confidence > 0.6 } // Only high-confidence labels
            .sortedByDescending { it.confidence } // Sort by confidence
            .map { it.description }
            .distinct()
            .take(10) // Limit to top 10 safety tags
    }
    
    /**
     * Provide mock analysis when Google Vision API is not configured or in mock mode
     */
    private fun getMockAnalysis(): ImageAnalysisResult {
        logger.info("Using mock Vision API analysis")
        
        val mockLabels = listOf(
            LabelInfo("Construction site", 0.95f),
            LabelInfo("Hard hat", 0.88f),
            LabelInfo("Safety vest", 0.82f),
            LabelInfo("Industrial equipment", 0.79f),
            LabelInfo("Workplace", 0.76f),
            LabelInfo("Safety gear", 0.73f),
            LabelInfo("Construction worker", 0.70f),
            LabelInfo("Building", 0.68f),
            LabelInfo("Scaffolding", 0.65f),
            LabelInfo("Machinery", 0.62f)
        )
        
        val mockObjects = listOf(
            ObjectInfo("Person", 0.92f),
            ObjectInfo("Building", 0.85f),
            ObjectInfo("Vehicle", 0.78f),
            ObjectInfo("Equipment", 0.71f)
        )
        
        return ImageAnalysisResult(
            success = true,
            safetyTags = listOf(
                "Construction site", "Hard hat", "Safety vest", 
                "Industrial equipment", "Workplace", "Safety gear"
            ),
            allLabels = mockLabels,
            objectsDetected = mockObjects,
            textDetected = "SAFETY FIRST - HARD HATS REQUIRED - AUTHORIZED PERSONNEL ONLY",
            confidenceScore = 0.84,
            errorMessage = null
        )
    }
    
    /**
     * Get service status and configuration
     */
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to visionEnabled,
            "mock_mode" to mockMode,
            "project_id" to projectId.ifBlank { "not_configured" },
            "credentials_path" to credentialsPath.ifBlank { "not_configured" },
            "credentials_file_exists" to if (credentialsPath.isNotBlank()) {
                java.io.File(credentialsPath).exists()
            } else false,
            "status" to if (visionEnabled) {
                if (mockMode) "MOCK_MODE" else "REAL_API"
            } else "DISABLED"
        )
    }
}

/**
 * Result of image analysis
 */
data class ImageAnalysisResult(
    val success: Boolean,
    val safetyTags: List<String>,
    val allLabels: List<LabelInfo>,
    val objectsDetected: List<ObjectInfo>,
    val textDetected: String,
    val confidenceScore: Double,
    val errorMessage: String?
)

/**
 * Individual label with confidence score
 */
data class LabelInfo(
    val description: String,
    val confidence: Float
)

/**
 * Detected object with confidence score
 */
data class ObjectInfo(
    val name: String,
    val confidence: Float
)
