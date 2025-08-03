package com.safesnap.backend.service

import com.safesnap.backend.dto.incident.IncidentCreateDTO
import com.safesnap.backend.dto.incident.IncidentResponseDTO
import com.safesnap.backend.dto.incident.RcaResponseDTO
import com.safesnap.backend.dto.incident.AiSuggestionDTO
import com.safesnap.backend.dto.rca.RcaAiSuggestionDTO
import com.safesnap.backend.dto.user.UserResponseDTO
import com.safesnap.backend.entity.*
import com.safesnap.backend.exception.IncidentNotFoundException
import com.safesnap.backend.exception.UnauthorizedAccessException
import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.repository.IncidentRepository
import com.safesnap.backend.repository.UserRepository
import com.safesnap.backend.repository.ImageAnalysisRepository
import com.safesnap.backend.repository.AiSuggestionRepository
import com.safesnap.backend.repository.RcaAiSuggestionRepository

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.transaction.support.TransactionSynchronization
import org.slf4j.LoggerFactory
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import java.time.LocalDateTime
import java.util.*


@Service
@Transactional
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val userRepository: UserRepository,
    private val imageAnalysisRepository: ImageAnalysisRepository,
    private val aiSuggestionRepository: AiSuggestionRepository,
    private val rcaAiSuggestionRepository: RcaAiSuggestionRepository,
    private val imageProcessingService: ImageProcessingService,
    private val rcaAiService: RcaAiService,
    private val metricsService: MetricsService
) {
    private val logger = LoggerFactory.getLogger(IncidentService::class.java)
    fun createIncident(request: IncidentCreateDTO, userEmail: String): IncidentResponseDTO {
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        val incident = Incident(
            title = request.title,
            description = request.description,
            severity = IncidentSeverity.valueOf(request.severity),
            status = IncidentStatus.OPEN,
            latitude = request.latitude,
            longitude = request.longitude,
            locationDescription = request.locationDescription,
            imageUrls = request.imageUrls ?: emptyList(),
            audioUrls = request.audioUrls ?: emptyList(),
            reportedBy = user,
            reportedAt = LocalDateTime.now()
        )

        val savedIncident = incidentRepository.save(incident)
        logger.debug("Incident saved with ID: ${savedIncident.id}")
        logger.debug("Image URLs: ${savedIncident.imageUrls}")

        // Record metric
        metricsService.recordIncidentCreated()

        // ✅ Delay async image analysis until AFTER transaction commits
        if (savedIncident.imageUrls.isNotEmpty() && savedIncident.id != null) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    logger.debug("Starting async image processing for incident ${savedIncident.id} with ${savedIncident.imageUrls.size} images")
                    imageProcessingService.processIncidentImages(savedIncident.id!!, savedIncident.imageUrls)
                    logger.debug("Async image processing call completed")
                }
            })
        } else {
            logger.debug("No images to process")
        }

        // ✅ Delay RCA generation until AFTER commit too
        if (savedIncident.id != null) {
            TransactionSynchronizationManager.registerSynchronization(object : TransactionSynchronization {
                override fun afterCommit() {
                    try {
                        logger.debug("Triggering RCA generation for incident ${savedIncident.id}")
                        rcaAiService.generateRcaSuggestionsAsync(savedIncident.id!!)
                        logger.debug("RCA generation triggered successfully")
                    } catch (e: Exception) {
                        logger.warn("Failed to trigger RCA generation for incident ${savedIncident.id}", e)
                    }
                }
            })
        }

        return savedIncident.toResponseDTO()
    }

    fun getIncidentsForUser(
        userEmail: String,
        pageable: Pageable,
        status: String? = null,
        severity: String? = null,
        search: String? = null
    ): Page<IncidentResponseDTO> {
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        // Users can only see their own incidents unless they're managers
        return if (user.role.name == "MANAGER") {
            getAllIncidents(pageable, status, severity, search, null)
        } else {
            val statusEnum = status?.let { IncidentStatus.valueOf(it.uppercase()) }
            val severityEnum = severity?.let { IncidentSeverity.valueOf(it.uppercase()) }
            
            incidentRepository.findIncidentsForUserWithRca(
                userId = user.id,
                status = statusEnum,
                severity = severityEnum,
                search = search,
                pageable = pageable
            ).map { it.toResponseDTO() }
        }
    }

    fun getAllIncidents(
        pageable: Pageable,
        status: String? = null,
        severity: String? = null,
        search: String? = null,
        assignedTo: String? = null
    ): Page<IncidentResponseDTO> {
        val statusEnum = status?.let { IncidentStatus.valueOf(it.uppercase()) }
        val severityEnum = severity?.let { IncidentSeverity.valueOf(it.uppercase()) }
        
        return incidentRepository.findAllIncidentsWithFiltersAndRca(
            status = statusEnum,
            severity = severityEnum,
            search = search,
            assignedTo = assignedTo,
            pageable = pageable
        ).map { it.toResponseDTO() }
    }

    fun getIncidentById(id: UUID, userEmail: String): IncidentResponseDTO {
        val incident = incidentRepository.findByIdWithRca(id).orElse(null)
            ?: throw IncidentNotFoundException(id)
        
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        // Check authorization - users can only view their own incidents unless they're managers
        if (user.role.name != "MANAGER" && incident.reportedBy.id != user.id) {
            throw UnauthorizedAccessException("You can only view your own incidents")
        }

        return incident.toResponseDTO()
    }

    fun updateIncident(id: UUID, request: IncidentCreateDTO, userEmail: String): IncidentResponseDTO {
        val incident = incidentRepository.findByIdWithRca(id).orElse(null)
            ?: throw IncidentNotFoundException(id)
        
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        // Check authorization - users can only update their own incidents unless they're managers
        if (user.role.name != "MANAGER" && incident.reportedBy.id != user.id) {
            throw UnauthorizedAccessException("You can only update your own incidents")
        }

        // Update incident fields
        incident.title = request.title
        incident.description = request.description
        incident.severity = IncidentSeverity.valueOf(request.severity)
        incident.latitude = request.latitude
        incident.longitude = request.longitude
        incident.locationDescription = request.locationDescription
        incident.updatedAt = LocalDateTime.now()

        // Handle new images/audio if provided
        if (!request.imageUrls.isNullOrEmpty()) {
            incident.imageUrls = request.imageUrls
            // Trigger image analysis for new images
            incident.id?.let { incidentId ->
                imageProcessingService.processIncidentImages(incidentId, request.imageUrls)
            }
        }
        
        if (!request.audioUrls.isNullOrEmpty()) {
            incident.audioUrls = request.audioUrls
        }

        val savedIncident = incidentRepository.save(incident)
        return savedIncident.toResponseDTO()
    }

    fun deleteIncident(id: UUID, userEmail: String) {
        val incident = incidentRepository.findById(id).orElse(null)
            ?: throw IncidentNotFoundException(id)
        
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        // Only managers can delete incidents
        if (user.role.name != "MANAGER") {
            throw UnauthorizedAccessException("Only managers can delete incidents")
        }

        incidentRepository.delete(incident)
    }

    fun updateIncidentStatus(id: UUID, status: String, userEmail: String): IncidentResponseDTO {
        val incident = incidentRepository.findByIdWithRca(id).orElse(null)
            ?: throw IncidentNotFoundException(id)
        
        val user = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        // Only managers can update incident status
        if (user.role.name != "MANAGER") {
            throw UnauthorizedAccessException("Only managers can update incident status")
        }

        incident.status = IncidentStatus.valueOf(status.uppercase())
        incident.updatedAt = LocalDateTime.now()
        incident.updatedBy = user

        val savedIncident = incidentRepository.save(incident)
        return savedIncident.toResponseDTO()
    }

    fun assignIncident(id: UUID, assigneeEmail: String, userEmail: String): IncidentResponseDTO {
        val incident = incidentRepository.findByIdWithRca(id).orElse(null)
            ?: throw IncidentNotFoundException(id)
        
        val manager = userRepository.findByEmail(userEmail)
            ?: throw UserNotFoundException(userEmail)

        val assignee = userRepository.findByEmail(assigneeEmail)
            ?: throw UserNotFoundException(assigneeEmail)

        // Only managers can assign incidents
        if (manager.role.name != "MANAGER") {
            throw UnauthorizedAccessException("Only managers can assign incidents")
        }

        incident.assignedTo = assignee
        incident.updatedAt = LocalDateTime.now()
        incident.updatedBy = manager

        val savedIncident = incidentRepository.save(incident)
        return savedIncident.toResponseDTO()
    }
}

