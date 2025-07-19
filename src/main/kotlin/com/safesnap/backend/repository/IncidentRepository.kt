package com.safesnap.backend.repository

import com.safesnap.backend.entity.Incident
import com.safesnap.backend.entity.IncidentStatus
import com.safesnap.backend.entity.IncidentSeverity
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface IncidentRepository : JpaRepository<Incident, UUID> {

    override fun findById(id: UUID): Optional<Incident?>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.reportedBy.id = :userId 
        AND (:status IS NULL OR i.status = :status)
        AND (:severity IS NULL OR i.severity = :severity)
        AND (:search IS NULL OR 
             LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) OR 
             LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(i.locationDescription) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY i.reportedAt DESC
    """)
    fun findIncidentsForUser(
        @Param("userId") userId: UUID,
        @Param("status") status: IncidentStatus?,
        @Param("severity") severity: IncidentSeverity?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE (:status IS NULL OR i.status = :status)
        AND (:severity IS NULL OR i.severity = :severity)
        AND (:assignedTo IS NULL OR i.assignedTo.email = :assignedTo)
        AND (:search IS NULL OR 
             LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) OR 
             LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(i.locationDescription) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(i.reportedBy.fullName) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY i.reportedAt DESC
    """)
    fun findAllIncidentsWithFilters(
        @Param("status") status: IncidentStatus?,
        @Param("severity") severity: IncidentSeverity?,
        @Param("search") search: String?,
        @Param("assignedTo") assignedTo: String?,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.reportedBy.id = :userId 
        ORDER BY i.reportedAt DESC
    """)
    fun findByReportedByIdOrderByReportedAtDesc(
        @Param("userId") userId: UUID,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.assignedTo.id = :userId 
        ORDER BY i.reportedAt DESC
    """)
    fun findByAssignedToIdOrderByReportedAtDesc(
        @Param("userId") userId: UUID,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.status = :status 
        ORDER BY i.reportedAt DESC
    """)
    fun findByStatusOrderByReportedAtDesc(
        @Param("status") status: IncidentStatus,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.severity = :severity 
        ORDER BY i.reportedAt DESC
    """)
    fun findBySeverityOrderByReportedAtDesc(
        @Param("severity") severity: IncidentSeverity,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.reportedBy.id = :userId
    """)
    fun countByReportedById(@Param("userId") userId: UUID): Long

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.assignedTo.id = :userId
    """)
    fun countByAssignedToId(@Param("userId") userId: UUID): Long

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.status = :status
    """)
    fun countByStatus(@Param("status") status: IncidentStatus): Long

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.severity = :severity
    """)
    fun countBySeverity(@Param("severity") severity: IncidentSeverity): Long

    @Query("""
        SELECT i FROM Incident i 
        WHERE SIZE(i.imageUrls) > 0 
        AND i.id NOT IN (
            SELECT DISTINCT ia.incident.id FROM ImageAnalysis ia 
            WHERE ia.incident.id = i.id
        )
        ORDER BY i.reportedAt ASC
    """)
    fun findIncidentsWithUnprocessedImages(): List<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE SIZE(i.audioUrls) > 0 
        AND i.id NOT IN (
            SELECT DISTINCT vt.incident.id FROM VoiceTranscription vt 
            WHERE vt.incident.id = i.id
        )
        ORDER BY i.reportedAt ASC
    """)
    fun findIncidentsWithUnprocessedAudio(): List<Incident>
    // Custom repository method to eagerly fetch images
    @Query("SELECT i FROM Incident i LEFT JOIN FETCH i.imageUrls WHERE i.id = :id")
    fun findByIdWithImages(id: UUID): Optional<Incident>
    // Add this to your IncidentRepository interface
    @Query("SELECT i.imageUrls FROM Incident i WHERE i.id = :incidentId")
    fun findImageUrlsByIncidentId(incidentId: UUID): List<String>
}

