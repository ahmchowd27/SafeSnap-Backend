package com.safesnap.backend.service

import com.google.cloud.vision.v1.*
import com.google.protobuf.ByteString
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.io.IOException

@Service
class GoogleVisionService(
    @Value("\${google.vision.project-id:}") private val projectId: String,
    @Value("\${google.vision.credentials-path:}") private val credentialsPath: String,
    @Value("\${google.vision.credentials-json:}") private val credentialsJson: String,
    private val metricsService: MetricsService
) {

    private val logger = LoggerFactory.getLogger(GoogleVisionService::class.java)

    fun analyzeImage(imageBytes: ByteArray): ImageAnalysisResult {
        metricsService.recordVisionApiCall()
        return metricsService.timeImageProcessing {
            try {
                logger.info("Analyzing image with Google Vision API (project: $projectId)")

                val vision = createVisionClient()

                vision.use { client ->
                    val imgBytes = ByteString.copyFrom(imageBytes)
                    val img = Image.newBuilder().setContent(imgBytes).build()

                    val features = listOf(
                        Feature.newBuilder().setType(Feature.Type.LABEL_DETECTION).setMaxResults(20).build(),
                        Feature.newBuilder().setType(Feature.Type.OBJECT_LOCALIZATION).setMaxResults(10).build(),
                        Feature.newBuilder().setType(Feature.Type.TEXT_DETECTION).setMaxResults(5).build(),
                        Feature.newBuilder().setType(Feature.Type.SAFE_SEARCH_DETECTION).build()
                    )

                    val request = AnnotateImageRequest.newBuilder()
                        .addAllFeatures(features)
                        .setImage(img)
                        .build()

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

                    val allLabels = imageResponse.labelAnnotationsList.map {
                        LabelInfo(it.description, it.score)
                    }

                    val safetyTags = filterSafetyRelevantLabels(allLabels)

                    val objectsDetected = imageResponse.localizedObjectAnnotationsList.map {
                        ObjectInfo(it.name, it.score)
                    }

                    val textDetected = imageResponse.textAnnotationsList
                        .firstOrNull()?.description ?: ""

                    val avgConfidence = if (allLabels.isNotEmpty()) {
                        allLabels.map { it.confidence.toDouble() }.average()
                    } else 0.0

                    logger.info("Vision API analysis completed: ${safetyTags.size} safety tags, ${allLabels.size} total labels, ${objectsDetected.size} objects")

                    ImageAnalysisResult(
                        success = true,
                        safetyTags = safetyTags,
                        allLabels = allLabels,
                        objectsDetected = objectsDetected,
                        textDetected = textDetected.take(500),
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

    private fun createVisionClient(): ImageAnnotatorClient {
        return when {
            credentialsJson.isNotBlank() -> {
                try {
                    logger.info("Using base64-encoded JSON credentials")
                    val decodedJson = java.util.Base64.getDecoder().decode(credentialsJson)
                    val credentialsStream = java.io.ByteArrayInputStream(decodedJson)
                    val credentials = com.google.auth.oauth2.ServiceAccountCredentials.fromStream(credentialsStream)
                    ImageAnnotatorClient.create(
                        ImageAnnotatorSettings.newBuilder()
                            .setCredentialsProvider { credentials }
                            .build()
                    )
                } catch (e: Exception) {
                    logger.warn("Failed to parse base64 JSON credentials: ${e.message}, using default credentials")
                    ImageAnnotatorClient.create()
                }
            }

            credentialsPath.isNotBlank() -> {
                val credentialsFile = java.io.File(credentialsPath)
                if (credentialsFile.exists()) {
                    logger.info("Using credentials file: $credentialsPath")
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
            }

            else -> {
                logger.info("No explicit credentials provided, using Application Default Credentials")
                ImageAnnotatorClient.create()
            }
        }
    }

    private fun filterSafetyRelevantLabels(allLabels: List<LabelInfo>): List<String> {
        val safetyKeywords = setOf(
            "hard hat", "helmet", "safety vest", "safety glasses", "gloves", "boots",
            "harness", "safety gear", "protective equipment", "high visibility", "vest",
            "protective clothing", "ear protection", "face shield",
            "construction", "building", "scaffold", "scaffolding", "ladder", "crane",
            "excavator", "bulldozer", "machinery", "equipment", "tool", "industrial",
            "factory", "warehouse", "construction site", "work site", "job site",
            "construction worker", "worker", "operator",
            "hazard", "danger", "warning", "caution", "spill", "leak", "fire",
            "electrical", "chemical", "toxic", "slippery", "wet floor", "falling",
            "sharp", "broken", "damaged", "unsafe", "risk", "accident", "injury",
            "exposed", "unprotected", "unstable",
            "barrier", "fence", "sign", "cone", "tape", "rope", "guard rail",
            "safety barrier", "warning sign", "caution tape", "safety cone",
            "barricade", "perimeter", "restricted area", "authorized personnel",
            "workplace", "office", "floor", "ceiling", "wall", "door", "window",
            "stairs", "ramp", "platform", "walkway", "entrance", "exit",
            "loading dock", "storage area", "confined space",
            "vehicle", "truck", "forklift", "cart", "conveyor", "transport",
            "heavy machinery", "mobile equipment", "lifting equipment",
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
            .filter { it.confidence > 0.6 }
            .sortedByDescending { it.confidence }
            .map { it.description }
            .distinct()
            .take(10)
    }
}

data class ImageAnalysisResult(
    val success: Boolean,
    val safetyTags: List<String>,
    val allLabels: List<LabelInfo>,
    val objectsDetected: List<ObjectInfo>,
    val textDetected: String,
    val confidenceScore: Double,
    val errorMessage: String?
)

data class LabelInfo(
    val description: String,
    val confidence: Float
)

data class ObjectInfo(
    val name: String,
    val confidence: Float
)
