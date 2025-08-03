package com.safesnap.backend.service

import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.springframework.stereotype.Service

@Service
class MetricsService(
    private val meterRegistry: MeterRegistry
) {
    
    // Counters for business metrics
    private val incidentsCreatedCounter = Counter.builder("safesnap.incidents.created")
        .description("Total number of incidents created")
        .register(meterRegistry)
        
    private val filesUploadedCounter = Counter.builder("safesnap.files.uploaded")
        .description("Total number of files uploaded")
        .tag("type", "unknown")
        .register(meterRegistry)
        
    private val authSuccessCounter = Counter.builder("safesnap.auth.success")
        .description("Successful authentication attempts")
        .register(meterRegistry)
        
    private val authFailureCounter = Counter.builder("safesnap.auth.failure")
        .description("Failed authentication attempts")
        .register(meterRegistry)
        
    private val visionApiCallsCounter = Counter.builder("safesnap.vision.api.calls")
        .description("Google Vision API calls made")
        .register(meterRegistry)
    
    // Timers for performance metrics
    private val imageProcessingTimer = Timer.builder("safesnap.image.processing.duration")
        .description("Time taken to process images")
        .register(meterRegistry)

    private val openAiRequestTimer = Timer.builder("safesnap.openai.request.duration")
        .description("Time taken for OpenAI API requests")
        .register(meterRegistry)

    private val rcaGenerationTimer = Timer.builder("safesnap.rca.generation.duration")
        .description("Time taken to generate RCA suggestions")
        .register(meterRegistry)

    // Business metric methods
    fun recordIncidentCreated() {
        incidentsCreatedCounter.increment()
    }
    
    fun recordFileUploaded(fileType: String) {
        Counter.builder("safesnap.files.uploaded")
            .description("Total number of files uploaded")
            .tag("type", fileType.lowercase())
            .register(meterRegistry)
            .increment()
    }
    
    fun recordAuthSuccess() {
        authSuccessCounter.increment()
    }
    
    fun recordAuthFailure() {
        authFailureCounter.increment()
    }
    
    fun recordVisionApiCall() {
        visionApiCallsCounter.increment()
    }
    
    fun recordImageProcessingTime(duration: Long) {
        imageProcessingTimer.record(duration, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
    
    // OpenAI Metrics
    fun recordOpenAiSuccess() {
        Counter.builder("safesnap.openai.requests")
            .description("OpenAI API requests")
            .tag("status", "success")
            .register(meterRegistry)
            .increment()
    }

    fun recordOpenAiError(errorType: String) {
        Counter.builder("safesnap.openai.requests")
            .description("OpenAI API requests")
            .tag("status", "error")
            .tag("type", errorType)
            .register(meterRegistry)
            .increment()
    }

    // RCA Generation Metrics
    fun recordRcaGenerated(category: String) {
        Counter.builder("safesnap.rca.generated")
            .description("RCA suggestions generated")
            .tag("category", category)
            .register(meterRegistry)
            .increment()
    }

    fun recordRcaFailed(errorType: String) {
        Counter.builder("safesnap.rca.failed")
            .description("RCA generation failures")
            .tag("error", errorType)
            .register(meterRegistry)
            .increment()
    }

    fun recordRcaApproved(category: String) {
        Counter.builder("safesnap.rca.approved")
            .description("RCA reports approved by managers")
            .tag("category", category)
            .register(meterRegistry)
            .increment()
    }
    
    // Helper methods to time operations
    fun <T> timeImageProcessing(operation: () -> T): T {
        return imageProcessingTimer.recordCallable(operation)!!
    }

    fun <T> timeOpenAiRequest(operation: () -> T): T {
        return openAiRequestTimer.recordCallable(operation)!!
    }

    fun <T> timeRcaGeneration(operation: () -> T): T {
        return rcaGenerationTimer.recordCallable(operation)!!
    }
}
