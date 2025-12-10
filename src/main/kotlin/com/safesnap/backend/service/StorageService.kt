package com.safesnap.backend.service

import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.repository.UserRepository
import com.google.cloud.storage.BlobId
import com.google.cloud.storage.BlobInfo
import com.google.cloud.storage.HttpMethod
import com.google.cloud.storage.Storage
import com.google.cloud.storage.StorageOptions
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.userdetails.UserDetails
import org.springframework.stereotype.Service
import java.net.URL
import java.util.*

@Service
class StorageService(
    private val userRepository: UserRepository,
    private val metricsService: MetricsService,
    @Value("\${gcs.bucket-name}") private val bucketName: String,
    @Value("\${gcs.public-base-url:https://storage.googleapis.com}") private val publicBaseUrl: String
) {

    private val logger = LoggerFactory.getLogger(StorageService::class.java)
    private val storage: Storage = StorageOptions.getDefaultInstance().service

    /**
     * Generate a pre-signed URL for uploading a file to Google Cloud Storage
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

        logger.info("Generating GCS signed upload URL for user $userId, key: $key")

        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, key))
            .setContentType(getContentType(fileExtension))
            .build()

        val uploadUrl: URL = storage.signUrl(
            blobInfo,
            15,
            java.util.concurrent.TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.PUT),
            Storage.SignUrlOption.withV4Signature(),
            Storage.SignUrlOption.withContentType()
        )

        // Generate the final GCS URL that will be stored in the database
        val gcsUrl = "$publicBaseUrl/$bucketName/$key"

        logger.info("Generated GCS signed upload URL for user $userId. URL: $gcsUrl")
        metricsService.recordFileUploaded(fileType.name)

        return PresignedUrlResponse(
            uploadUrl = uploadUrl.toString(),
            s3Url = gcsUrl,
            fileName = fileName,
            expiresInMinutes = 15,
            uploadedByUserId = userId
        )
    }

    /**
     * Generate a pre-signed URL for downloading a file from Google Cloud Storage
     */
    fun generatePresignedDownloadUrl(s3Url: String): String {
        val key = extractKeyFromGcsUrl(s3Url)
        logger.info("Generating GCS signed download URL for key: $key")

        val blobInfo = BlobInfo.newBuilder(BlobId.of(bucketName, key)).build()
        val downloadUrl: URL = storage.signUrl(
            blobInfo,
            60,
            java.util.concurrent.TimeUnit.MINUTES,
            Storage.SignUrlOption.httpMethod(HttpMethod.GET),
            Storage.SignUrlOption.withV4Signature()
        )
        return downloadUrl.toString()
    }

    /**
     * Check if a file exists in Google Cloud Storage
     */
    fun fileExists(s3Url: String): Boolean {
        return try {
            val key = extractKeyFromGcsUrl(s3Url)
            storage.get(BlobId.of(bucketName, key)) != null
        } catch (e: Exception) {
            logger.warn("File does not exist in GCS: $s3Url", e)
            false
        }
    }

    /**
     * Download file content as byte array (for AI processing)
     */
    fun downloadFileAsBytes(s3Url: String): ByteArray {
        val key = extractKeyFromGcsUrl(s3Url)
        logger.info("Downloading file bytes from GCS for key: $key")
        val blob = storage.get(BlobId.of(bucketName, key))
        return blob.getContent()
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

    private fun extractKeyFromGcsUrl(gcsUrl: String): String {
        return when {
            gcsUrl.contains("storage.googleapis.com/$bucketName/") -> gcsUrl.substringAfter("storage.googleapis.com/$bucketName/")
            gcsUrl.contains("https://$bucketName.storage.googleapis.com/") -> gcsUrl.substringAfter("$bucketName.storage.googleapis.com/")
            gcsUrl.contains("$publicBaseUrl/$bucketName/") -> gcsUrl.substringAfter("$publicBaseUrl/$bucketName/")
            else -> gcsUrl.substringAfter("$bucketName/")
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
