package com.safesnap.backend.service

import com.safesnap.backend.entity.ImageAnalysis
import com.safesnap.backend.repository.ImageAnalysisRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import com.safesnap.backend.config.SafeSnapConstants
import java.time.LocalDateTime
import java.util.*

@Service
class ImageProcessingService(
    private val storageService: StorageService,
    private val claudeService: ClaudeService,
    private val imageAnalysisRepository: ImageAnalysisRepository
) {
    private val logger = LoggerFactory.getLogger(ImageProcessingService::class.java)

    @Async
    fun processIncidentImages(incidentId: UUID, imageUrls: List<String>?) {
        val urls = imageUrls ?: emptyList()
        if (urls.isEmpty()) {
            logger.info("No images to process for incident $incidentId")
            return
        }

        urls.forEach { imageUrl ->
            try {
                processSingleImage(incidentId, imageUrl)
            } catch (e: Exception) {
                logger.error("Error processing image $imageUrl for incident $incidentId", e)
                imageAnalysisRepository.save(createFailedAnalysis(incidentId, imageUrl, "Processing error: ${e.message}"))
            }
        }

        logger.info("Completed processing ${urls.size} images for incident $incidentId")
    }

    fun processSingleImage(incidentId: UUID, imageUrl: String): ImageAnalysis {
        val existing = imageAnalysisRepository.findByIncidentId(incidentId).find { it.imageUrl == imageUrl }
        if (existing != null) return existing

        val imageBytes = downloadImage(imageUrl)
        if (imageBytes.isEmpty()) {
            return imageAnalysisRepository.save(createFailedAnalysis(incidentId, imageUrl, "Failed to download image"))
        }

        val mediaType = detectMediaType(imageUrl)
        val result = claudeService.analyzeImages(
            imageDataList = listOf(mediaType to imageBytes),
            incidentTitle = "Incident $incidentId",
            incidentDescription = "",
            severity = "UNKNOWN",
            locationDescription = null
        )

        val analysis = if (result.success) {
            ImageAnalysis(
                incidentId = incidentId,
                imageUrl = imageUrl,
                tags = result.safetyTags.joinToString(", ").ifEmpty { "No safety tags detected" },
                allLabels = result.oshaViolations.joinToString("; ").ifEmpty { result.summary.take(500) },
                textDetected = result.textDetected,
                confidenceScore = result.confidenceScore,
                processed = true,
                processedAt = LocalDateTime.now()
            )
        } else {
            createFailedAnalysis(incidentId, imageUrl, result.errorMessage)
        }

        return imageAnalysisRepository.save(analysis)
    }

    private fun downloadImage(imageUrl: String): ByteArray {
        return try {
            if (imageUrl.isBlank()) return byteArrayOf()
            if (!storageService.fileExists(imageUrl)) {
                logger.warn("Image not found in storage: $imageUrl")
                return byteArrayOf()
            }
            val bytes = storageService.downloadFileAsBytes(imageUrl)
            logger.info("Downloaded ${bytes.size} bytes for $imageUrl")
            bytes
        } catch (e: Exception) {
            logger.error("Failed to download image: $imageUrl", e)
            byteArrayOf()
        }
    }

    private fun detectMediaType(imageUrl: String): String {
        return when {
            imageUrl.endsWith(".png", ignoreCase = true) -> "image/png"
            imageUrl.endsWith(".gif", ignoreCase = true) -> "image/gif"
            imageUrl.endsWith(".webp", ignoreCase = true) -> "image/webp"
            else -> "image/jpeg"
        }
    }

    private fun createFailedAnalysis(incidentId: UUID, imageUrl: String, errorMessage: String?): ImageAnalysis {
        return ImageAnalysis(
            incidentId = incidentId,
            imageUrl = imageUrl,
            tags = "PROCESSING_FAILED",
            allLabels = "Error: ${errorMessage ?: "Unknown error"}",
            processed = false,
            processedAt = LocalDateTime.now()
        )
    }

    fun getProcessingStats(): ImageProcessingStats {
        val total = imageAnalysisRepository.count()
        val success = imageAnalysisRepository.countByProcessedTrue()
        val failed = imageAnalysisRepository.countByProcessedFalse()
        return ImageProcessingStats(
            totalImagesProcessed = total,
            successfulAnalyses = success,
            failedAnalyses = failed,
            successRate = if (total > 0) (success.toDouble() / total) * SafeSnapConstants.PERCENTAGE_MULTIPLIER else 0.0
        )
    }

    fun getIncidentAnalyses(incidentId: UUID): List<ImageAnalysis> {
        return imageAnalysisRepository.findByIncidentIdOrderByProcessedAtDesc(incidentId)
    }
}

data class ImageProcessingStats(
    val totalImagesProcessed: Long,
    val successfulAnalyses: Long,
    val failedAnalyses: Long,
    val successRate: Double
)
