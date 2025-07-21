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
    
    // Helper method to time operations
    fun <T> timeImageProcessing(operation: () -> T): T {
        return imageProcessingTimer.recordCallable(operation)!!
    }
}
