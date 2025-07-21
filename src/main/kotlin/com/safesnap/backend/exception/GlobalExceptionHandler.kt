package com.safesnap.backend.exception

import com.safesnap.backend.dto.error.ErrorResponseDTO
import com.safesnap.backend.dto.error.ValidationErrorResponseDTO
import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.authentication.BadCredentialsException
import org.springframework.security.core.userdetails.UsernameNotFoundException
import org.springframework.validation.FieldError
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

@RestControllerAdvice
class GlobalExceptionHandler {

    private val logger = LoggerFactory.getLogger(GlobalExceptionHandler::class.java)

    @ExceptionHandler(EmailAlreadyExistsException::class)
    fun handleEmailAlreadyExists(
        ex: EmailAlreadyExistsException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Email already exists: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.CONFLICT.value(),
            error = "Email Already Registered",
            message = ex.message ?: "Email already registered",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.CONFLICT).body(errorResponse)
    }

    @ExceptionHandler(InvalidCredentialsException::class, BadCredentialsException::class)
    fun handleInvalidCredentials(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Invalid credentials attempt: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Authentication Failed",
            message = "Invalid email or password",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    @ExceptionHandler(UserNotFoundException::class, UsernameNotFoundException::class)
    fun handleUserNotFound(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("User not found: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Authentication Failed", 
            message = "Invalid email or password", // Don't reveal user existence
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    @ExceptionHandler(UnauthorizedAccessException::class)
    fun handleUnauthorizedAccess(
        ex: UnauthorizedAccessException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Unauthorized access: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Unauthorized",
            message = ex.message ?: "Unauthorized access",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    @ExceptionHandler(InvalidTokenException::class)
    fun handleInvalidToken(
        ex: InvalidTokenException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Invalid token: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.UNAUTHORIZED.value(),
            error = "Invalid Token",
            message = ex.message ?: "Invalid or expired token",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(errorResponse)
    }

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun handleValidationErrors(
        ex: MethodArgumentNotValidException,
        request: HttpServletRequest
    ): ResponseEntity<ValidationErrorResponseDTO> {
        logger.warn("Validation error: ${ex.message}")
        
        val validationErrors = mutableMapOf<String, String>()
        ex.bindingResult.allErrors.forEach { error ->
            val fieldName = (error as FieldError).field
            val errorMessage = error.getDefaultMessage() ?: "Invalid value"
            validationErrors[fieldName] = errorMessage
        }
        
        val errorResponse = ValidationErrorResponseDTO(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Validation Failed",
            message = "Invalid input data",
            path = request.requestURI,
            validationErrors = validationErrors
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(
        ex: IllegalArgumentException,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.warn("Illegal argument: ${ex.message}")
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.BAD_REQUEST.value(),
            error = "Bad Request",
            message = ex.message ?: "Invalid request data",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errorResponse)
    }

    @ExceptionHandler(Exception::class)
    fun handleGenericException(
        ex: Exception,
        request: HttpServletRequest
    ): ResponseEntity<ErrorResponseDTO> {
        logger.error("Unexpected error: ${ex.message}", ex)
        
        val errorResponse = ErrorResponseDTO(
            status = HttpStatus.INTERNAL_SERVER_ERROR.value(),
            error = "Internal Server Error",
            message = "An unexpected error occurred",
            path = request.requestURI
        )
        
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse)
    }
}
