package com.safesnap.backend.dto.incident

import com.safesnap.backend.dto.user.UserResponseDTO
import java.time.LocalDateTime

data class RcaResponseDTO(
    val id: Long,
    val fiveWhys: String,
    val correctiveAction: String,
    val preventiveAction: String,
    val createdAt: LocalDateTime,
    val manager: UserResponseDTO
)