package com.safesnap.backend.service

import com.safesnap.backend.entity.*
import com.safesnap.backend.dto.incident.RcaCreateDTO
import com.safesnap.backend.exception.IncidentNotFoundException
import com.safesnap.backend.exception.OpenAiServiceException
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
    private val openAiService: OpenAiService,
    private val incidentCategorizationService: IncidentCategorizationService,
    private val rcaTemplateService: RcaTemplateService,
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
                logger.info("Starting async RCA generation for incident: $incidentId")
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

                val category = incidentCategorizationService.categorizeIncident(incident, imageAnalyses)
                val confidence = incidentCategorizationService.getCategorizationConfidence(incident, category, imageAnalyses)

                logger.info("Incident $incidentId categorized as: $category (confidence: $confidence)")

                // 🛠️ Fix: Fetch user data separately to avoid lazy loading issues
                val user = userRepository.findById(incident.reportedBy.id).orElseThrow {
                    UserNotFoundException("User not found with ID: ${incident.reportedBy.id}")
                }
                val reporterFullName = user.fullName
                val reporterRole = user.role.name

                val template = rcaTemplateService.getTemplate(
                    category = category,
                    incident = incident,
                    imageAnalyses = imageAnalyses,
                    reporterName = reporterFullName,
                    reporterRole = reporterRole
                )

                val openAiResponse = openAiService.generateRcaAnalysis(
                    prompt = template,
                    incidentContext = buildIncidentContext(incident, imageAnalyses),
                    userEmail = user.email
                )

                if (!openAiResponse.success) {
                    throw OpenAiServiceException("OpenAI generation failed: ${openAiResponse.errorMessage}")
                }

                val parsedRca = parseRcaResponse(openAiResponse.content)

                val processingTime = System.currentTimeMillis() - startTime

                val rcaSuggestion = RcaAiSuggestion(
                    incident = incident,
                    suggestedFiveWhys = parsedRca.fiveWhys,
                    suggestedCorrectiveAction = parsedRca.correctiveAction,
                    suggestedPreventiveAction = parsedRca.preventiveAction,
                    confidenceScore = confidence,
                    incidentCategory = category,
                    templateUsed = category.name,
                    openaiModel = openAiResponse.model,
                    tokensUsed = openAiResponse.tokensUsed,
                    processingTimeMs = processingTime,
                    status = RcaAiStatus.GENERATED
                )

                existing?.let { rcaAiSuggestionRepository.delete(it) }

                val saved = rcaAiSuggestionRepository.save(rcaSuggestion)

                logger.info("RCA suggestions generated successfully for incident: $incidentId in ${processingTime}ms")
                metricsService.recordRcaGenerated(category.name)

                saved

            } catch (e: OpenAiServiceException) {
                logger.error("OpenAI service error for incident: $incidentId", e)
                val failedSuggestion = createFailedSuggestion(incidentId, "OpenAI service error: ${e.message}")
                metricsService.recordRcaFailed("openai_error")
                failedSuggestion
            } catch (e: Exception) {
                logger.error("Unexpected error generating RCA for incident: $incidentId", e)
                val failedSuggestion = createFailedSuggestion(incidentId, "Generation failed: ${e.message}")
                metricsService.recordRcaFailed("unexpected_error")
                failedSuggestion
            }
        }
    }

    private fun createFailedSuggestion(incidentId: UUID, errorMessage: String): RcaAiSuggestion {
        val incident = incidentRepository.findById(incidentId).orElseThrow {
            IncidentNotFoundException(incidentId)
        }

        val failedSuggestion = RcaAiSuggestion(
            incident = incident,
            suggestedFiveWhys = "RCA generation failed",
            suggestedCorrectiveAction = "Please manually complete RCA analysis",
            suggestedPreventiveAction = "Please manually complete RCA analysis",
            confidenceScore = 0.0,
            incidentCategory = IncidentCategory.GENERAL_SAFETY,
            templateUsed = "ERROR",
            status = RcaAiStatus.FAILED,
            errorMessage = errorMessage
        )

        return rcaAiSuggestionRepository.save(failedSuggestion)
    }

    private fun buildIncidentContext(incident: Incident, imageAnalyses: List<ImageAnalysis>): Map<String, Any> {
        // 🛠️ Fix: Use user data that was already fetched to avoid lazy loading
        val user = userRepository.findById(incident.reportedBy.id).orElseThrow {
            UserNotFoundException("User not found with ID: ${incident.reportedBy.id}")
        }
        
        return mapOf(
            "incident_id" to incident.id.toString(),
            "title" to incident.title,
            "description" to incident.description,
            "severity" to incident.severity.name,
            "location" to (incident.locationDescription ?: "Not specified"),
            "reported_by" to user.fullName,
            "reported_at" to incident.reportedAt.toString(),
            "image_analyses" to imageAnalyses.map {
                mapOf(
                    "tags" to it.tags,
                    "confidence" to it.confidenceScore,
                    "processed" to it.processed
                )
            }
        )
    }

    private fun parseRcaResponse(content: String): ParsedRcaResponse {
        return try {
            val lines = content.lines().map { it.trim() }
            val fiveWhysStart = lines.indexOfFirst { it.uppercase().contains("FIVE WHYS") }
            val correctiveStart = lines.indexOfFirst { it.uppercase().contains("CORRECTIVE ACTIONS") }
            val preventiveStart = lines.indexOfFirst { it.uppercase().contains("PREVENTIVE ACTIONS") }

            if (fiveWhysStart == -1 || correctiveStart == -1 || preventiveStart == -1) {
                logger.warn("Could not find all RCA sections in OpenAI response, using raw content")
                return ParsedRcaResponse(
                    fiveWhys = content,
                    correctiveAction = "Please review and format the AI-generated response",
                    preventiveAction = "Please review and format the AI-generated response"
                )
            }

            val fiveWhys = lines.subList(fiveWhysStart + 1, correctiveStart)
                .filter { it.isNotBlank() }.joinToString("\n")

            val correctiveAction = lines.subList(correctiveStart + 1, preventiveStart)
                .filter { it.isNotBlank() }.joinToString("\n")

            val preventiveAction = lines.subList(preventiveStart + 1, lines.size)
                .filter { it.isNotBlank() }.joinToString("\n")

            ParsedRcaResponse(fiveWhys, correctiveAction, preventiveAction)
        } catch (e: Exception) {
            logger.error("Failed to parse OpenAI RCA response", e)
            ParsedRcaResponse(
                fiveWhys = content,
                correctiveAction = "Please review and format the AI-generated response",
                preventiveAction = "Please review and format the AI-generated response"
            )
        }
    }

    /**
     * Mark RCA suggestions as reviewed by a manager
     */
    fun markAsReviewed(incidentId: UUID, managerEmail: String): RcaAiSuggestion {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        
        if (manager.role != Role.MANAGER) {
            throw RcaGenerationException("User is not a manager: $managerEmail")
        }
        
        suggestion.status = RcaAiStatus.REVIEWED
        suggestion.reviewedAt = LocalDateTime.now()
        suggestion.reviewedBy = manager
        
        logger.info("RCA suggestions marked as reviewed for incident: $incidentId by manager: $managerEmail")
        return rcaAiSuggestionRepository.save(suggestion)
    }

    /**
     * Mark RCA suggestions as approved by a manager (makes them visible to workers)
     */
    fun markAsApproved(incidentId: UUID, managerEmail: String): RcaAiSuggestion {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        
        if (manager.role != Role.MANAGER) {
            throw RcaGenerationException("User is not a manager: $managerEmail")
        }
        
        suggestion.status = RcaAiStatus.APPROVED
        suggestion.reviewedAt = LocalDateTime.now()
        suggestion.reviewedBy = manager
        
        logger.info("RCA suggestions marked as approved for incident: $incidentId by manager: $managerEmail")
        return rcaAiSuggestionRepository.save(suggestion)
    }
    
    /**
     * Create final RCA report from approved AI suggestions
     */
    fun createFinalRcaFromSuggestions(
        incidentId: UUID,
        rcaCreateDTO: RcaCreateDTO,
        managerEmail: String
    ): RcaReport {
        val suggestion = rcaAiSuggestionRepository.findByIncidentId(incidentId)
            ?: throw RcaGenerationException("No RCA suggestions found for incident: $incidentId")
        
        val manager = userRepository.findByEmail(managerEmail)
            ?: throw RcaGenerationException("Manager not found: $managerEmail")
        
        if (manager.role != Role.MANAGER) {
            throw RcaGenerationException("User is not a manager: $managerEmail")
        }
        
        // Check if final RCA already exists
        val existingRca = suggestion.incident.rcaReport
        if (existingRca != null) {
            throw RcaGenerationException("Final RCA report already exists for incident: $incidentId")
        }
        
        // Create final RCA report
        val rcaReport = RcaReport(
            incident = suggestion.incident,
            manager = manager,
            fiveWhys = rcaCreateDTO.fiveWhys,
            correctiveAction = rcaCreateDTO.correctiveAction,
            preventiveAction = rcaCreateDTO.preventiveAction
        )
        
        // Save the RCA report to the database
        val savedRcaReport = rcaReportRepository.save(rcaReport)
        
        // Update suggestion status
        suggestion.status = if (isContentModified(suggestion, rcaCreateDTO)) {
            RcaAiStatus.MODIFIED
        } else {
            RcaAiStatus.APPROVED
        }
        
        rcaAiSuggestionRepository.save(suggestion)
        
        logger.info("Final RCA report created and saved for incident: $incidentId by manager: $managerEmail")
        metricsService.recordRcaApproved(suggestion.incidentCategory.name)
        
        return savedRcaReport
    }
    
    /**
     * Check if manager modified the AI suggestions
     */
    private fun isContentModified(suggestion: RcaAiSuggestion, finalRca: RcaCreateDTO): Boolean {
        val fiveWhysSimilarity = calculateSimilarity(suggestion.suggestedFiveWhys, finalRca.fiveWhys)
        val correctiveSimilarity = calculateSimilarity(suggestion.suggestedCorrectiveAction, finalRca.correctiveAction)
        val preventiveSimilarity = calculateSimilarity(suggestion.suggestedPreventiveAction, finalRca.preventiveAction)
        
        val averageSimilarity = (fiveWhysSimilarity + correctiveSimilarity + preventiveSimilarity) / 3
        
        return averageSimilarity < SafeSnapConstants.SIMILARITY_THRESHOLD
    }
    
    /**
     * Simple text similarity calculation (Jaccard similarity)
     */
    private fun calculateSimilarity(text1: String, text2: String): Double {
        val words1 = text1.lowercase().split("\\s+".toRegex()).toSet()
        val words2 = text2.lowercase().split("\\s+".toRegex()).toSet()
        
        val intersection = words1.intersect(words2).size
        val union = words1.union(words2).size
        
        return if (union == 0) 1.0 else intersection.toDouble() / union
    }
    
    /**
     * Get RCA suggestions for an incident
     */
    fun getRcaSuggestions(incidentId: UUID): RcaAiSuggestion? {
        return rcaAiSuggestionRepository.findByIncidentId(incidentId)
    }
    
    /**
     * Retry failed RCA generation
     */
    fun retryFailedGeneration(incidentId: UUID): RcaAiSuggestion {
        logger.info("Retrying RCA generation for incident: $incidentId")
        return generateRcaSuggestions(incidentId, forceRegenerate = true)
    }
    
    /**
     * Get RCA generation statistics
     */
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
    
    /**
     * Get incidents that need RCA review by managers
     */
    fun getIncidentsNeedingReview(): List<RcaAiSuggestion> {
        return rcaAiSuggestionRepository.findPendingReview()
    }
    
    /**
     * Health check for RCA AI service
     */
    fun healthCheck(): RcaServiceHealth {
        val openAiHealthy = openAiService.healthCheck()
        val recentFailures = rcaAiSuggestionRepository.findFailedSince(LocalDateTime.now().minusHours(24))
        
        return RcaServiceHealth(
            openAiServiceHealthy = openAiHealthy,
            recentFailureCount = recentFailures.size,
            pendingReviewCount = rcaAiSuggestionRepository.findPendingReview().size,
            healthy = openAiHealthy && recentFailures.size < 10 // Less than 10 failures in 24h
        )
    }
}

/**
 * Parsed RCA response from OpenAI
 */
data class ParsedRcaResponse(
    val fiveWhys: String,
    val correctiveAction: String,
    val preventiveAction: String
)

/**
 * RCA generation statistics
 */
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

/**
 * RCA service health status
 */
data class RcaServiceHealth(
    val openAiServiceHealthy: Boolean,
    val recentFailureCount: Int,
    val pendingReviewCount: Int,
    val healthy: Boolean
)
