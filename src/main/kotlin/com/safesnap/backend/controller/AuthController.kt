package com.safesnap.backend.controller



import com.safesnap.backend.auth.AuthService
import com.safesnap.backend.dto.user.AuthResponseDTO
import com.safesnap.backend.dto.user.LoginRequestDTO
import com.safesnap.backend.dto.user.UserCreateDTO
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val authService: AuthService
) {

    @PostMapping("/register")
    fun register(@Valid @RequestBody request: UserCreateDTO): ResponseEntity<AuthResponseDTO> {
        return ResponseEntity.ok(authService.register(request))
    }

    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequestDTO): ResponseEntity<AuthResponseDTO> {
        return ResponseEntity.ok(authService.login(request))
    }
}
