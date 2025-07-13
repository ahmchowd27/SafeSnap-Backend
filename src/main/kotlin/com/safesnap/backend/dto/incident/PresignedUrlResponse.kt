package com.safesnap.backend.dto.incident

data class PresignedUrlResponse(
    val presignedUrl: String,
    val finalUrl: String
)