package com.safesnap.backend.dto.incident

import java.time.LocalDateTime
import java.util.*

data class IncidentResponseDTO(
    val id: UUID,
    val title: String,
    val description: String,
    val severity: String,
    val status: String,
    val latitude: Double?,
    val longitude: Double?,
    val locationDescription: String?,
    val imageUrls: List<String>,
    val audioUrls: List<String>,
    val reportedBy: String,
    val reportedByEmail: String,
    val assignedTo: String?,
    val assignedToEmail: String?,
    val reportedAt: LocalDateTime,
    val updatedAt: LocalDateTime?,
    val updatedBy: String?,
    val rcaReport: RcaResponseDTO? = null,
    val aiSuggestions: List<AiSuggestionDTO> = emptyList(),
    val imageTags: List<String> = emptyList(),
    val transcriptions: List<String> = emptyList()
)
