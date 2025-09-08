/*
 *
 *  * Copyright (c) 2025 SafeSnap Development Team
 *  * Licensed under the MIT License
 *  * See LICENSE file in the project root for full license information
 *
 */

package com.safesnap.backend.logging

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.slf4j.MDC
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import org.springframework.web.util.ContentCachingRequestWrapper
import org.springframework.web.util.ContentCachingResponseWrapper
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.beans.factory.annotation.Value
import java.io.IOException

/**
 * Lightweight access log (human‑readable) focused on request/response lifecycle.
 * Captures: method, path, query, status, duration, user, IP, sizes, correlationId, error message.
 * Optionally includes truncated response body when an error (status >= 400) occurs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 25)
@ConditionalOnProperty(prefix = "access-log", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class AccessLoggingFilter(
    @Value("\${access-log.log-body-on-error:true}") private val logBodyOnError: Boolean,
    @Value("\${access-log.max-body-length:1000}") private val maxBodyLength: Int,
    @Value("\${access-log.log-request-body:false}") private val logRequestBody: Boolean,
    @Value("\${access-log.log-response-body-mode:ERROR}") private val responseBodyModeRaw: String,
    @Value("\${access-log.max-request-body-length:2000}") private val maxRequestBodyLength: Int,
    @Value("\${access-log.max-response-body-length:4000}") private val maxResponseBodyLength: Int
) : OncePerRequestFilter() {

    private val log = LoggerFactory.getLogger("ACCESS")
    private enum class ResponseBodyMode { NONE, ERROR, ALWAYS }
    private val responseBodyMode = runCatching { ResponseBodyMode.valueOf(responseBodyModeRaw.uppercase()) }
        .getOrElse { ResponseBodyMode.ERROR }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val startNs = System.nanoTime()
        val wrappedRequest = ContentCachingRequestWrapper(request)
        val wrappedResponse = ContentCachingResponseWrapper(response)

        var thrown: Throwable? = null
        try {
            filterChain.doFilter(wrappedRequest, wrappedResponse)
        } catch (ex: Throwable) {
            thrown = ex
            // Re-throw after capturing so Spring's exception handling still works
            throw ex
        } finally {
            val durationMs = (System.nanoTime() - startNs) / 1_000_000
            val status = wrappedResponse.status
            val method = request.method
            val uri = request.requestURI
            val query = request.queryString
            val clientIp = clientIp(request)
            val user = MDC.get("userEmail") ?: "anon"
            val requestId = MDC.get("requestId") ?: "-"

            val requestSize = safeSize(wrappedRequest.contentAsByteArray.size)
            val responseSize = safeSize(wrappedResponse.contentAsByteArray.size)

            var errorBodySnippet: String? = null
            if (status >= 400 && logBodyOnError && isTextLike(wrappedResponse.contentType)) {
                errorBodySnippet = buildResponseSnippet(wrappedResponse, maxBodyLength)
            }

            // Determine if we should capture full response body per config
            var responseBodySnippet: String? = null
            if (shouldLogResponseBody(status) && isTextLike(wrappedResponse.contentType)) {
                responseBodySnippet = buildResponseSnippet(wrappedResponse, maxResponseBodyLength)
            }

            var requestBodySnippet: String? = null
            if (logRequestBody && isTextLike(wrappedRequest.contentType)) {
                requestBodySnippet = buildRequestSnippet(wrappedRequest, maxRequestBodyLength)
            }

            val errorMessage = when {
                thrown != null -> thrown.javaClass.simpleName + (thrown.message?.let { ": $it" } ?: "")
                status >= 500 -> "SERVER_ERROR"
                status >= 400 -> "CLIENT_ERROR"
                else -> null
            }

            // Build concise line (avoid JSON since user requested readable access log)
            val sb = StringBuilder().apply {
                append("method=").append(method)
                append(" path=").append(uri)
                if (!query.isNullOrBlank()) append("?${query}")
                append(" status=").append(status)
                append(" durMs=").append(durationMs)
                append(" user=").append(user)
                append(" ip=").append(clientIp)
                append(" reqBytes=").append(requestSize)
                append(" resBytes=").append(responseSize)
                append(" reqId=").append(requestId)
                errorMessage?.let { append(" error=").append(it) }
                errorBodySnippet?.let { append(" bodySnippet=\"").append(it.replace("\n", "\\n")).append("\"") }
                requestBodySnippet?.let { append(" reqBody=\"").append(it).append("\"") }
                responseBodySnippet?.let { append(" resBody=\"").append(it).append("\"") }
            }

            when {
                thrown != null -> log.error(sb.toString(), thrown)
                status >= 500 -> log.error(sb.toString())
                status >= 400 -> log.warn(sb.toString())
                else -> log.info(sb.toString())
            }

            // Important: copy cached body back to real response
            try { wrappedResponse.copyBodyToResponse() } catch (_: IOException) { }
        }
    }

    private fun clientIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) return xff.split(',')[0].trim()
        val real = request.getHeader("X-Real-IP")
        if (!real.isNullOrBlank()) return real
        return request.remoteAddr ?: "unknown"
    }

    private fun safeSize(size: Int?): Int = size ?: 0

    // Moved up so it's defined before usage
    private fun truncateAndMask(raw: String, max: Int): String {
        val masked = maskSensitive(raw)
        return if (masked.length > max) masked.substring(0, max) + "…(truncated)" else masked
    }

    private fun buildRequestSnippet(req: ContentCachingRequestWrapper, max: Int): String? {
        val bytes = req.contentAsByteArray
        if (bytes.isEmpty()) return null
        val raw = bytes.toString(Charsets.UTF_8)
        return truncateAndMask(raw, max)
    }

    private fun buildResponseSnippet(res: ContentCachingResponseWrapper, max: Int): String? {
        val bytes = res.contentAsByteArray
        if (bytes.isEmpty()) return null
        val raw = bytes.toString(Charsets.UTF_8)
        return truncateAndMask(raw, max)
    }

    private fun maskSensitive(s: String): String {
        val patterns = listOf(
            Regex("(?i)\"password\"\\s*:\\s*\"([^\"]*)\"") to "\"password\":\"***\"",
            Regex("(?i)\"secret\"\\s*:\\s*\"([^\"]*)\"") to "\"secret\":\"***\"",
            Regex("(?i)\"token\"\\s*:\\s*\"([^\"]*)\"") to "\"token\":\"***\""
        )
        var result = s
        patterns.forEach { (regex, replacement) ->
            result = regex.replace(result, replacement)
        }
        return result.replace("\n", "\\n").replace("\r", "")
    }

    private fun shouldLogResponseBody(status: Int): Boolean = when (responseBodyMode) {
        ResponseBodyMode.NONE -> false
        ResponseBodyMode.ERROR -> status >= 400
        ResponseBodyMode.ALWAYS -> true
    }

    private fun isTextLike(contentType: String?): Boolean {
        if (contentType.isNullOrBlank()) return false
        val ct = contentType.lowercase()
        if (ct.startsWith("multipart/")) return false // skip file uploads
        return (ct.startsWith("application/json") ||
                ct.startsWith("text/") ||
                ct.contains("xml") ||
                ct.contains("javascript"))
    }
}
