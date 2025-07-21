package com.safesnap.backend.dto.incident

import jakarta.validation.constraints.NotBlank

data class RcaCreateDTO(
    @field:NotBlank val fiveWhys: String,
    @field:NotBlank val correctiveAction: String,
    @field:NotBlank val preventiveAction: String
)