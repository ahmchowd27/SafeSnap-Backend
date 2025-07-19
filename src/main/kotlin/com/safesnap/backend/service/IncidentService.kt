package com.safesnap.backend.service

import com.safesnap.backend.dto.incident.IncidentCreateDTO
import com.safesnap.backend.dto.incident.IncidentResponseDTO
import com.safesnap.backend.entity.Incident
import com.safesnap.backend.entity.IncidentStatus
import com.safesnap.backend.entity.IncidentSeverity
import com.safesnap.backend.exception.IncidentNotFoundException
import com.safesnap.backend.exception.UnauthorizedAccessException
import com.safesnap.backend.exception.UserNotFoundException
import com.safesnap.backend.repository.IncidentRepository
import com.safesnap.backend.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.LocalDateTime
import java.util.*

@Service
@Transactional
class IncidentService(
    private val incidentRepository: IncidentRepository,
    private val userRepository: UserRepository,
    private val imageProcessingService: ImageProcessingService
) {

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

        // Trigger async image analysis if images are provided
        if (savedIncident.imageUrls.isNotEmpty()) {
            imageProcessingService.processIncidentImages(savedIncident.id)
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
            
            incidentRepository.findIncidentsForUser(
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
        
        return incidentRepository.findAllIncidentsWithFilters(
            status = statusEnum,
            severity = severityEnum,
            search = search,
            assignedTo = assignedTo,
            pageable = pageable
        ).map { it.toResponseDTO() }
    }

    fun getIncidentById(id: UUID, userEmail: String): IncidentResponseDTO {
        val incident = incidentRepository.findById(id)
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
        val incident = incidentRepository.findById(id)
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
            imageProcessingService.processIncidentImages(incident.id)
        }
        
        if (!request.audioUrls.isNullOrEmpty()) {
            incident.audioUrls = request.audioUrls
        }

        val savedIncident = incidentRepository.save(incident)
        return savedIncident.toResponseDTO()
    }

    fun deleteIncident(id: UUID, userEmail: String) {
        val incident = incidentRepository.findById(id)
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
        val incident = incidentRepository.findById(id)
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
        val incident = incidentRepository.findById(id)
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

// Extension function to convert entity to DTO
private fun Incident.toResponseDTO(): IncidentResponseDTO {
    return IncidentResponseDTO(
        id = this.id,
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
        updatedBy = this.updatedBy?.fullName
    )
}
