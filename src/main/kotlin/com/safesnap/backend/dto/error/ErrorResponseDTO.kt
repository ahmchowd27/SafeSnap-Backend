package com.safesnap.backend.dto.error

import com.fasterxml.jackson.annotation.JsonFormat
import java.time.LocalDateTime

data class ErrorResponseDTO(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String
) {
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    fun getFormattedTimestamp(): LocalDateTime = timestamp
}

data class ValidationErrorResponseDTO(
    val timestamp: LocalDateTime = LocalDateTime.now(),
    val status: Int,
    val error: String,
    val message: String,
    val path: String,
    val validationErrors: Map<String, String>
)