// Extension function to convert entity to DTO with all related data
private fun Incident.toResponseDTO(): IncidentResponseDTO {
    return IncidentResponseDTO(
        id = this.id!!, // Safe to use !! since we only convert saved entities
        title = this.title,
        description = this.description,
        severity = this.severity.name,
        status = this.status.name,
        latitude = this.latitude,
        longitude = this.longitude,
        locationDescription = this.locationDescription,
        imageUrls = this.imageUrls,
        audioUrls = this.audioUrls,
        reportedBy = this.reportedBy.fullName,
        reportedByEmail = this.reportedBy.email,
        assignedTo = this.assignedTo?.fullName,
        assignedToEmail = this.assignedTo?.email,
        reportedAt = this.reportedAt,
        updatedAt = this.updatedAt,
        updatedBy = this.updatedBy?.fullName,
        
        // Populate the missing fields from entity relationships
        rcaReport = this.rcaReport?.let { rca ->
            RcaResponseDTO(
                id = rca.id,
                fiveWhys = rca.fiveWhys,
                correctiveAction = rca.correctiveAction,
                preventiveAction = rca.preventiveAction,
                createdAt = rca.createdAt,
                manager = UserResponseDTO(
                    id = rca.manager.id,
                    name = rca.manager.fullName,
                    email = rca.manager.email,
                    role = rca.manager.role
                )
            )
        },
        
        // ✅ ONLY show AI suggestions if manager has approved them + clean payload
        rcaAiSuggestions = this.rcaAiSuggestion?.let { aiRca ->
            // Only show if status is APPROVED 
            if (aiRca.status == RcaAiStatus.APPROVED) {
                RcaAiSuggestionDTO(
                    suggestedFiveWhys = aiRca.suggestedFiveWhys,
                    suggestedCorrectiveAction = aiRca.suggestedCorrectiveAction,
                    suggestedPreventiveAction = aiRca.suggestedPreventiveAction,
                    reviewedByManager = aiRca.reviewedBy?.fullName ?: "Unknown Manager"
                )
            } else null
        },
        
        aiSuggestions = this.aiSuggestions.map { suggestion ->
            AiSuggestionDTO(
                summary = suggestion.summary,
                keywords = try {
                    jacksonObjectMapper().readValue<List<String>>(suggestion.keywords)
                } catch (e: Exception) {
                    emptyList()
                }
            )
        },
        imageTags = this.imageAnalyses.flatMap { analysis ->
            analysis.tags.split(",").map { it.trim() }.filter { it.isNotBlank() }
        },
        transcriptions = emptyList() // Audio processing on hold for now
    )
}
