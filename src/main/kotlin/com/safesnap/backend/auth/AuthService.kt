package com.safesnap.backend.auth

import com.safesnap.backend.dto.user.AuthResponseDTO
import com.safesnap.backend.dto.user.LoginRequestDTO
import com.safesnap.backend.dto.user.UserCreateDTO
import com.safesnap.backend.entity.User
import com.safesnap.backend.jwt.JwtService
import com.safesnap.backend.repository.UserRepository
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager
) {
    fun register(request: UserCreateDTO): AuthResponseDTO {
        if (userRepository.existsByEmail(request.email)) {
            throw IllegalArgumentException("Email already registered")
        }

        val user = User(
            fullName = request.name,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = request.role
        )
        userRepository.save(user)
        val token = jwtService.generateToken(user.email, user.role)
        return AuthResponseDTO(token, user.role.name)

    }

    fun login(request: LoginRequestDTO): AuthResponseDTO {
        val authToken = UsernamePasswordAuthenticationToken(request.email, request.password)
        authenticationManager.authenticate(authToken)

        val user = userRepository.findByEmail(request.email)
            ?: throw IllegalArgumentException("Invalid email or password")

        val token = jwtService.generateToken(user.email, user.role)
        return AuthResponseDTO(token, user.role.name)

    }
}
