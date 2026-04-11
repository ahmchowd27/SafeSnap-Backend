package com.safesnap.backend.service

import com.safesnap.backend.entity.*
import com.safesnap.backend.dto.incident.RcaCreateDTO
import com.safesnap.backend.exception.IncidentNotFoundException
import com.safesnap.backend.exception.RcaGenerationException
import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.repository.*
import org.slf4j.LoggerFactory
import org.springframework.scheduling.annotation.Async
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import com.safesnap.backend.config.SafeSnapConstants
import java.time.LocalDateTime
import java.util.*
import java.util.concurrent.CompletableFuture

@Service
@Transactional
class RcaAiService(
    private val claudeService: ClaudeService,
    private val rcaAiSuggestionRepository: RcaAiSuggestionRepository,
    private val rcaReportRepository: RcaReportRepository,
    private val incidentRepository: IncidentRepository,
    private val imageAnalysisRepository: ImageAnalysisRepository,
    private val userRepository: UserRepository,
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(RcaAiService::class.java)

    @Async
    fun generateRcaSuggestionsAsync(incidentId: UUID): CompletableFuture<RcaAiSuggestion?> {
        return CompletableFuture.supplyAsync {
            try {
                generateRcaSuggestions(incidentId)
            } catch (e: Exception) {
                logger.error("Async RCA generation failed for incident: $incidentId", e)
                null
            }
        }
    }

    fun generateRcaSuggestions(incidentId: UUID, forceRegenerate: Boolean = false): RcaAiSuggestion {
        logger.info("Generating RCA suggestions for incident: $incidentId")

        val existing = rcaAiSuggestionRepository.findByIncidentId(incidentId)
        if (existing != null && !forceRegenerate) {
            logger.info("RCA suggestions already exist for incident: $incidentId")
            return existing
        }

        return metricsService.timeRcaGeneration {
            try {
                val startTime = System.currentTimeMillis()

                val incident = incidentRepository.findById(incidentId).orElseThrow {
                    IncidentNotFoundException(incidentId)
                }

                val imageAnalyses = imageAnalysisRepository.findByIncidentIdOrderByProcessedAtDesc(incidentId)

                val user = userRepository.findById(incident.reportedBy.id).orElseThrow {
                    UserNotFoundException("User not found with ID: ${incident.reportedBy.id}")
                }

                // Build context from image analyses
                val imageAnalysisSummaries = imageAnalyses
                    .filter { it.processed }
                    .map { it.allLabels ?: it.tags }

                val safetyTags = imageAnalyses
                    .filter { it.processed }
                    .flatMap { it.tags.split(",").map { t -> t.trim() }.filter { t -> t.isNotBlank() } }

                // OSHA violations stored in allLabels field from Claude image analysis
                val oshaViolations = imageAnalyses
                    .filter { it.processed && it.allLabels?.contains("29 CFR") == true }
                    .mapNotNull { it.allLabels }

                // Derive category from existing image analysis tags, fallback to GENERAL_SAFETY
                val category = deriveCategory(incident, safetyTags)

                val result = claudeService.generateRca(
                    incidentTitle = incident.title,
                    incidentDescription = incident.description,
                    severity = incident.severity.name,
                    locationDescription = incident.locationDescription,
                    reporterName = user.fullName,
                    reporterRole = user.role.name,
                    imageAnalysisSummaries = imageAnalysisSummaries,
                    safetyTags = safetyTags,
                    oshaViolationsFromImages = oshaViolations,
                    incidentCategory = category,
                    userEmail = user.email
                )

                if (!result.success) {
                    throw RcaGenerationException("Claude RCA generation failed: ${result.errorMessage}")
                }

                val processingTime = System.currentTimeMillis() - startTime

                // Format structured lists into strings for storage (backward compat with existing schema)
                val rcaSuggestion = RcaAiSuggestion(
                    incident = incident,
                    suggestedFiveWhys = result.fiveWhys.joinToString("\n"),
                    suggestedCorrectiveAction = result.immediateCorrectiveActions.joinToString("\n"),
                    suggestedPreventiveAction = result.longTermPreventiveActions.joinToString("\n"),
                    confidenceScore = result.confidenceScore,
                    incidentCategory = result.incidentCategory,
                    templateUsed = result.oshaReferences.joinToString("; ").take(255),
                    openaiModel = "claude-sonnet-4-6",
                    tokensUsed = result.tokensUsed,
                    processingTimeMs = processingTime,
                    status = RcaAiStatus.GENERATED
                )

                existing?.let { rcaAiSuggestionRepository.delete(it) }
                val saved = rcaAiSuggestionRepository.save(rcaSuggestion)

                logger.info("RCA suggestions generated for incident: $incidentId in ${processingTime}ms (tokens: ${result.tokensUsed})")
                metricsService.recordRcaGenerated(result.incidentCategory.name)

                saved

            } catch (e: RcaGenerationException) {
                logger.error("RCA generation error for incident: $incidentId", e)
                val failed = createFailedSuggestion(incidentId, e.message ?: "Generation failed")
                metricsService.recordRcaFailed("rca_generation_error")
                failed
            } catch (e: Exception) {
                logger.error("Unexpected error generating RCA for incident: $incidentId", e)
                val failed = createFailedSuggestion(incidentId, "Unexpected error: ${e.message}")
                metricsService.recordRcaFailed("unexpected_error")
                failed
            }
        }
    }

    private fun createFailedSuggestion(incidentId: UUID, errorMessage: String): RcaAiSuggestion {
        val incident = incidentRepository.findById(incidentId).orElseThrow {
            IncidentNotFoundException(incidentId)
        }
        return rcaAiSuggestionRepository.save(RcaAiSuggestion(
            incident = incident,
            suggestedFiveWhys = "RCA generation failed — please complete manually",
            suggestedCorrectiveAction = "Please complete corrective actions manually",
            suggestedPreventiveAction = "Please complete preventive actions manually",
            confidenceScore = 0.0,
            incidentCategory = IncidentCategory.GENERAL_SAFETY,
            templateUsed = "ERROR",
            status = RcaAiStatus.FAILED,
            errorMessage = errorMessage
        ))
    }

    fun markAsReviewed(incidentId: UUID, managerEmail: String): RcaAiSuggestion {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        if (manager.role != Role.MANAGER) throw RcaGenerationException("User is not a manager: $managerEmail")

        suggestion.status = RcaAiStatus.REVIEWED
        suggestion.reviewedAt = LocalDateTime.now()
        suggestion.reviewedBy = manager
        return rcaAiSuggestionRepository.save(suggestion)
    }

    fun markAsApproved(incidentId: UUID, managerEmail: String): RcaAiSuggestion {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        if (manager.role != Role.MANAGER) throw RcaGenerationException("User is not a manager: $managerEmail")

        suggestion.status = RcaAiStatus.APPROVED
        suggestion.reviewedAt = LocalDateTime.now()
        suggestion.reviewedBy = manager
        return rcaAiSuggestionRepository.save(suggestion)
    }

    fun createFinalRcaFromSuggestions(incidentId: UUID, rcaCreateDTO: RcaCreateDTO, managerEmail: String): RcaReport {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        if (manager.role != Role.MANAGER) throw RcaGenerationException("User is not a manager: $managerEmail")
        if (suggestion.incident.rcaReport != null) throw RcaGenerationException("Final RCA already exists for incident: $incidentId")

        val rcaReport = rcaReportRepository.save(RcaReport(
            incident = suggestion.incident,
            manager = manager,
            fiveWhys = rcaCreateDTO.fiveWhys,
            correctiveAction = rcaCreateDTO.correctiveAction,
            preventiveAction = rcaCreateDTO.preventiveAction
        ))

        suggestion.status = if (isContentModified(suggestion, rcaCreateDTO)) RcaAiStatus.MODIFIED else RcaAiStatus.APPROVED
        rcaAiSuggestionRepository.save(suggestion)

        metricsService.recordRcaApproved(suggestion.incidentCategory.name)
        logger.info("Final RCA created for incident: $incidentId by manager: $managerEmail")

        return rcaReport
    }

    fun getRcaSuggestions(incidentId: UUID): RcaAiSuggestion? =
        rcaAiSuggestionRepository.findByIncidentId(incidentId)

    fun retryFailedGeneration(incidentId: UUID): RcaAiSuggestion =
        generateRcaSuggestions(incidentId, forceRegenerate = true)

    fun getRcaStatistics(): RcaStatistics {
        val total = rcaAiSuggestionRepository.count()
        val generated = rcaAiSuggestionRepository.countByStatus(RcaAiStatus.GENERATED)
        val reviewed = rcaAiSuggestionRepository.countByStatus(RcaAiStatus.REVIEWED)
        val approved = rcaAiSuggestionRepository.countByStatus(RcaAiStatus.APPROVED)
        val modified = rcaAiSuggestionRepository.countByStatus(RcaAiStatus.MODIFIED)
        val failed = rcaAiSuggestionRepository.countByStatus(RcaAiStatus.FAILED)
        val avgProcessingTime = rcaAiSuggestionRepository.getAverageProcessingTime() ?: 0.0
        val avgTokenUsage = rcaAiSuggestionRepository.getAverageTokenUsage(LocalDateTime.now().minusDays(30)) ?: 0.0

        return RcaStatistics(
            totalSuggestions = total,
            generatedCount = generated,
            reviewedCount = reviewed,
            approvedCount = approved,
            modifiedCount = modified,
            failedCount = failed,
            successRate = if (total > 0) ((generated + reviewed + approved + modified).toDouble() / total) * SafeSnapConstants.PERCENTAGE_MULTIPLIER else 0.0,
            averageProcessingTimeMs = avgProcessingTime,
            averageTokenUsage = avgTokenUsage
        )
    }

    fun getIncidentsNeedingReview(): List<RcaAiSuggestion> =
        rcaAiSuggestionRepository.findPendingReview()

    fun healthCheck(): RcaServiceHealth {
        val recentFailures = rcaAiSuggestionRepository.findFailedSince(LocalDateTime.now().minusHours(24))
        return RcaServiceHealth(
            claudeServiceHealthy = claudeService.healthCheck(),
            recentFailureCount = recentFailures.size,
            pendingReviewCount = rcaAiSuggestionRepository.findPendingReview().size,
            healthy = claudeService.healthCheck() && recentFailures.size < 10
        )
    }

    private fun isContentModified(suggestion: RcaAiSuggestion, finalRca: RcaCreateDTO): Boolean {
        val fiveWhysSim = calculateSimilarity(suggestion.suggestedFiveWhys, finalRca.fiveWhys)
        val correctiveSim = calculateSimilarity(suggestion.suggestedCorrectiveAction, finalRca.correctiveAction)
        val preventiveSim = calculateSimilarity(suggestion.suggestedPreventiveAction, finalRca.preventiveAction)
        return ((fiveWhysSim + correctiveSim + preventiveSim) / 3) < SafeSnapConstants.SIMILARITY_THRESHOLD
    }

    private fun calculateSimilarity(text1: String, text2: String): Double {
        val words1 = text1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = text2.lowercase().split("\\s+".toRegex()).toSet()
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        return if (union == 0) 1.0 else intersection.toDouble() / union
    }

    /**
     * Derive incident category from image analysis tags when Claude image analysis
     * ran before RCA generation — avoids a second Claude call for categorization.
     */
    private fun deriveCategory(incident: Incident, safetyTags: List<String>): IncidentCategory {
        val text = ("${incident.title} ${incident.description} ${safetyTags.joinToString(" ")}").lowercase()
        return when {
            text.containsAny("ppe", "hard hat", "helmet", "vest", "glove", "harness", "protective") -> IncidentCategory.PPE_VIOLATION
            text.containsAny("equipment", "machine", "malfunction", "broken", "defective", "failure") -> IncidentCategory.EQUIPMENT_MALFUNCTION
            text.containsAny("slip", "trip", "fall", "wet floor", "slippery", "stumble") -> IncidentCategory.SLIP_TRIP_FALL
            text.containsAny("lift", "strain", "back", "ergonomic", "heavy", "manual handling") -> IncidentCategory.LIFTING_INJURY
            text.containsAny("chemical", "toxic", "spill", "exposure", "fume", "hazmat") -> IncidentCategory.CHEMICAL_EXPOSURE
            text.containsAny("electric", "shock", "arc flash", "wiring", "voltage") -> IncidentCategory.ELECTRICAL_INCIDENT
            text.containsAny("vehicle", "forklift", "truck", "collision", "pedestrian") -> IncidentCategory.VEHICLE_INCIDENT
            text.containsAny("fire", "explosion", "ignition", "flammable", "combustion") -> IncidentCategory.FIRE_EXPLOSION
            text.containsAny("confined space", "permit required", "atmospheric") -> IncidentCategory.CONFINED_SPACE
            else -> IncidentCategory.GENERAL_SAFETY
        }
    }

    private fun String.containsAny(vararg terms: String): Boolean = terms.any { this.contains(it) }
}

data class RcaStatistics(
    val totalSuggestions: Long,
    val generatedCount: Long,
    val reviewedCount: Long,
    val approvedCount: Long,
    val modifiedCount: Long,
    val failedCount: Long,
    val successRate: Double,
    val averageProcessingTimeMs: Double,
    val averageTokenUsage: Double
)

data class RcaServiceHealth(
    val claudeServiceHealthy: Boolean,
    val recentFailureCount: Int,
    val pendingReviewCount: Int,
    val healthy: Boolean
)
