package com.safesnap.backend.controller

import com.safesnap.backend.dto.incident.IncidentCreateDTO
import com.safesnap.backend.dto.incident.IncidentResponseDTO
import com.safesnap.backend.service.IncidentService
import com.safesnap.backend.service.ImageProcessingService
import jakarta.validation.Valid
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.Authentication
import org.springframework.web.bind.annotation.*
import org.slf4j.LoggerFactory
import java.util.*

@RestController
@RequestMapping("/api/incidents")
class IncidentController(
    private val incidentService: IncidentService,
    private val imageProcessingService: ImageProcessingService
) {
    private val logger = LoggerFactory.getLogger(IncidentController::class.java)

    @PostMapping
    fun createIncident(
        @Valid @RequestBody request: IncidentCreateDTO,
        authentication: Authentication
    ): ResponseEntity<IncidentResponseDTO> {
        val incident = incidentService.createIncident(request, authentication.name)
        
        // Trigger image analysis if images are provided
        if (!request.imageUrls.isNullOrEmpty()) {
            try {
                imageProcessingService.processIncidentImages(incident.id, request.imageUrls)
            } catch (e: Exception) {
                // Log error but don't fail incident creation
                logger.warn("Failed to start image processing for incident ${incident.id}", e)
            }
        }
        
        return ResponseEntity.ok(incident)
    }

    @GetMapping
    fun getIncidents(
        authentication: Authentication,
        pageable: Pageable,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) search: String?
    ): ResponseEntity<Page<IncidentResponseDTO>> {
        val incidents = incidentService.getIncidentsForUser(
            userEmail = authentication.name,
            pageable = pageable,
            status = status,
            severity = severity,
            search = search
        )
        return ResponseEntity.ok(incidents)
    }

    @GetMapping("/{id}")
    fun getIncident(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<IncidentResponseDTO> {
        val incident = incidentService.getIncidentById(id, authentication.name)
        return ResponseEntity.ok(incident)
    }

    @PutMapping("/{id}")
    fun updateIncident(
        @PathVariable id: UUID,
        @Valid @RequestBody request: IncidentCreateDTO,
        authentication: Authentication
    ): ResponseEntity<IncidentResponseDTO> {
        val incident = incidentService.updateIncident(id, request, authentication.name)
        return ResponseEntity.ok(incident)
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('MANAGER')")
    fun deleteIncident(
        @PathVariable id: UUID,
        authentication: Authentication
    ): ResponseEntity<Void> {
        incidentService.deleteIncident(id, authentication.name)
        return ResponseEntity.noContent().build()
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('MANAGER')")
    fun getAllIncidents(
        pageable: Pageable,
        @RequestParam(required = false) status: String?,
        @RequestParam(required = false) severity: String?,
        @RequestParam(required = false) search: String?,
        @RequestParam(required = false) assignedTo: String?
    ): ResponseEntity<Page<IncidentResponseDTO>> {
        val incidents = incidentService.getAllIncidents(
            pageable = pageable,
            status = status,
            severity = severity,
            search = search,
            assignedTo = assignedTo
        )
        return ResponseEntity.ok(incidents)
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasRole('MANAGER')")
    fun updateIncidentStatus(
        @PathVariable id: UUID,
        @RequestParam status: String,
        authentication: Authentication
    ): ResponseEntity<IncidentResponseDTO> {
        val incident = incidentService.updateIncidentStatus(id, status, authentication.name)
        return ResponseEntity.ok(incident)
    }

    @PatchMapping("/{id}/assign")
    @PreAuthorize("hasRole('MANAGER')")
    fun assignIncident(
        @PathVariable id: UUID,
        @RequestParam assigneeEmail: String,
        authentication: Authentication
    ): ResponseEntity<IncidentResponseDTO> {
        val incident = incidentService.assignIncident(id, assigneeEmail, authentication.name)
        return ResponseEntity.ok(incident)
    }
}
