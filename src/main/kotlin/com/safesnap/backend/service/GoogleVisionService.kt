package com.safesnap.backend.service

import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class GoogleVisionService(
    @Value("\${google.application.credentials:}") private val credentialsPath: String
) {
    
    private val logger = LoggerFactory.getLogger(GoogleVisionService::class.java)
    
    /**
     * Analyze image and extract labels/tags relevant to safety incidents
     */
    fun analyzeImage(imageBytes: ByteArray): ImageAnalysisResult {
        return try {
            if (credentialsPath.isBlank()) {
                logger.warn("Google Vision API credentials not configured, using mock analysis")
                return getMockAnalysis()
            }
            
            // Create Vision API client
            ImageAnnotatorClient.create().use { vision ->
                
                // Build the image request
                val imgBytes = ByteString.copyFrom(imageBytes)
                val img = Image.newBuilder().setContent(imgBytes).build()
                
                // Configure analysis features
                val features = listOf(
                    Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).setMaxResults(20).build(),
                    Feature.newBuilder().setType(Feature.Type.OBJECT_LOCALIZATION).setMaxResults(10).build(),
                    Feature.newBuilder().setType(Feature.Type.SAFE_SEARCH_DETECTION).build(),
                    Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).build()
                )
                
                val request = AnnotateImageRequest.newBuilder()
                    .addAllFeatures(features)
                    .setImage(img)
                    .build()
                
                // Perform analysis
                val response = vision.batchAnnotateImages(listOf(request))
                val imageResponse = response.responsesList[0]
                
                if (imageResponse.hasError()) {
                    logger.error("Vision API error: ${imageResponse.error.message}")
                    return ImageAnalysisResult(
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
                
                // Extract text
                val textDetected = imageResponse.textAnnotationsList
                    .firstOrNull()?.description ?: ""
                
                // Calculate overall confidence
                val avgConfidence = allLabels.map { it.confidence }.average()
                
                logger.info("Vision API analysis completed: ${safetyTags.size} safety tags, ${allLabels.size} total labels")
                
                return ImageAnalysisResult(
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
    
    /**
     * Filter labels to identify safety-relevant tags
     */
    private fun filterSafetyRelevantLabels(allLabels: List<LabelInfo>): List<String> {
        val safetyKeywords = setOf(
            // PPE & Safety Equipment
            "hard hat", "helmet", "safety vest", "safety glasses", "gloves", "boots", 
            "harness", "safety gear", "protective equipment", "high visibility",
            
            // Construction & Industrial
            "construction", "building", "scaffold", "ladder", "crane", "excavator",
            "machinery", "equipment", "tool", "industrial", "factory", "warehouse",
            "construction site", "work site",
            
            // Hazards & Dangers
            "hazard", "danger", "warning", "caution", "spill", "leak", "fire",
            "electrical", "chemical", "toxic", "slippery", "wet floor", "falling",
            "sharp", "broken", "damaged", "unsafe", "risk",
            
            // Infrastructure
            "barrier", "fence", "sign", "cone", "tape", "rope", "guard rail",
            "safety barrier", "warning sign", "caution tape",
            
            // Workplace Areas
            "workplace", "office", "floor", "ceiling", "wall", "door", "window",
            "stairs", "ramp", "platform", "walkway", "entrance", "exit",
            
            // Vehicles & Transport
            "vehicle", "truck", "forklift", "cart", "conveyor", "transport",
            
            // General Safety
            "safety", "security", "protection", "emergency", "first aid",
            "evacuation", "procedure", "compliance", "regulation"
        )
        
        return allLabels
            .filter { label ->
                safetyKeywords.any { keyword ->
                    label.description.lowercase().contains(keyword) ||
                    keyword.contains(label.description.lowercase())
                }
            }
            .filter { it.confidence > 0.6 } // Only high-confidence labels
            .map { it.description }
            .distinct()
            .take(10) // Limit to top 10 safety tags
    }
    
    /**
     * Provide mock analysis when Google Vision API is not configured
     */
    private fun getMockAnalysis(): ImageAnalysisResult {
        val mockLabels = listOf(
            LabelInfo("Construction site", 0.95f),
            LabelInfo("Hard hat", 0.88f),
            LabelInfo("Safety vest", 0.82f),
            LabelInfo("Industrial equipment", 0.79f),
            LabelInfo("Workplace", 0.76f)
        )
        
        return ImageAnalysisResult(
            success = true,
            safetyTags = listOf("Construction site", "Hard hat", "Safety vest", "Industrial equipment", "Workplace"),
            allLabels = mockLabels,
            objectsDetected = listOf(
                ObjectInfo("Person", 0.92f),
                ObjectInfo("Building", 0.85f)
            ),
            textDetected = "SAFETY FIRST - HARD HATS REQUIRED",
            confidenceScore = 0.84,
            errorMessage = null
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
