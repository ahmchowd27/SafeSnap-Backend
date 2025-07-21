package com.safesnap.backend.service

import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.repository.UserRepository
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.model.GetObjectRequest
import software.amazon.awssdk.services.s3.model.PutObjectRequest
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest
import java.time.Duration
import java.util.*

@Service
class S3Service(
    private val s3Client: S3Client,
    private val s3Presigner: S3Presigner,
    private val userRepository: UserRepository,
    private val metricsService: MetricsService,
    @Value("\${aws.s3.bucket-name}") private val bucketName: String,
    @Value("\${aws.endpoint-url:}") private val endpointUrl: String
) {
    
    private val logger = LoggerFactory.getLogger(S3Service::class.java)
    
    /**
     * Generate a pre-signed URL for uploading a file to S3
     * Tracks which user uploaded which file
     */
    fun generatePresignedUploadUrl(
        fileType: FileType,
        fileExtension: String,
        userDetails: UserDetails
    ): PresignedUrlResponse {
        val userId = extractUserIdFromUserDetails(userDetails)
        val fileName = generateFileName(fileType, fileExtension, userId)
        val key = "${fileType.folder}/$fileName"
        
        logger.info("Generating pre-signed upload URL for user $userId, key: $key")
        
        val putObjectRequest = PutObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .contentType(getContentType(fileExtension))
            .build()
        
        val presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(15)) // 15 minutes to upload
            .putObjectRequest(putObjectRequest)
            .build()
        
        val presignedRequest = s3Presigner.presignPutObject(presignRequest)
        val uploadUrl = presignedRequest.url().toString()
        
        // Generate the final S3 URL that will be stored in the database
        val s3Url = if (endpointUrl.isNotBlank()) {
            // LocalStack URL format
            "$endpointUrl/$bucketName/$key"
        } else {
            // Real AWS S3 URL format
            "https://$bucketName.s3.amazonaws.com/$key"
        }
        
        logger.info("Generated pre-signed URL for upload by user $userId. S3 URL: $s3Url")
        
        // Record file upload metric
        metricsService.recordFileUploaded(fileType.name)
        
        return PresignedUrlResponse(
            uploadUrl = uploadUrl,
            s3Url = s3Url,
            fileName = fileName,
            expiresInMinutes = 15,
            uploadedByUserId = userId
        )
    }

    fun generatePresignedDownloadUrl(s3Url: String): String {
        val key = extractKeyFromS3Url(s3Url)
        
        logger.info("Generating pre-signed download URL for key: $key")
        
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        
        val presignRequest = GetObjectPresignRequest.builder()
            .signatureDuration(Duration.ofHours(1)) // 1 hour to download
            .getObjectRequest(getObjectRequest)
            .build()
        
        val presignedRequest = s3Presigner.presignGetObject(presignRequest)
        return presignedRequest.url().toString()
    }

    fun fileExists(s3Url: String): Boolean {
        return try {
            val key = extractKeyFromS3Url(s3Url)
            s3Client.headObject { it.bucket(bucketName).key(key) }
            true
        } catch (e: Exception) {
            logger.warn("File does not exist in S3: $s3Url", e)
            false
        }
    }
    
    /**
     * Download file content as byte array (for AI processing)
     */
    fun downloadFileAsBytes(s3Url: String): ByteArray {
        val key = extractKeyFromS3Url(s3Url)
        
        logger.info("Downloading file as bytes for key: $key")
        
        val getObjectRequest = GetObjectRequest.builder()
            .bucket(bucketName)
            .key(key)
            .build()
        
        return s3Client.getObjectAsBytes(getObjectRequest).asByteArray()
    }
    
    /**
     * Extract user ID from UserDetails for tracking file uploads
     * Since users log in with email, the username in UserDetails will be the email
     */
    private fun extractUserIdFromUserDetails(userDetails: UserDetails): Long {
        val email = userDetails.username
        val user = userRepository.findByEmail(email)
            ?: throw UserNotFoundException("User not found with email: $email")
        return user.id
    }
    
    private fun generateFileName(fileType: FileType, extension: String, userId: Long): String {
        val timestamp = System.currentTimeMillis()
        val uuid = UUID.randomUUID().toString().substring(0, 8)
        return "user_${userId}_${timestamp}_${uuid}.$extension"
    }
    
    private fun extractKeyFromS3Url(s3Url: String): String {
        return if (endpointUrl.isNotBlank()) {
            // LocalStack format: http://localhost:4566/bucket-name/key
            s3Url.substringAfter("$bucketName/")
        } else {
            // AWS S3 format: https://bucket-name.s3.amazonaws.com/key
            s3Url.substringAfter("$bucketName.s3.amazonaws.com/")
        }
    }
    
    private fun getContentType(extension: String): String {
        return when (extension.lowercase()) {
            "jpg", "jpeg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "webp" -> "image/webp"
            "mp3" -> "audio/mpeg"
            "wav" -> "audio/wav"
            "m4a" -> "audio/mp4"
            "ogg" -> "audio/ogg"
            else -> "application/octet-stream"
        }
    }
}

enum class FileType(val folder: String) {
    IMAGE("incidents/images"),
    AUDIO("incidents/audio")
}

data class PresignedUrlResponse(
    val uploadUrl: String,
    val s3Url: String,
    val fileName: String,
    val expiresInMinutes: Int,
    val uploadedByUserId: Long
)
