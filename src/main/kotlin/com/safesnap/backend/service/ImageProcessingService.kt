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
    private val googleVisionService: GoogleVisionService,
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

        val analyses = mutableListOf<ImageAnalysis>()
        urls.forEach { imageUrl ->
            try {
                val analysis = processSingleImage(incidentId, imageUrl)
                analyses.add(analysis)
            } catch (e: Exception) {
                logger.error("Error processing image $imageUrl", e)
                val failed = createFailedAnalysis(incidentId, imageUrl, "Processing error: ${e.message}")
                analyses.add(imageAnalysisRepository.save(failed))
            }
        }

        logger.info("Completed processing ${analyses.size} images for incident $incidentId")
    }

    fun processSingleImage(incidentId: UUID, imageUrl: String): ImageAnalysis {
        try {
            val existing = imageAnalysisRepository.findByIncidentId(incidentId)
                .find { it.imageUrl == imageUrl }
            if (existing != null) return existing

            val imageBytes = downloadImageFromS3(imageUrl)
            if (imageBytes.isEmpty()) {
                return imageAnalysisRepository.save(
                    createFailedAnalysis(incidentId, imageUrl, "Failed to download")
                )
            }

            val result = googleVisionService.analyzeImage(imageBytes)
            val analysis = if (result.success) {
                createSuccessfulAnalysis(incidentId, imageUrl, result)
            } else {
                createFailedAnalysis(incidentId, imageUrl, result.errorMessage)
            }

            return imageAnalysisRepository.save(analysis)
        } catch (e: Exception) {
            logger.error("Failed to process image $imageUrl", e)
            return imageAnalysisRepository.save(
                createFailedAnalysis(incidentId, imageUrl, "Processing error: ${e.message}")
            )
        }
    }

    private fun downloadImageFromS3(s3Url: String): ByteArray {
        return try {
            if (s3Url.isBlank()) {
                logger.warn("S3 URL is blank")
                return byteArrayOf()
            }

            logger.info("Attempting to download from S3: $s3Url")

            if (!storageService.fileExists(s3Url)) {
                logger.warn("Image file does not exist in S3: $s3Url")
                return byteArrayOf()
            }

            val imageBytes = storageService.downloadFileAsBytes(s3Url)

            if (imageBytes.isEmpty()) {
                logger.warn("Downloaded 0 bytes from S3 for $s3Url")
            } else {
                logger.info("Downloaded ${imageBytes.size} bytes from S3 for $s3Url")
            }

            return imageBytes
        } catch (e: Exception) {
            logger.error("Failed to download image from S3: $s3Url", e)
            byteArrayOf()
        }
    }

    private fun createSuccessfulAnalysis(
        incidentId: UUID,
        imageUrl: String,
        result: ImageAnalysisResult
    ): ImageAnalysis {
        val safetyTags = result.safetyTags.joinToString(", ")
        val allLabels = result.allLabels.joinToString(", ") {
            "${it.description} (${String.format("%.2f", it.confidence)})"
        }

        return ImageAnalysis(
            incidentId = incidentId,
            imageUrl = imageUrl,
            tags = safetyTags.ifEmpty { "No safety-specific tags detected" },
            allLabels = allLabels,
            textDetected = result.textDetected.ifBlank { null },
            confidenceScore = result.confidenceScore,
            processed = true,
            processedAt = LocalDateTime.now()
        )
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
