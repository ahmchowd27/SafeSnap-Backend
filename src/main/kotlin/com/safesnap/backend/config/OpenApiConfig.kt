package com.safesnap.backend.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun customOpenAPI(): OpenAPI {
        return OpenAPI()
            .info(
                Info()
                    .title("SafeSnap API")
                    .description("""
                        ## SafeSnap Safety Incident Reporting System
                        
                        A comprehensive API for construction and warehouse safety incident reporting.
                        
                        ### Features:
                        - 🔐 JWT-based authentication with role-based access control
                        - 📸 Secure file upload with S3 pre-signed URLs
                        - 🤖 AI-powered image analysis for safety detection
                        - 📊 Incident management with advanced filtering
                        - 👥 Role separation (WORKER/MANAGER)
                        
                        ### Getting Started:
                        1. Register a new user account
                        2. Login to receive a JWT token
                        3. Use the token in the 'Authorization' header for protected endpoints
                        
                        ### Authentication:
                        Use the 'Authorize' button below to add your JWT token.
                        Format: `Bearer your-jwt-token-here`
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("SafeSnap Development Team")
                            .email("support@safesnap.com")
                            .url("https://github.com/your-org/safesnap")
                    )
                    .license(
                        License()
                            .name("MIT License")
                            .url("https://opensource.org/licenses/MIT")
                    )
            )
            .addServersItem(
                Server()
                    .url("http://localhost:8080")
                    .description("Local Development Server")
            )
            .addServersItem(
                Server()
                    .url("https://api.safesnap.com")
                    .description("Production Server")
            )
            .addSecurityItem(SecurityRequirement().addList("JWT"))
            .components(
                io.swagger.v3.oas.models.Components()
                    .addSecuritySchemes("JWT",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                            .description("JWT token obtained from /api/auth/login endpoint")
                    )
            )
    }
}
