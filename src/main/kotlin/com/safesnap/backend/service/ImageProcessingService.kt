package com.safesnap.backend.service

import com.safesnap.backend.entity.ImageAnalysis
import com.safesnap.backend.repository.ImageAnalysisRepository
import com.safesnap.backend.repository.IncidentRepository
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
class ImageProcessingService(
    private val s3Service: S3Service,
    private val googleVisionService: GoogleVisionService,
    private val imageAnalysisRepository: ImageAnalysisRepository,
    private val incidentRepository: IncidentRepository
) {
    
    private val logger = LoggerFactory.getLogger(ImageProcessingService::class.java)
    
    /**
     * Process all images for an incident asynchronously
     */
    @Async
    @Transactional
    fun processIncidentImages(incidentId: UUID): CompletableFuture<List<ImageAnalysis>> {
        return CompletableFuture.supplyAsync {
            try {
                logger.info("Starting image processing for incident $incidentId")

                // Retrieve incident with images - make sure you're using the correct repository method
                val incident = incidentRepository.findById(incidentId).orElse(null)
                if (incident == null) {
                    logger.error("Incident not found: $incidentId")
                    return@supplyAsync emptyList<ImageAnalysis>()
                }


                val urls = incident.imageUrls
                val analyses = mutableListOf<ImageAnalysis>()

                // Process each image URL
                urls.forEachIndexed { index: Int, imageUrl: String ->
                    try {
                        logger.info("Processing image ${index + 1}/${urls.size}: $imageUrl")

                        // Check if already processed
                        val existingAnalysis = imageAnalysisRepository.findByIncidentIdAndImageUrl(incidentId, imageUrl)
                        if (existingAnalysis != null) {
                            logger.info("Image already processed, skipping: $imageUrl")
                            analyses.add(existingAnalysis)
                        } else {

                            val imageBytes = downloadImageFromS3(imageUrl)
                            if (imageBytes.isEmpty()) {
                                logger.error("Failed to download image: $imageUrl")
                                val failedAnalysis = createFailedAnalysis(incidentId, imageUrl, "Failed to download from S3")
                                analyses.add(imageAnalysisRepository.save(failedAnalysis))
                            } else {

                                val analysisResult = googleVisionService.analyzeImage(imageBytes)
                                val imageAnalysis = if (analysisResult.success) {
                                    createSuccessfulAnalysis(incidentId, imageUrl, analysisResult)
                                } else {
                                    createFailedAnalysis(incidentId, imageUrl, analysisResult.errorMessage)
                                }
                                analyses.add(imageAnalysisRepository.save(imageAnalysis))
                                logger.info("Successfully processed image: $imageUrl")
                            }
                        }
                    } catch (e: Exception) {
                        logger.error("Error processing image $imageUrl", e)
                        val failedAnalysis = createFailedAnalysis(incidentId, imageUrl, "Processing error: ${e.message}")
                        analyses.add(imageAnalysisRepository.save(failedAnalysis))
                    }
                }

                logger.info("Completed processing ${analyses.size} images for incident $incidentId")
                return@supplyAsync analyses

            } catch (e: Exception) {
                logger.error("Unexpected error during image processing for incident $incidentId", e)
                return@supplyAsync emptyList<ImageAnalysis>()
            }
        }
    }
    /**
     * Process a single image synchronously
     */
    @Transactional
    fun processSingleImage(incidentId: UUID, imageUrl: String): ImageAnalysis {
        try {
            logger.info("Processing single image: $imageUrl for incident: $incidentId")

            // Check if already processed
            val existingAnalysis = imageAnalysisRepository.findByIncidentIdAndImageUrl(incidentId, imageUrl)
            if (existingAnalysis != null) {
                logger.info("Image already processed: $imageUrl")
                return existingAnalysis
            }

            // Download and analyze
            val imageBytes = downloadImageFromS3(imageUrl)
            if (imageBytes.isEmpty()) {
                return imageAnalysisRepository.save(createFailedAnalysis(incidentId, imageUrl, "Failed to download from S3"))
            }

            val analysisResult = googleVisionService.analyzeImage(imageBytes)
            val analysis = if (analysisResult.success) {
                createSuccessfulAnalysis(incidentId, imageUrl, analysisResult)
            } else {
                createFailedAnalysis(incidentId, imageUrl, analysisResult.errorMessage)
            }

            return imageAnalysisRepository.save(analysis)

        } catch (e: Exception) {
            logger.error("Error processing single image $imageUrl", e)
            val failedAnalysis = createFailedAnalysis(incidentId, imageUrl, "Processing error: ${e.message}")
            return imageAnalysisRepository.save(failedAnalysis)
        }
    }
    /**
     * Download image bytes from S3
     */
    private fun downloadImageFromS3(s3Url: String): ByteArray {
        return try {
            if (!s3Service.fileExists(s3Url)) {
                logger.warn("Image file does not exist in S3: $s3Url")
                return byteArrayOf()
            }
            
            s3Service.downloadFileAsBytes(s3Url)
        } catch (e: Exception) {
            logger.error("Failed to download image from S3: $s3Url", e)
            byteArrayOf()
        }
    }
    
    /**
     * Create successful ImageAnalysis entity
     */
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
    
    /**
     * Create failed ImageAnalysis entity
     */
    private fun createFailedAnalysis(
        incidentId: UUID, 
        imageUrl: String, 
        errorMessage: String?
    ): ImageAnalysis {
        return ImageAnalysis(
            incidentId = incidentId,
            imageUrl = imageUrl,
            tags = "PROCESSING_FAILED",
            allLabels = "Error: ${errorMessage ?: "Unknown error"}",
            processed = false,
            processedAt = LocalDateTime.now()
        )
    }
    
    /**
     * Reprocess failed image analyses
     */
    @Async
    @Transactional
    fun reprocessFailedAnalyses(): CompletableFuture<Int> {
        return CompletableFuture.supplyAsync {
            try {
                val failedAnalyses = imageAnalysisRepository.findByProcessedFalse()
                var reprocessedCount = 0
                
                failedAnalyses.forEach { analysis ->
                    try {
                        logger.info("Reprocessing failed analysis: ${analysis.id}")
                        processSingleImage(analysis.incidentId, analysis.imageUrl)
                        reprocessedCount++
                    } catch (e: Exception) {
                        logger.error("Failed to reprocess analysis ${analysis.id}", e)
                    }
                }
                
                logger.info("Reprocessed $reprocessedCount failed analyses")
                reprocessedCount
                
            } catch (e: Exception) {
                logger.error("Error during batch reprocessing", e)
                0
            }
        }
    }
    
    /**
     * Get processing statistics
     */
    fun getProcessingStats(): ImageProcessingStats {
        val totalProcessed = imageAnalysisRepository.count()
        val successfulProcessed = imageAnalysisRepository.countByProcessedTrue()
        val failedProcessed = imageAnalysisRepository.countByProcessedFalse()
        
        return ImageProcessingStats(
            totalImagesProcessed = totalProcessed,
            successfulAnalyses = successfulProcessed,
            failedAnalyses = failedProcessed,
            successRate = if (totalProcessed > 0) (successfulProcessed.toDouble() / totalProcessed) * 100 else 0.0
        )
    }
    
    /**
     * Get analysis results for an incident
     */
    fun getIncidentAnalyses(incidentId: UUID): List<ImageAnalysis> {
        return imageAnalysisRepository.findByIncidentIdOrderByProcessedAtDesc(incidentId)
    }
}

/**
 * Image processing statistics
 */
data class ImageProcessingStats(
    val totalImagesProcessed: Long,
    val successfulAnalyses: Long,
    val failedAnalyses: Long,
    val successRate: Double
)
