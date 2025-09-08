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
import org.slf4j.MDC
import org.springframework.core.Ordered
import org.springframework.core.annotation.Order
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter
import java.util.UUID

/**
 * Adds structured context (MDC) so Logback JSON encoder outputs rich fields per log line.
 * Avoids putting sensitive data (e.g. passwords, tokens) into logs.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
class CorrelationIdFilter : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val correlationId = extractOrGenerateCorrelationId(request)
        val auth = SecurityContextHolder.getContext().authentication
        val userEmail = if (auth != null && auth.isAuthenticated && auth.name != "anonymousUser") auth.name else null
        val clientIp = clientIp(request)

        MDC.put("requestId", correlationId)
        MDC.put("method", request.method)
        MDC.put("path", request.requestURI)
        MDC.put("clientIp", clientIp)
        userEmail?.let { MDC.put("userEmail", it) }

        try {
            response.setHeader("X-Request-ID", correlationId)
            filterChain.doFilter(request, response)
        } finally {
            // Clean to avoid thread-local leakage in pooled threads
            MDC.remove("requestId")
            MDC.remove("method")
            MDC.remove("path")
            MDC.remove("clientIp")
            MDC.remove("userEmail")
        }
    }

    private fun extractOrGenerateCorrelationId(request: HttpServletRequest): String {
        val header = request.getHeader("X-Request-ID") ?: request.getHeader("X-Correlation-ID")
        return header?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
    }

    private fun clientIp(request: HttpServletRequest): String {
        val xff = request.getHeader("X-Forwarded-For")
        if (!xff.isNullOrBlank()) return xff.split(',')[0].trim()
        val real = request.getHeader("X-Real-IP")
        if (!real.isNullOrBlank()) return real
        return request.remoteAddr ?: "unknown"
    }
}

