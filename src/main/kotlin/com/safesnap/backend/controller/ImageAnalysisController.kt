package com.safesnap.backend.controller

import com.safesnap.backend.repository.ImageAnalysisRepository
import com.safesnap.backend.service.ImageProcessingService
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*
import java.time.LocalDateTime
import java.util.*

@RestController
@RequestMapping("/api/image-analysis")
class ImageAnalysisController(
    private val imageProcessingService: ImageProcessingService,
    private val imageAnalysisRepository: ImageAnalysisRepository
) {

    /**
     * Process images for an incident (called after S3 upload)
     */
    @PostMapping("/process-incident/{incidentId}")
    @PreAuthorize("hasRole('WORKER') or hasRole('MANAGER')")
    fun processIncidentImages(
        @PathVariable incidentId: UUID,
        @RequestBody request: ProcessIncidentImagesRequest
    ): ResponseEntity<ProcessImageResponse> {
        
        return try {
            // Start async processing of all images
            imageProcessingService.processIncidentImages(incidentId, request.imageUrls)
            
            ResponseEntity.ok(ProcessImageResponse(
                success = true,
                message = "Image analysis started for ${request.imageUrls.size} images",
                incidentId = incidentId,
                imagesQueued = request.imageUrls.size
            ))
            
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(ProcessImageResponse(
                success = false,
                message = "Failed to start image processing: ${e.message}",
                incidentId = incidentId,
                imagesQueued = 0
            ))
        }
    }

    /**
     * Get analysis results for an incident
     */
    @GetMapping("/incident/{incidentId}/results")
    fun getIncidentAnalysisResults(@PathVariable incidentId: UUID): ResponseEntity<IncidentAnalysisResponse> {
        
        val analyses = imageAnalysisRepository.findByIncidentId(incidentId)
        
        if (analyses.isEmpty()) {
            return ResponseEntity.ok(IncidentAnalysisResponse(
                incidentId = incidentId,
                totalImages = 0,
                processedImages = 0,
                pendingImages = 0,
                results = emptyList(),
                status = "NO_IMAGES"
            ))
        }
        
        val processed = analyses.filter { it.processed }
        val pending = analyses.filter { !it.processed && it.errorMessage == null }
        val failed = analyses.filter { !it.processed && it.errorMessage != null }
        
        return ResponseEntity.ok(IncidentAnalysisResponse(
            incidentId = incidentId,
            totalImages = analyses.size,
            processedImages = processed.size,
            pendingImages = pending.size,
            failedImages = failed.size,
            results = processed.map { analysis ->
                ImageAnalysisResultResponse(
                    imageUrl = analysis.imageUrl,
                    detectedItems = analysis.tags.split(","),
                    textDetected = analysis.textDetected,
                    confidenceScore = analysis.confidenceScore,
                    processed = analysis.processed,
                    processedAt = analysis.processedAt,
                    errorMessage = analysis.errorMessage
                )
            },
            status = when {
                pending.isNotEmpty() -> "PROCESSING"
                failed.isNotEmpty() && processed.isEmpty() -> "FAILED"
                failed.isNotEmpty() -> "PARTIAL"
                else -> "COMPLETED"
            }
        ))
    }

    /**
     * Get processing status for specific image
     */
    @GetMapping("/incident/{incidentId}/image-status")
    fun getImageProcessingStatus(
        @PathVariable incidentId: UUID,
        @RequestParam imageUrl: String
    ): ResponseEntity<ImageProcessingStatusResponse> {
        
        val analysis = imageAnalysisRepository.findByIncidentIdAndImageUrl(incidentId, imageUrl)
            ?: return ResponseEntity.notFound().build()
        
        return ResponseEntity.ok(ImageProcessingStatusResponse(
            imageUrl = imageUrl,
            status = when {
                analysis.processed -> "COMPLETED"
                analysis.errorMessage != null -> "FAILED"
                else -> "PROCESSING"
            },
            detectedItems = analysis.tags.split(","),
            textDetected = analysis.textDetected,
            confidenceScore = analysis.confidenceScore,
            processedAt = analysis.processedAt,
            errorMessage = analysis.errorMessage
        ))
    }

    /**
     * Retry failed image processing
     */
    @PostMapping("/incident/{incidentId}/retry-failed")
    @PreAuthorize("hasRole('MANAGER')")
    fun retryFailedProcessing(@PathVariable incidentId: UUID): ResponseEntity<Map<String, Any>> {
        
        val failedAnalyses = imageAnalysisRepository.findByIncidentIdAndProcessedFalseAndErrorMessageIsNotNull(incidentId)
        
        if (failedAnalyses.isEmpty()) {
            return ResponseEntity.ok(mapOf(
                "message" to "No failed images found for incident",
                "incidentId" to incidentId
            ))
        }
        
        val imageUrls = failedAnalyses.map { it.imageUrl }
        imageProcessingService.processIncidentImages(incidentId, imageUrls)
        
        return ResponseEntity.ok(mapOf(
            "message" to "Retrying ${imageUrls.size} failed images",
            "incidentId" to incidentId,
            "retriedImages" to imageUrls.size
        ))
    }
}

// Request/Response DTOs for real S3 integration

data class ProcessIncidentImagesRequest(
    val imageUrls: List<String> // S3 URLs of uploaded images
)

data class ProcessImageResponse(
    val success: Boolean,
    val message: String,
    val incidentId: UUID,
    val imagesQueued: Int
)

data class IncidentAnalysisResponse(
    val incidentId: UUID,
    val totalImages: Int,
    val processedImages: Int,
    val pendingImages: Int,
    val failedImages: Int = 0,
    val results: List<ImageAnalysisResultResponse>,
    val status: String // NO_IMAGES, PROCESSING, COMPLETED, FAILED, PARTIAL
)

data class ImageAnalysisResultResponse(
    val imageUrl: String,
    val detectedItems: List<String>,
    val textDetected: String?,
    val confidenceScore: Double?,
    val processed: Boolean,
    val processedAt: LocalDateTime?,
    val errorMessage: String?
)

data class ImageProcessingStatusResponse(
    val imageUrl: String,
    val status: String, // PROCESSING, COMPLETED, FAILED
    val detectedItems: List<String>,
    val textDetected: String?,
    val confidenceScore: Double?,
    val processedAt: LocalDateTime?,
    val errorMessage: String?
)
