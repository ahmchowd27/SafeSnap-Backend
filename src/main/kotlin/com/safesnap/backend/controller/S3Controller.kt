package com.safesnap.backend.controller

import com.safesnap.backend.service.FileType
import com.safesnap.backend.service.PresignedUrlResponse
import com.safesnap.backend.service.S3Service
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
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
        
        val response = s3Service.generatePresignedUploadUrl(
            fileType = request.fileType,
            fileExtension = request.fileExtension,
            userDetails = userDetails
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
    @Operation(
        summary = "Check if file exists",
        description = "Verify if a file exists in S3 storage"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "File existence check completed",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{"exists": true}"""
                    )]
                )]
            )
        ]
    )
    fun checkFileExists(
        @RequestParam 
        @io.swagger.v3.oas.annotations.Parameter(
            description = "S3 URL of the file to check",
            example = "https://bucket.s3.amazonaws.com/incidents/images/user_123_1234567890_abc123.jpg"
        )
        s3Url: String
    ): ResponseEntity<Map<String, Boolean>> {
        val exists = s3Service.fileExists(s3Url)
        return ResponseEntity.ok(mapOf("exists" to exists))
    }
}

@Schema(description = "Request for generating pre-signed upload URL")
data class PresignedUploadRequest(
    @Schema(
        description = "Type of file to upload", 
        example = "IMAGE",
        allowableValues = ["IMAGE", "AUDIO"]
    )
    val fileType: FileType,
    
    @Schema(
        description = "File extension", 
        example = "jpg",
        allowableValues = ["jpg", "jpeg", "png", "gif", "webp", "mp3", "wav", "m4a", "ogg"]
    )
    val fileExtension: String
)

@Schema(description = "Request for generating pre-signed download URL")
data class PresignedDownloadRequest(
    @Schema(
        description = "S3 URL of the file to download",
        example = "https://bucket.s3.amazonaws.com/incidents/images/user_123_1234567890_abc123.jpg"
    )
    val s3Url: String
)
