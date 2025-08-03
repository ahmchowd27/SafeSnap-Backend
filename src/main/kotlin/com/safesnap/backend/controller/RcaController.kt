package com.safesnap.backend.controller

import com.safesnap.backend.dto.incident.RcaCreateDTO
import com.safesnap.backend.dto.incident.RcaResponseDTO
import com.safesnap.backend.service.RcaAiService
import com.safesnap.backend.service.RcaStatistics
import com.safesnap.backend.service.RcaServiceHealth
import com.safesnap.backend.entity.RcaAiSuggestion
import jakarta.validation.Valid
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import java.util.*

@RestController
@RequestMapping("/api/incidents/{incidentId}/rca")
class RcaController(
    private val rcaAiService: RcaAiService
) {

    /**
     * Get AI-generated RCA suggestions for manager review
     * GET /api/incidents/{incidentId}/rca/suggestions
     */
    @GetMapping("/suggestions")
    @PreAuthorize("hasRole('MANAGER')")
    fun getRcaAiSuggestions(
        @PathVariable incidentId: UUID,
        authentication: Authentication
    ): ResponseEntity<RcaAiSuggestionResponseDTO> {
        val suggestions = rcaAiService.getRcaSuggestions(incidentId)
            ?: return ResponseEntity.notFound().build()
        
        val response = RcaAiSuggestionResponseDTO.fromEntity(suggestions)
        return ResponseEntity.ok(response)
    }

    /**
     * Mark RCA suggestions as reviewed by manager
     * POST /api/incidents/{incidentId}/rca/suggestions/review
     */
    @PostMapping("/suggestions/review") 
    @PreAuthorize("hasRole('MANAGER')")
    fun reviewRcaSuggestions(
        @PathVariable incidentId: UUID,
        authentication: Authentication
    ): ResponseEntity<RcaAiSuggestionResponseDTO> {
        val reviewed = rcaAiService.markAsReviewed(incidentId, authentication.name)
        val response = RcaAiSuggestionResponseDTO.fromEntity(reviewed)
        return ResponseEntity.ok(response)
    }

    /**
     * Approve RCA suggestions to make them visible to workers
     * POST /api/incidents/{incidentId}/rca/suggestions/approve
     */
    @PostMapping("/suggestions/approve") 
    @PreAuthorize("hasRole('MANAGER')")
    fun approveRcaSuggestions(
        @PathVariable incidentId: UUID,
        authentication: Authentication
    ): ResponseEntity<RcaAiSuggestionResponseDTO> {
        val approved = rcaAiService.markAsApproved(incidentId, authentication.name)
        val response = RcaAiSuggestionResponseDTO.fromEntity(approved)
        return ResponseEntity.ok(response)
    }

    /**
     * Manager approves RCA with modifications and creates final report
     * POST /api/incidents/{incidentId}/rca/approve
     */
    @PostMapping("/approve")
    @PreAuthorize("hasRole('MANAGER')")  
    fun approveRcaWithModifications(
        @PathVariable incidentId: UUID,
        @Valid @RequestBody request: RcaCreateDTO,
        authentication: Authentication
    ): ResponseEntity<RcaResponseDTO> {
        val rcaReport = rcaAiService.createFinalRcaFromSuggestions(incidentId, request, authentication.name)
        val response = RcaResponseDTO.fromEntity(rcaReport)
        return ResponseEntity.ok(response)
    }

    /**
     * Get final RCA report (accessible to all authenticated users)
     * GET /api/incidents/{incidentId}/rca
     */
    @GetMapping
    fun getRcaReport(
        @PathVariable incidentId: UUID,
        authentication: Authentication
    ): ResponseEntity<RcaResponseDTO> {
        // This will be handled by the existing incident service
        // For now, return not implemented
        return ResponseEntity.notFound().build()
    }

    /**
     * Manually trigger AI RCA generation (if failed initially)
     * POST /api/incidents/{incidentId}/rca/suggestions/regenerate
     */
    @PostMapping("/suggestions/regenerate")
    @PreAuthorize("hasRole('MANAGER')")
    fun regenerateRcaSuggestions(
        @PathVariable incidentId: UUID,
        authentication: Authentication
    ): ResponseEntity<RcaAiSuggestionResponseDTO> {
        val suggestions = rcaAiService.retryFailedGeneration(incidentId)
        val response = RcaAiSuggestionResponseDTO.fromEntity(suggestions)
        return ResponseEntity.ok(response)
    }
}

/**
 * Separate controller for RCA management endpoints
 */
@RestController
@RequestMapping("/api/rca")
class RcaManagementController(
    private val rcaAiService: RcaAiService
) {

    /**
     * Get RCA generation statistics (for managers and monitoring)
     * GET /api/rca/statistics
     */
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('MANAGER')")
    fun getRcaStatistics(): ResponseEntity<RcaStatistics> {
        val stats = rcaAiService.getRcaStatistics()
        return ResponseEntity.ok(stats)
    }

    /**
     * Get RCA service health status
     * GET /api/rca/health
     */
    @GetMapping("/health")
    @PreAuthorize("hasRole('MANAGER')")
    fun getRcaServiceHealth(): ResponseEntity<RcaServiceHealth> {
        val health = rcaAiService.healthCheck()
        return ResponseEntity.ok(health)
    }

    /**
     * Get all incidents needing RCA review
     * GET /api/rca/pending-review
     */
    @GetMapping("/pending-review")
    @PreAuthorize("hasRole('MANAGER')")
    fun getIncidentsNeedingReview(): ResponseEntity<List<RcaAiSuggestionResponseDTO>> {
        val pendingReviews = rcaAiService.getIncidentsNeedingReview()
        val response = pendingReviews.map { RcaAiSuggestionResponseDTO.fromEntity(it) }
        return ResponseEntity.ok(response)
    }
}

/**
 * DTO for RCA AI suggestions response
 */
data class RcaAiSuggestionResponseDTO(
    val id: UUID,
    val incidentId: UUID,
    val incidentTitle: String,
    val suggestedFiveWhys: String,
    val suggestedCorrectiveAction: String,
    val suggestedPreventiveAction: String,
    val confidenceScore: Double,
    val incidentCategory: String,
    val templateUsed: String,
    val openaiModel: String,
    val tokensUsed: Int?,
    val processingTimeMs: Long?,
    val status: String,
    val generatedAt: String,
    val reviewedAt: String?,
    val reviewedByName: String?,
    val errorMessage: String?
) {
    companion object {
        fun fromEntity(suggestion: RcaAiSuggestion): RcaAiSuggestionResponseDTO {
            return RcaAiSuggestionResponseDTO(
                id = suggestion.id!!,
                incidentId = suggestion.incident.id!!,
                incidentTitle = suggestion.incident.title,
                suggestedFiveWhys = suggestion.suggestedFiveWhys,
                suggestedCorrectiveAction = suggestion.suggestedCorrectiveAction,
                suggestedPreventiveAction = suggestion.suggestedPreventiveAction,
                confidenceScore = suggestion.confidenceScore,
                incidentCategory = suggestion.incidentCategory.name,
                templateUsed = suggestion.templateUsed,
                openaiModel = suggestion.openaiModel,
                tokensUsed = suggestion.tokensUsed,
                processingTimeMs = suggestion.processingTimeMs,
                status = suggestion.status.name,
                generatedAt = suggestion.generatedAt.toString(),
                reviewedAt = suggestion.reviewedAt?.toString(),
                reviewedByName = suggestion.reviewedBy?.fullName,
                errorMessage = suggestion.errorMessage
            )
        }
    }
}
