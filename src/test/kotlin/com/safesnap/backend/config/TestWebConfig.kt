package com.safesnap.backend.config

import org.springframework.context.annotation.Configuration
import org.springframework.context.annotation.Profile
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
@Profile("test")
class TestWebConfig : WebMvcConfigurer {
    // Override the main WebConfig to disable rate limiting in tests
    // No interceptors are added, so rate limiting is completely disabled
}
