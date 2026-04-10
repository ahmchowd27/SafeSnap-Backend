package com.safesnap.backend.service

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
    @Value("\${openai.rate-limit.requests-per-minute:20}") private val requestsPerMinute: Int,
    @Value("\${openai.rate-limit.tokens-per-minute:40000}") private val tokensPerMinute: Int,
    private val restTemplate: RestTemplate,
    private val objectMapper: ObjectMapper,
    private val metricsService: MetricsService
) {

    private val logger = LoggerFactory.getLogger(OpenAiService::class.java)

    private val requestBucket: Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(requestsPerMinute.toLong(), Refill.intervally(requestsPerMinute.toLong(), Duration.ofMinutes(1))))
        .build()

    private val tokenBucket: Bucket = Bucket.builder()
        .addLimit(Bandwidth.classic(tokensPerMinute.toLong(), Refill.intervally(tokensPerMinute.toLong(), Duration.ofMinutes(1))))
        .build()

    private val userRequestBuckets = ConcurrentHashMap<String, Bucket>()

    fun generateRcaAnalysis(
        prompt: String,
        incidentContext: Map<String, Any>,
        userEmail: String? = null
    ): OpenAiResponse {
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

    private fun checkRateLimits(userEmail: String?, estimatedTokens: Int) {
        if (!requestBucket.tryConsume(1)) {
            logger.warn("Service-wide OpenAI rate limit exceeded")
            throw RateLimitExceededException("Service rate limit exceeded. Please try again later.")
        }

        if (!tokenBucket.tryConsume(estimatedTokens.toLong())) {
            logger.warn("Service-wide OpenAI token rate limit exceeded")
            throw RateLimitExceededException("Token rate limit exceeded. Please try again later.")
        }

        userEmail?.let { email ->
            val userBucket = getUserRequestBucket(email)
            if (!userBucket.tryConsume(1)) {
                logger.warn("User-specific OpenAI rate limit exceeded for: $email")
                throw RateLimitExceededException("User rate limit exceeded. Please try again later.")
            }
        }

        logger.debug("Rate limit check passed for user: $userEmail")
    }

    private fun getUserRequestBucket(userEmail: String): Bucket {
        return userRequestBuckets.computeIfAbsent(userEmail) {
            Bucket.builder()
                .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(1))))
                .build()
        }
    }

    private fun estimateTokens(text: String): Int {
        return (text.length / 4) + maxTokens
    }

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

    private fun parseOpenAiResponse(responseBody: String): OpenAiResponse {
        try {
            val jsonNode = objectMapper.readTree(responseBody)

            val choices = jsonNode.get("choices")
            if (choices == null || choices.isEmpty) {
                throw OpenAiServiceException("No choices in OpenAI response")
            }

            val content = choices.get(0).get("message").get("content").asText()
            val tokensUsed = jsonNode.get("usage")?.get("total_tokens")?.asInt() ?: 0

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

    fun healthCheck(): Boolean = apiKey.isNotBlank()
}

data class OpenAiResponse(
    val content: String,
    val tokensUsed: Int,
    val model: String,
    val success: Boolean,
    val processingTimeMs: Long? = null,
    val errorMessage: String? = null
)
