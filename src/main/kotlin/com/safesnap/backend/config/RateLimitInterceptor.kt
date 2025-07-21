package com.safesnap.backend.config

import com.safesnap.backend.service.RateLimitService
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.beans.factory.annotation.Value
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.servlet.HandlerInterceptor

@Component
class RateLimitInterceptor(
    private val rateLimitService: RateLimitService,
    @Value("\${rate-limiting.enabled:true}") private val rateLimitingEnabled: Boolean
) : HandlerInterceptor {
    
    override fun preHandle(
        request: HttpServletRequest,
        response: HttpServletResponse,
        handler: Any
    ): Boolean {
        // Skip rate limiting if disabled (e.g., in test mode)
        if (!rateLimitingEnabled) {
            return true
        }
        
        val path = request.requestURI
        val method = request.method
        

        if (shouldSkipRateLimiting(path)) {
            return true
        }
        
        // Determine rate limit type based on endpoint
        val rateLimitType = determineRateLimitType(path, method)
        if (rateLimitType == null) {
            return true // No rate limiting for this endpoint
        }
        
        // Get rate limiting key (user or IP-based)
        val key = getRateLimitKey(request, path)
        
        // Check rate limit
        if (!rateLimitService.isAllowed(key, rateLimitType)) {
            // Rate limit exceeded
            response.status = 429 // Too Many Requests
            response.contentType = "application/json"
            
            val remaining = rateLimitService.getRemainingTokens(key, rateLimitType)
            val retryAfter = rateLimitService.getTimeUntilRefill(key, rateLimitType)
            
            response.addHeader("X-RateLimit-Limit", rateLimitType.capacity.toString())
            response.addHeader("X-RateLimit-Remaining", remaining.toString())
            response.addHeader("X-RateLimit-Type", rateLimitType.description)
            
            if (retryAfter != null) {
                response.addHeader("Retry-After", retryAfter.seconds.toString())
            }
            
            val errorResponse = """
                {
                    "error": "Rate Limit Exceeded",
                    "message": "Too many requests. ${rateLimitType.description}",
                    "remaining": $remaining,
                    "retryAfterSeconds": ${retryAfter?.seconds ?: "unknown"}
                }
            """.trimIndent()
            
            response.writer.write(errorResponse)
            return false
        }
        
        // Add rate limit headers to successful responses
        val remaining = rateLimitService.getRemainingTokens(key, rateLimitType)
        response.addHeader("X-RateLimit-Limit", rateLimitType.capacity.toString())
        response.addHeader("X-RateLimit-Remaining", remaining.toString())
        
        return true
    }
    
    private fun shouldSkipRateLimiting(path: String): Boolean {
        return path.startsWith("/actuator/") ||
               path.startsWith("/api/swagger-ui") ||
               path.startsWith("/api/docs") ||
               path.equals("/")
    }
    
    private fun determineRateLimitType(path: String, method: String): RateLimitService.RateLimitType? {
        return when {
            // Authentication endpoints
            path == "/api/auth/login" && method == "POST" -> 
                RateLimitService.RateLimitType.LOGIN_ATTEMPTS
            path == "/api/auth/register" && method == "POST" -> 
                RateLimitService.RateLimitType.REGISTRATION
            
            // File upload endpoints
            path == "/api/s3/upload-url" && method == "POST" -> 
                RateLimitService.RateLimitType.FILE_UPLOADS
            
            // Incident creation
            path == "/api/incidents" && method == "POST" -> 
                RateLimitService.RateLimitType.INCIDENT_CREATION
            
            // Vision API endpoints (expensive)
            path.startsWith("/api/image-analysis") -> 
                RateLimitService.RateLimitType.VISION_API
            
            // General API endpoints
            path.startsWith("/api/") -> 
                RateLimitService.RateLimitType.GENERAL_API
            
            else -> null
        }
    }
    
    private fun getRateLimitKey(request: HttpServletRequest, path: String): String {
        // Try to get authenticated user
        val authentication = SecurityContextHolder.getContext().authentication
        
        return if (authentication != null && authentication.isAuthenticated && authentication.name != "anonymousUser") {
            // User-based rate limiting for authenticated requests
            rateLimitService.getUserKey(authentication.name, getOperationName(path))
        } else {
            // IP-based rate limiting for anonymous requests
            val clientIp = getClientIpAddress(request)
            rateLimitService.getIpKey(clientIp, getOperationName(path))
        }
    }
    
    private fun getOperationName(path: String): String {
        return when {
            path.startsWith("/api/auth/") -> "auth"
            path.startsWith("/api/incidents") -> "incidents"
            path.startsWith("/api/s3/") -> "files"
            path.startsWith("/api/image-analysis") -> "vision"
            else -> "general"
        }
    }
    
    private fun getClientIpAddress(request: HttpServletRequest): String {
        val xForwardedFor = request.getHeader("X-Forwarded-For")
        return when {
            !xForwardedFor.isNullOrBlank() -> xForwardedFor.split(",")[0].trim()
            !request.getHeader("X-Real-IP").isNullOrBlank() -> request.getHeader("X-Real-IP")
            else -> request.remoteAddr ?: "unknown"
        }
    }
}
