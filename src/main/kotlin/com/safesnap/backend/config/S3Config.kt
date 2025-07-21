package com.safesnap.backend.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3Client
import software.amazon.awssdk.services.s3.presigner.S3Presigner
import java.net.URI

@Configuration
class S3Config(
    @Value("\${aws.access-key-id}") private val accessKeyId: String,
    @Value("\${aws.secret-access-key}") private val secretAccessKey: String,
    @Value("\${aws.region}") private val region: String,
    @Value("\${aws.endpoint-url:}") private val endpointUrl: String
) {

    @Bean
    fun s3Client(): S3Client {
        val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        val credentialsProvider = StaticCredentialsProvider.create(credentials)
        
        val clientBuilder = S3Client.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
        

        if (endpointUrl.isNotBlank()) {
            clientBuilder.endpointOverride(URI.create(endpointUrl))
                .forcePathStyle(true) // Required for LocalStack
        }
        
        return clientBuilder.build()
    }

    @Bean
    fun s3Presigner(): S3Presigner {
        val credentials = AwsBasicCredentials.create(accessKeyId, secretAccessKey)
        val credentialsProvider = StaticCredentialsProvider.create(credentials)
        
        val presignerBuilder = S3Presigner.builder()
            .credentialsProvider(credentialsProvider)
            .region(Region.of(region))
        
        // If endpoint URL is provided (LocalStack), use it
        if (endpointUrl.isNotBlank()) {
            presignerBuilder.endpointOverride(URI.create(endpointUrl))
        }
        
        return presignerBuilder.build()
    }
}
