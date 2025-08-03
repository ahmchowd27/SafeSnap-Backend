package com.safesnap.backend.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Contact
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.info.License
import io.swagger.v3.oas.models.security.SecurityRequirement
import io.swagger.v3.oas.models.security.SecurityScheme
import io.swagger.v3.oas.models.servers.Server
import org.springdoc.core.models.GroupedOpenApi
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
                        SafeSnap Safety Incident Reporting System API
                        
                        Features:
                        - JWT-based authentication
                        - Role-based access control
                        - File upload with S3
                        - AI-powered analysis
                        
                        Authentication:
                        Use 'Bearer your-jwt-token' in Authorization header
                    """.trimIndent())
                    .version("1.0.0")
                    .contact(
                        Contact()
                            .name("SafeSnap Team")
                            .email("support@safesnap.com")
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
                    .description("Local Development")
            )
            .addSecurityItem(SecurityRequirement().addList("JWT"))
            .components(
                io.swagger.v3.oas.models.Components()
                    .addSecuritySchemes("JWT",
                        SecurityScheme()
                            .type(SecurityScheme.Type.HTTP)
                            .scheme("bearer")
                            .bearerFormat("JWT")
                    )
            )
    }

    @Bean
    fun publicApi(): GroupedOpenApi {
        return GroupedOpenApi.builder()
            .group("public")
            .pathsToMatch("/api/**")
            .build()
    }
}
