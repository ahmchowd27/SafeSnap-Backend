/*
 *
 *  * Copyright (c) 2025 SafeSnap Development Team
 *  * Licensed under the MIT License
 *  * See LICENSE file in the project root for full license information
 *
 */

package com.safesnap.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.cors.CorsConfiguration
import org.springframework.web.cors.CorsConfigurationSource
import org.springframework.web.cors.UrlBasedCorsConfigurationSource

@Configuration
class CorsConfig {
    @Bean
    fun corsConfigurationSource(): CorsConfigurationSource {
        val config = CorsConfiguration()
        // Allow any origin TEMPORARILY for development/demo
        config.allowedOriginPatterns = listOf("*")
        // Allow common methods
        config.allowedMethods = listOf("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
        // Allow any header
        config.allowedHeaders = listOf("*")
        // Must be false with wildcard origins per CORS spec
        config.allowCredentials = false
        // Cache preflight for 1 hour
        config.maxAge = 3600

        val source = UrlBasedCorsConfigurationSource()
        source.registerCorsConfiguration("/**", config)
        return source
    }
}

