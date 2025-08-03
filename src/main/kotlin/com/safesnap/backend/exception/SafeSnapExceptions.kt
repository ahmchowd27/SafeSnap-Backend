package com.safesnap.backend.exception

import java.util.*

// Base Exception
abstract class SafeSnapException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

// Authentication Exceptions
class EmailAlreadyExistsException(email: String) : SafeSnapException("Email already registered: $email")

class InvalidCredentialsException(message: String = "Invalid email or password") : SafeSnapException(message)

class UserNotFoundException(email: String) : SafeSnapException("User not found: $email")

// Validation Exceptions
class InvalidEmailFormatException(email: String) : SafeSnapException("Invalid email format: $email")

class WeakPasswordException(message: String) : SafeSnapException(message)

// Authorization Exceptions  
class UnauthorizedAccessException(message: String = "Unauthorized access") : SafeSnapException(message)

class InvalidTokenException(message: String = "Invalid or expired token") : SafeSnapException(message)

// Business Logic Exceptions
class IncidentCreationException(message: String) : SafeSnapException(message)

class IncidentNotFoundException(id: UUID) : SafeSnapException("Incident not found with ID: $id")

class FileUploadException(message: String) : SafeSnapException(message)

class S3ServiceException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

class VisionApiException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

class ImageProcessingException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

class AudioTranscriptionException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

// RCA Related Exceptions
class RcaReportNotFoundException(id: UUID) : SafeSnapException("RCA Report not found with ID: $id")

class RcaReportAlreadyExistsException(incidentId: UUID) : SafeSnapException("RCA Report already exists for incident: $incidentId")

class InvalidRcaDataException(message: String) : SafeSnapException(message)

// OpenAI and Rate Limiting Exceptions
class RateLimitExceededException(message: String) : SafeSnapException(message)

class OpenAiServiceException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

class RcaGenerationException(message: String, cause: Throwable? = null) : SafeSnapException(message, cause)

class IncidentCategorizationException(message: String) : SafeSnapException(message)

class TemplateNotFoundException(category: String) : SafeSnapException("Template not found for category: $category")
