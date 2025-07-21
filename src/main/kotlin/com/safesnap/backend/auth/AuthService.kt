package com.safesnap.backend.auth

import com.safesnap.backend.dto.user.AuthResponseDTO
import com.safesnap.backend.dto.user.LoginRequestDTO
import com.safesnap.backend.dto.user.UserCreateDTO
import com.safesnap.backend.entity.User
import com.safesnap.backend.exception.EmailAlreadyExistsException
import com.safesnap.backend.exception.InvalidCredentialsException
import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.jwt.JwtService
import com.safesnap.backend.repository.UserRepository
import com.safesnap.backend.service.MetricsService
import org.springframework.security.authentication.AuthenticationManager
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service

@Service
class AuthService(
    private val userRepository: UserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val authenticationManager: AuthenticationManager,
    private val metricsService: MetricsService
) {
    fun register(request: UserCreateDTO): AuthResponseDTO {
        // Check if email already exists
        if (userRepository.existsByEmail(request.email)) {
            throw EmailAlreadyExistsException(request.email)
        }

        // Create and save user
        val user = User(
            fullName = request.name,
            email = request.email,
            password = passwordEncoder.encode(request.password),
            role = request.role
        )
        
        val savedUser = userRepository.save(user)
        val token = jwtService.generateToken(savedUser.email, savedUser.role)
        
        // Record successful registration
        metricsService.recordAuthSuccess()
        
        return AuthResponseDTO(token, savedUser.role.name)
    }

    fun login(request: LoginRequestDTO): AuthResponseDTO {
        try {
            // Authenticate user
            val authToken = UsernamePasswordAuthenticationToken(request.email, request.password)
            authenticationManager.authenticate(authToken)
            
            // Find user
            val user = userRepository.findByEmail(request.email)
                ?: throw UserNotFoundException(request.email)
            
            // Generate token
            val token = jwtService.generateToken(user.email, user.role)
            
            // Record successful login
            metricsService.recordAuthSuccess()
            
            return AuthResponseDTO(token, user.role.name)
            
        } catch (ex: BadCredentialsException) {
            // Record failed login attempt
            metricsService.recordAuthFailure()
            throw InvalidCredentialsException("Invalid email or password")
        }
    }
}
