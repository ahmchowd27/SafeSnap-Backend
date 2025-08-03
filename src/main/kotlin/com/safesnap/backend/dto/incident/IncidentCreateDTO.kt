package com.safesnap.backend.dto.incident

import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import com.safesnap.backend.config.SafeSnapConstants

data class IncidentCreateDTO(
    @field:NotBlank(message = "Title is required")
    val title: String,
    
    @field:NotBlank(message = "Description is required")
    val description: String,
    
    @field:NotNull(message = "Severity is required")
    val severity: String, // LOW, MEDIUM, HIGH, CRITICAL
    
    @field:DecimalMin(value = SafeSnapConstants.MIN_LATITUDE.toString(), message = "Latitude must be between -90 and 90")
    @field:DecimalMax(value = SafeSnapConstants.MAX_LATITUDE.toString(), message = "Latitude must be between -90 and 90")
    val latitude: Double?,
    
    @field:DecimalMin(value = SafeSnapConstants.MIN_LONGITUDE.toString(), message = "Longitude must be between -180 and 180")
    @field:DecimalMax(value = SafeSnapConstants.MAX_LONGITUDE.toString(), message = "Longitude must be between -180 and 180")
    val longitude: Double?,
    
    val locationDescription: String? = null,
    
    val imageUrls: List<String>? = emptyList(),
    
    val audioUrls: List<String>? = emptyList()
)
