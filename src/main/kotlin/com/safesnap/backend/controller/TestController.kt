package com.safesnap.backend.controller

import com.safesnap.backend.service.S3Service
import com.safesnap.backend.service.GoogleVisionService
import com.safesnap.backend.service.FileType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/test")
class TestController(
    private val s3Service: S3Service,
    private val googleVisionService: GoogleVisionService
) {

    @GetMapping("/protected")
    fun protectedEndpoint(): ResponseEntity<Map<String, String>> {
        return ResponseEntity.ok(mapOf("message" to "Access granted to protected endpoint"))
    }

    @GetMapping("/s3-health")
    fun testS3Health(): ResponseEntity<Map<String, Any>> {
        return try {
            // Test S3 connection by trying to generate a pre-signed URL
            val testUrl = s3Service.generatePresignedUploadUrl(
                fileType = FileType.IMAGE,
                fileExtension = "jpg",
                userId = 999L
            )
            
            ResponseEntity.ok(mapOf(
                "status" to "healthy",
                "message" to "S3 LocalStack is working!",
                "endpoint" to "LocalStack S3",
                "testUploadUrl" to testUrl.uploadUrl.take(100) + "...",
                "bucket" to "safesnap-dev-bucket"
            ))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf(
                "status" to "error",
                "message" to "S3 connection failed: ${e.message}",
                "error" to e.javaClass.simpleName
            ))
        }
    }
    
    @GetMapping("/vision-health")
    fun testVisionHealth(): ResponseEntity<Map<String, Any>> {
        return try {
            // Create a minimal test image (tiny JPEG)
            val testImageBytes = byteArrayOf(
                -1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 1, 0, 72, 0, 72, 0, 0, -1, -37, 0, 67, 0
            )
            
            val result = googleVisionService.analyzeImage(testImageBytes)
            
            ResponseEntity.ok(mapOf(
                "status" to if (result.success) "healthy" else "error",
                "message" to if (result.success) "Google Vision API is working!" else "Vision API failed",
                "mockMode" to (result.safetyTags.contains("Construction site")),
                "safetyTagsFound" to result.safetyTags.size,
                "errorMessage" to (result.errorMessage ?: "")
            ))
        } catch (e: Exception) {
            ResponseEntity.ok(mapOf(
                "status" to "error",
                "message" to "Vision API test failed: ${e.message}",
                "error" to e.javaClass.simpleName
            ))
        }
    }
    
    @GetMapping("/full-system")
    fun testFullSystem(): ResponseEntity<Map<String, Any>> {
        val results = mutableMapOf<String, Any>()
        
        // Test S3
        try {
            s3Service.generatePresignedUploadUrl(FileType.IMAGE, "jpg", 999L)
            results["s3"] = "✅ Working"
        } catch (e: Exception) {
            results["s3"] = "❌ Failed: ${e.message}"
        }
        
        // Test Vision API
        try {
            val testBytes = byteArrayOf(-1, -40, -1, -32, 0, 16, 74, 70, 73, 70, 0, 1, 1, 1, 0, 72, 0, 72, 0, 0, -1, -37, 0, 67, 0)
            val visionResult = googleVisionService.analyzeImage(testBytes)
            results["vision"] = if (visionResult.success) "✅ Working" else "❌ Failed: ${visionResult.errorMessage ?: "Unknown error"}"
        } catch (e: Exception) {
            results["vision"] = "❌ Failed: ${e.message}"
        }
        
        // Overall status
        val allWorking = results.values.all { it.toString().startsWith("✅") }
        results["overall"] = if (allWorking) "✅ All systems operational" else "⚠️ Some issues detected"
        
        return ResponseEntity.ok(results)
    }
}
