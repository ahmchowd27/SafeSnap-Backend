package com.safesnap.backend.controller

import com.safesnap.backend.service.FileType
import com.safesnap.backend.service.PresignedUrlResponse
import com.safesnap.backend.service.S3Service
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/s3")
class S3Controller(
    private val s3Service: S3Service
) {

    @PostMapping("/upload-url")
    fun getPresignedUploadUrl(
        @RequestBody request: PresignedUploadRequest,
        @AuthenticationPrincipal userDetails: UserDetails
    ): ResponseEntity<PresignedUrlResponse> {
        
        // Extract user ID from UserDetails (you'll need to implement this based on your UserDetails implementation)
        val userId = extractUserIdFromUserDetails(userDetails)
        
        val response = s3Service.generatePresignedUploadUrl(
            fileType = request.fileType,
            fileExtension = request.fileExtension,
            userId = userId
        )
        
        return ResponseEntity.ok(response)
    }

    @PostMapping("/download-url")
    fun getPresignedDownloadUrl(
        @RequestBody request: PresignedDownloadRequest
    ): ResponseEntity<Map<String, String>> {
        
        val downloadUrl = s3Service.generatePresignedDownloadUrl(request.s3Url)
        
        return ResponseEntity.ok(mapOf("downloadUrl" to downloadUrl))
    }

    @GetMapping("/file-exists")
    fun checkFileExists(@RequestParam s3Url: String): ResponseEntity<Map<String, Boolean>> {
        val exists = s3Service.fileExists(s3Url)
        return ResponseEntity.ok(mapOf("exists" to exists))
    }
    
    // TODO: Implement this method based on your UserDetails implementation
    private fun extractUserIdFromUserDetails(userDetails: UserDetails): Long {
        // This is a placeholder - you'll need to implement this based on how you store user ID in UserDetails
        // For now, return a dummy value
        return 1L
    }
}

data class PresignedUploadRequest(
    val fileType: FileType,
    val fileExtension: String
)

data class PresignedDownloadRequest(
    val s3Url: String
)
