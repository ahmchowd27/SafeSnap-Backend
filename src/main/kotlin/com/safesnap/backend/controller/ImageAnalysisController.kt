package com.safesnap.backend.controller

import com.safesnap.backend.service.GoogleVisionService
import com.safesnap.backend.service.ImageProcessingService
import com.safesnap.backend.service.S3Service
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/image-analysis")
class ImageAnalysisController(
    private val imageProcessingService: ImageProcessingService,
    private val googleVisionService: GoogleVisionService,
    private val s3Service: S3Service
) {

    /**
     * Test endpoint to analyze an image from S3 URL
     */
    @PostMapping("/test-analyze")
    fun testAnalyzeImage(@RequestBody request: TestAnalysisRequest): ResponseEntity<Map<String, Any>> {
        return try {
            // Download image from S3
            val imageBytes = s3Service.downloadFileAsBytes(request.s3Url)
            
            if (imageBytes.isEmpty()) {
                return ResponseEntity.badRequest().body(mapOf(
                    "success" to false,
                    "error" to "Could not download image from S3 URL"
                ))
            }
            
            // Analyze with Google Vision
            val result = googleVisionService.analyzeImage(imageBytes)
            
            ResponseEntity.ok(mapOf(
                "success" to result.success,
                "safetyTags" to result.safetyTags,
                "allLabels" to result.allLabels.map { mapOf(
                    "description" to it.description,
                    "confidence" to it.confidence
                )},
                "objectsDetected" to result.objectsDetected.map { mapOf(
                    "name" to it.name,
                    "confidence" to it.confidence
                )},
                "textDetected" to result.textDetected,
                "confidenceScore" to result.confidenceScore,
                "errorMessage" to (result.errorMessage ?: "")
            ))
            
        } catch (e: Exception) {
            ResponseEntity.badRequest().body(mapOf(
                "success" to false,
                "error" to "Analysis failed: ${e.message}"
            ))
        }
    }
    
    /**
     * Get processing statistics
     */
    @GetMapping("/stats")
    fun getProcessingStats(): ResponseEntity<Map<String, Any>> {
        val stats = imageProcessingService.getProcessingStats()
        
        return ResponseEntity.ok(mapOf(
            "totalImagesProcessed" to stats.totalImagesProcessed,
            "successfulAnalyses" to stats.successfulAnalyses,
            "failedAnalyses" to stats.failedAnalyses,
            "successRate" to String.format("%.2f%%", stats.successRate)
        ))
    }
    
    /**
     * Test Google Vision API connectivity
     */
    @GetMapping("/test-vision")
    fun testVisionApi(): ResponseEntity<Map<String, Any>> {
        return try {
            // Create a simple test image (1x1 pixel)
            val testImageBytes = byteArrayOf(
                -1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 1, 0, 72, 0, 72, 0, 0, -1, -37, 0, 67, 0
            )
            
            val result = googleVisionService.analyzeImage(testImageBytes)
            
            ResponseEntity.ok(mapOf(
                "visionApiAvailable" to result.success,
                "message" to if (result.success) "Google Vision API is working" else "Vision API failed",
                "errorMessage" to (result.errorMessage ?: ""),
                "mockMode" to (result.safetyTags.contains("Construction site")) // Mock analysis indicator
            ))
            
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf(
                "visionApiAvailable" to false,
                "message" to "Vision API test failed",
                "error" to (e.message ?: "Unknown error")
            ))
        }
    }
    
    /**
     * Manually trigger image processing for testing
     * (This would normally be called automatically when incidents are created)
     */
    @PostMapping("/manual-process")
    @PreAuthorize("hasRole('MANAGER')")
    fun manualProcessImage(@RequestBody request: ManualProcessRequest): ResponseEntity<Map<String, Any>> {
        return ResponseEntity.ok(mapOf(
            "message" to "Manual processing endpoint ready",
            "note" to "This would process incident ${request.incidentId} when incident service is implemented"
        ))
    }
}

/**
 * Request to test image analysis
 */
data class TestAnalysisRequest(
    val s3Url: String
)

/**
 * Request for manual image processing
 */
data class ManualProcessRequest(
    val incidentId: Long
)
