package com.safesnap.backend.dto.user

import com.safesnap.backend.entity.Role
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank

data class UserCreateDTO(
    @field:NotBlank val name: String,
    @field:Email val email: String,
    @field:NotBlank val password: String,
    val role: Role  // WORKER or MANAGER
)