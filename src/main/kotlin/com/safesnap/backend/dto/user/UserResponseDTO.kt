package com.safesnap.backend.dto.user

import com.safesnap.backend.entity.Role

data class UserResponseDTO(
    val id: Long,
    val name: String,
    val email: String,
    val role: Role
)