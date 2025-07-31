/*
 * Copyright (c) 2025 SafeSnap Development Team
 * Licensed under the MIT License
 * See LICENSE file in the project root for full license information
 */
package com.safesnap.backend.controller

import com.safesnap.backend.auth.AuthService
import com.safesnap.backend.dto.user.AuthResponseDTO
import com.safesnap.backend.dto.user.LoginRequestDTO
import com.safesnap.backend.dto.user.UserCreateDTO
import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.media.Content
import io.swagger.v3.oas.annotations.media.ExampleObject
import io.swagger.v3.oas.annotations.media.Schema
import io.swagger.v3.oas.annotations.responses.ApiResponse
import io.swagger.v3.oas.annotations.responses.ApiResponses
import io.swagger.v3.oas.annotations.tags.Tag
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
@Tag(
    name = "Authentication", 
    description = "User registration and login endpoints"
)
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    @Operation(
        summary = "Register a new user",
        description = "Create a new user account with email, password, and role (WORKER or MANAGER)"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "User registered successfully",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AuthResponseDTO::class),
                    examples = [ExampleObject(
                        value = """{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", "role": "WORKER"}"""
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "409",
                description = "Email already registered",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{"error": "Email Already Registered", "message": "Email already registered: user@example.com"}"""
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "400",
                description = "Validation error (invalid email format, weak password, etc.)"
            )
        ]
    )
    fun register(
        @Valid @RequestBody 
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "User registration details",
            content = [Content(
                examples = [ExampleObject(
                    name = "Worker Registration",
                    value = """
                    {
                        "name": "John Doe",
                        "email": "john.doe@company.com",
                        "password": "SecurePassword123!",
                        "role": "WORKER"
                    }
                    """
                ), ExampleObject(
                    name = "Manager Registration", 
                    value = """
                    {
                        "name": "Jane Manager",
                        "email": "jane.manager@company.com",
                        "password": "SecurePassword123!",
                        "role": "MANAGER"
                    }
                    """
                )]
            )]
        )
        request: UserCreateDTO
    ): ResponseEntity<AuthResponseDTO> {
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/login")
    @Operation(
        summary = "User login",
        description = "Authenticate user with email and password, returns JWT token for API access"
    )
    @ApiResponses(
        value = [
            ApiResponse(
                responseCode = "200",
                description = "Login successful",
                content = [Content(
                    mediaType = "application/json",
                    schema = Schema(implementation = AuthResponseDTO::class),
                    examples = [ExampleObject(
                        value = """{"token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...", "role": "WORKER"}"""
                    )]
                )]
            ),
            ApiResponse(
                responseCode = "401",
                description = "Invalid credentials",
                content = [Content(
                    mediaType = "application/json",
                    examples = [ExampleObject(
                        value = """{"error": "Unauthorized", "message": "Invalid email or password"}"""
                    )]
                )]
            )
        ]
    )
    fun login(
        @Valid @RequestBody
        @io.swagger.v3.oas.annotations.parameters.RequestBody(
            description = "Login credentials",
            content = [Content(
                examples = [ExampleObject(
                    value = """
                    {
                        "email": "john.doe@company.com",
                        "password": "SecurePassword123!"
                    }
                    """
                )]
            )]
        )
        request: LoginRequestDTO
    ): ResponseEntity<AuthResponseDTO> {
        return ResponseEntity.ok(authService.login(request))
    }
}
