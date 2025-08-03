package com.safesnap.backend.service

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.safesnap.backend.exception.RateLimitExceededException
import com.safesnap.backend.exception.OpenAiServiceException
import io.github.bucket4j.Bucket
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Refill
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.*
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Service
class OpenAiService(
    @Value("\${openai.api.key:}") private val apiKey: String,
    @Value("\${openai.api.url:https://api.openai.com/v1/chat/completions}") private val apiUrl: String,
    @Value("\${openai.model:gpt-3.5-turbo}") private val model: String,
    @Value("\${openai.max-tokens:1200}") private val maxTokens: Int,
    @Value("\${openai.temperature:0.3}") private val temperature: Double,
    @Value("\${openai.enabled:true}") private val enabled: Boolean,
    @Value("\${openai.mock-mode:false}") private val mockMode: Boolean,
    @Value("\${openai.rate-limit.requests-per-minute:20}") private val requestsPerMinute: Int,
    @Value("\${openai.rate-limit.tokens-per-minute:40000}") private val tokensPerMinute: Int,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService
) {
    
    private val logger = LoggerFactory.getLogger(OpenAiService::class.java)
    
    // Rate limiting buckets - per service instance
    private val requestBucket: Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(requestsPerMinute.toLong(), Refill.intervally(requestsPerMinute.toLong(), Duration.ofMinutes(1))))
        .build()
    
    private val tokenBucket: Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(tokensPerMinute.toLong(), Refill.intervally(tokensPerMinute.toLong(), Duration.ofMinutes(1))))
        .build()
    
    // Track user-specific rate limits
    private val userRequestBuckets = ConcurrentHashMap<String, Bucket>()
    
    /**
     * Generate RCA analysis using OpenAI GPT-3.5-turbo
     */
    fun generateRcaAnalysis(
        prompt: String,
        incidentContext: Map<String, Any>,
        userEmail: String? = null
    ): OpenAiResponse {
        
        // Check if service is enabled
        if (!enabled) {
            logger.info("OpenAI service is disabled, returning mock response")
            return getMockRcaResponse()
        }
        
        if (mockMode) {
            logger.info("OpenAI service in mock mode")
            return getMockRcaResponse()
        }
        
        // Rate limiting checks
        checkRateLimits(userEmail, estimateTokens(prompt))
        
        return metricsService.timeOpenAiRequest {
            try {
                val startTime = System.currentTimeMillis()
                
                logger.info("Generating RCA analysis with OpenAI for user: $userEmail")
                logger.debug("Prompt length: ${prompt.length} characters")
                
                val response = callOpenAiApi(prompt)
                val processingTime = System.currentTimeMillis() - startTime
                
                logger.info("OpenAI request completed in ${processingTime}ms")
                metricsService.recordOpenAiSuccess()
                
                response.copy(processingTimeMs = processingTime)
                
            } catch (e: HttpClientErrorException) {
                logger.error("OpenAI API client error: ${e.statusCode} - ${e.responseBodyAsString}")
                metricsService.recordOpenAiError("client_error")
                
                when (e.statusCode) {
                    HttpStatus.TOO_MANY_REQUESTS -> throw RateLimitExceededException("OpenAI rate limit exceeded")
                    HttpStatus.UNAUTHORIZED -> throw OpenAiServiceException("Invalid OpenAI API key")
                    HttpStatus.BAD_REQUEST -> throw OpenAiServiceException("Invalid request: ${e.responseBodyAsString}")
                    else -> throw OpenAiServiceException("OpenAI API error: ${e.message}")
                }
            } catch (e: HttpServerErrorException) {
                logger.error("OpenAI API server error: ${e.statusCode}")
                metricsService.recordOpenAiError("server_error")
                throw OpenAiServiceException("OpenAI service unavailable: ${e.message}")
            } catch (e: Exception) {
                logger.error("Unexpected error calling OpenAI API", e)
                metricsService.recordOpenAiError("unexpected_error")
                throw OpenAiServiceException("RCA generation failed: ${e.message}")
            }
        }
    }
    
    /**
     * Check rate limits before making API call
     */
    private fun checkRateLimits(userEmail: String?, estimatedTokens: Int) {
        // Global service rate limit
        if (!requestBucket.tryConsume(1)) {
            logger.warn("Service-wide OpenAI rate limit exceeded")
            throw RateLimitExceededException("Service rate limit exceeded. Please try again later.")
        }

        if (!tokenBucket.tryConsume(estimatedTokens.toLong())) {
            logger.warn("Service-wide OpenAI token rate limit exceeded")
            throw RateLimitExceededException("Token rate limit exceeded. Please try again later.")
        }

        // User-specific rate limit (optional)
        userEmail?.let { email ->
            val userBucket = getUserRequestBucket(email)
            if (!userBucket.tryConsume(1)) {
                logger.warn("User-specific OpenAI rate limit exceeded for: $email")
                throw RateLimitExceededException("User rate limit exceeded. Please try again later.")
            }
        }

        logger.debug("Rate limit check passed for user: $userEmail")
    }

    /**
     * Get or create user-specific rate limit bucket
     */
    private fun getUserRequestBucket(userEmail: String): Bucket {
        return userRequestBuckets.computeIfAbsent(userEmail) {
            Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1)))) // 5 requests per minute per user
                .build()
        }
    }
    
    /**
     * Estimate token count for rate limiting (rough approximation)
     */
    private fun estimateTokens(text: String): Int {
        // Rough estimation: 1 token ≈ 4 characters for English text
        // Add some buffer for response tokens
        return (text.length / 4) + maxTokens
    }
    
    /**
     * Make actual API call to OpenAI
     */
    private fun callOpenAiApi(prompt: String): OpenAiResponse {
        val headers = HttpHeaders().apply {
            contentType = MediaType.APPLICATION_JSON
            set("Authorization", "Bearer $apiKey")
        }
        
        val requestBody = mapOf(
            "model" to model,
            "messages" to listOf(
                mapOf(
                    "role" to "system",
                    "content" to "You are a professional safety expert specializing in workplace incident analysis and root cause analysis for construction and warehouse environments."
                ),
                mapOf(
                    "role" to "user", 
                    "content" to prompt
                )
            ),
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "top_p" to 1.0,
            "frequency_penalty" to 0.0,
            "presence_penalty" to 0.0
        )
        
        val entity = HttpEntity(requestBody, headers)
        
        logger.debug("Sending request to OpenAI API: $apiUrl")
        
        val response = restTemplate.postForEntity(apiUrl, entity, String::class.java)
        
        if (response.statusCode != HttpStatus.OK) {
            throw OpenAiServiceException("OpenAI API returned status: ${response.statusCode}")
        }
        
        return parseOpenAiResponse(response.body ?: throw OpenAiServiceException("Empty response from OpenAI"))
    }
    
    /**
     * Parse OpenAI API response
     */
    private fun parseOpenAiResponse(responseBody: String): OpenAiResponse {
        try {
            val jsonNode = objectMapper.readTree(responseBody)
            
            val choices = jsonNode.get("choices")
            if (choices == null || choices.isEmpty) {
                throw OpenAiServiceException("No choices in OpenAI response")
            }
            
            val firstChoice = choices.get(0)
            val message = firstChoice.get("message")
            val content = message.get("content").asText()
            
            val usage = jsonNode.get("usage")
            val tokensUsed = usage?.get("total_tokens")?.asInt() ?: 0
            
            logger.debug("OpenAI response parsed successfully. Tokens used: $tokensUsed")
            
            return OpenAiResponse(
                content = content,
                tokensUsed = tokensUsed,
                model = model,
                success = true
            )
            
        } catch (e: Exception) {
            logger.error("Failed to parse OpenAI response", e)
            throw OpenAiServiceException("Failed to parse OpenAI response: ${e.message}")
        }
    }
    
    /**
     * Mock response for development/testing
     */
    private fun getMockRcaResponse(): OpenAiResponse {
        logger.info("Returning mock OpenAI RCA response")
        
        val mockContent = """
            FIVE WHYS:
            1. Why did this incident occur? Worker was not wearing required hard hat while operating near overhead hazards
            2. Why was the worker not wearing a hard hat? The hard hat was left at the previous work station and worker forgot to retrieve it
            3. Why wasn't this prevented by safety protocols? The site safety checklist was not properly enforced at shift start
            4. Why isn't the safety checklist being enforced? Supervisors are not conducting mandatory PPE inspections due to time pressure
            5. Why isn't management ensuring adequate time for safety protocols? Production targets are prioritized over safety compliance procedures
            
            CORRECTIVE ACTIONS (Immediate - next 24-48 hours):
            - Issue replacement hard hat to worker immediately
            - Conduct mandatory PPE inspection for all workers on site
            - Supervisor to complete safety incident documentation and reporting
            - Review and reinforce hard hat policy with all crew members
            
            PREVENTIVE ACTIONS (Long-term - next 30-90 days):
            - Implement mandatory PPE check stations at all work area entrances
            - Provide additional hard hat storage locations throughout job site
            - Revise shift start procedures to include verified PPE compliance check
            - Train supervisors on safety-first culture and proper enforcement techniques
            - Review production targets to ensure adequate time for safety protocols
        """.trimIndent()
        
        return OpenAiResponse(
            content = mockContent,
            tokensUsed = 450,
            model = "mock-gpt-3.5-turbo",
            success = true,
            processingTimeMs = 1200
        )
    }
    
    /**
     * Get service status and configuration
     */
    fun getServiceStatus(): Map<String, Any> {
        return mapOf(
            "enabled" to enabled,
            "mock_mode" to mockMode,
            "model" to model,
            "max_tokens" to maxTokens,
            "temperature" to temperature,
            "api_key_configured" to apiKey.isNotBlank(),
            "rate_limits" to mapOf(
                "requests_per_minute" to requestsPerMinute,
                "tokens_per_minute" to tokensPerMinute,
                "request_bucket_available" to requestBucket.availableTokens,
                "token_bucket_available" to tokenBucket.availableTokens
            ),
            "status" to if (enabled) {
                if (mockMode) "MOCK_MODE" else "REAL_API"
            } else "DISABLED"
        )
    }
    
    /**
     * Health check for monitoring
     */
    fun healthCheck(): Boolean {
        return if (enabled && !mockMode) {
            apiKey.isNotBlank()
        } else {
            true // Always healthy in mock mode or when disabled
        }
    }
}

/**
 * OpenAI API response wrapper
 */
data class OpenAiResponse(
    val content: String,
    val tokensUsed: Int,
    val model: String,
    val success: Boolean,
    val processingTimeMs: Long? = null,
    val errorMessage: String? = null
)
