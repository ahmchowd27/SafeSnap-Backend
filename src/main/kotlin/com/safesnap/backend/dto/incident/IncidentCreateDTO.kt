package com.safesnap.backend.dto.incident

import jakarta.validation.constraints.NotBlank

data class IncidentCreateDTO(
    @field:NotBlank val title: String,
    @field:NotBlank val description: String,
    val latitude: Double,
    val longitude: Double,
    @field:NotBlank val imageS3Url: String,
    val audioS3Url: String? = null
)
