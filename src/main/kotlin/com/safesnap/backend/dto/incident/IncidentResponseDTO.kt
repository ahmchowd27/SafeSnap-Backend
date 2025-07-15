package com.safesnap.backend.dto.incident

import com.safesnap.backend.dto.user.UserResponseDTO
import java.time.LocalDateTime

data class IncidentResponseDTO(
    val id: Long,
    val title: String,
    val description: String,
    val imageS3Url: String,
    val audioS3Url: String?,
    val createdAt: LocalDateTime,
    val reporter: UserResponseDTO,
    val imageTags: List<String>,
    val transcription: String?,
    val aiSuggestion: String?,
    val rcaReport: RcaResponseDTO?
)