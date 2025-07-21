package com.safesnap.backend.controller

import io.micrometer.core.instrument.MeterRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/metrics")
class MetricsController(
    private val meterRegistry: MeterRegistry
) {

    @GetMapping("/summary")
    fun getMetricsSummary(): ResponseEntity<Map<String, Any>> {
        val summary = mutableMapOf<String, Any>()

        // Business metrics
        val incidentsCreated = meterRegistry.counter("safesnap.incidents.created").count()
        val authSuccesses = meterRegistry.counter("safesnap.auth.success").count()
        val authFailures = meterRegistry.counter("safesnap.auth.failure").count()
        val visionApiCalls = meterRegistry.counter("safesnap.vision.api.calls").count()

        // File upload metrics by type
        val imageUploads = meterRegistry.find("safesnap.files.uploaded")
            .tag("type", "image")
            .counter()?.count() ?: 0.0

        val audioUploads = meterRegistry.find("safesnap.files.uploaded")
            .tag("type", "audio")
            .counter()?.count() ?: 0.0

        summary["business_metrics"] = mapOf(
            "incidents_created" to incidentsCreated,
            "files_uploaded" to mapOf(
                "images" to imageUploads,
                "audio" to audioUploads,
                "total" to (imageUploads + audioUploads)
            ),
            "authentication" to mapOf(
                "successful_logins" to authSuccesses,
                "failed_logins" to authFailures,
                "success_rate" to if (authSuccesses + authFailures > 0)
                    (authSuccesses / (authSuccesses + authFailures) * 100).toInt() else 0
            ),
            "vision_api_calls" to visionApiCalls
        )

        // System metrics
        val httpRequests = meterRegistry.find("http.server.requests").counters()
            .sumOf { it.count() }

        summary["system_metrics"] = mapOf(
            "total_http_requests" to httpRequests,
            "jvm_memory_used" to (meterRegistry.find("jvm.memory.used").gauge()?.value() ?: 0.0),
            "jvm_memory_max" to (meterRegistry.find("jvm.memory.max").gauge()?.value() ?: 0.0)
        )

        return ResponseEntity.ok(summary)
    }
}