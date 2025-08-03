package com.safesnap.backend.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestTemplate
import java.time.Duration
import org.springframework.boot.web.client.RestTemplateBuilder

@Configuration
class RestClientConfig {
    
    /**
     * RestTemplate bean for HTTP requests (used by OpenAI service)
     */
    @Bean
    fun restTemplate(builder: RestTemplateBuilder): RestTemplate {
        return builder
            .setConnectTimeout(Duration.ofSeconds(30))
            .setReadTimeout(Duration.ofSeconds(60))
            .build()
    }
}