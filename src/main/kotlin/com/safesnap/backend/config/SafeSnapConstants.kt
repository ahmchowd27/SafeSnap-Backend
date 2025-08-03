package com.safesnap.backend.config

/**
 * Centralized constants for the SafeSnap application.
 * This file contains all magic numbers, thresholds, and configuration values
 * to improve maintainability and reduce code duplication.
 */
object SafeSnapConstants {
    
    // File size limits
    const val MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024 // 10MB
    const val MAX_AUDIO_SIZE_BYTES = 50 * 1024 * 1024 // 50MB
    
    // AI and ML thresholds
    const val SIMILARITY_THRESHOLD = 0.8
    const val PERCENTAGE_MULTIPLIER = 100.0
    const val DEFAULT_CONFIDENCE_THRESHOLD = 0.7
    
    // Rate limiting
    const val DEFAULT_RATE_LIMIT_REQUESTS_PER_MINUTE = 20
    const val DEFAULT_RATE_LIMIT_TOKENS_PER_MINUTE = 40000
    
    // Pagination
    const val DEFAULT_PAGE_SIZE = 20
    const val MAX_PAGE_SIZE = 100
    
    // Validation
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 64
    const val MAX_NAME_LENGTH = 50
    
    // Coordinates validation
    const val MIN_LATITUDE = -90.0
    const val MAX_LATITUDE = 90.0
    const val MIN_LONGITUDE = -180.0
    const val MAX_LONGITUDE = 180.0
    
    // Processing timeouts
    const val IMAGE_PROCESSING_TIMEOUT_MS = 30000 // 30 seconds
    const val AI_GENERATION_TIMEOUT_MS = 60000 // 60 seconds
    
    // S3 configuration
    const val S3_PRESIGNED_URL_EXPIRY_SECONDS = 3600 // 1 hour
    const val S3_MAX_FILE_SIZE_BYTES = 100 * 1024 * 1024 // 100MB
    
    // Security
    const val JWT_EXPIRATION_HOURS = 24
    const val REFRESH_TOKEN_EXPIRATION_DAYS = 30
    
    // Metrics
    const val METRICS_RETENTION_DAYS = 90
    const val METRICS_BATCH_SIZE = 1000
    
    // Error messages
    const val GENERIC_ERROR_MESSAGE = "An unexpected error occurred"
    const val VALIDATION_ERROR_MESSAGE = "Invalid request data"
    const val UNAUTHORIZED_MESSAGE = "Access denied"
    const val NOT_FOUND_MESSAGE = "Resource not found"
    
    // Logging
    const val LOG_MASK_PASSWORD = "***"
    const val LOG_MASK_EMAIL = "***@***"
    
    // API versions
    const val API_VERSION_V1 = "v1"
    const val API_BASE_PATH = "/api"
    
    // Cache configuration
    const val CACHE_TTL_SECONDS = 300 // 5 minutes
    const val CACHE_MAX_SIZE = 1000
    
    // Database
    const val DB_CONNECTION_TIMEOUT_MS = 30000
    const val DB_QUERY_TIMEOUT_MS = 10000
    
    // External services
    const val OPENAI_TIMEOUT_MS = 30000
    const val GOOGLE_VISION_TIMEOUT_MS = 30000
    const val S3_TIMEOUT_MS = 30000
} 