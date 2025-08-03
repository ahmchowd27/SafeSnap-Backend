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

    override fun findById(id: UUID): Optional<Incident>

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
        @Param("userId") userId: Long,
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
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        WHERE i.assignedTo.id = :userId 
        ORDER BY i.reportedAt DESC
    """)
    fun findByAssignedToIdOrderByReportedAtDesc(
        @Param("userId") userId: Long,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.reportedBy.id = :userId
    """)
    fun countByReportedById(@Param("userId") userId: Long): Long

    @Query("""
        SELECT COUNT(i) FROM Incident i 
        WHERE i.assignedTo.id = :userId
    """)
    fun countByAssignedToId(@Param("userId") userId: Long): Long

    @Query("SELECT i FROM Incident i LEFT JOIN FETCH i.rcaReport r LEFT JOIN FETCH r.manager LEFT JOIN FETCH i.rcaAiSuggestion WHERE i.id = :id")
    fun findByIdWithRca(@Param("id") id: UUID): Optional<Incident>

    @Query("""
        SELECT i FROM Incident i 
        LEFT JOIN FETCH i.rcaReport r 
        LEFT JOIN FETCH r.manager
        LEFT JOIN FETCH i.rcaAiSuggestion
        WHERE i.reportedBy.id = :userId 
        AND (:status IS NULL OR i.status = :status)
        AND (:severity IS NULL OR i.severity = :severity)
        AND (:search IS NULL OR 
             LOWER(i.title) LIKE LOWER(CONCAT('%', :search, '%')) OR 
             LOWER(i.description) LIKE LOWER(CONCAT('%', :search, '%')) OR
             LOWER(i.locationDescription) LIKE LOWER(CONCAT('%', :search, '%')))
        ORDER BY i.reportedAt DESC
    """)
    fun findIncidentsForUserWithRca(
        @Param("userId") userId: Long,
        @Param("status") status: IncidentStatus?,
        @Param("severity") severity: IncidentSeverity?,
        @Param("search") search: String?,
        pageable: Pageable
    ): Page<Incident>

    @Query("""
        SELECT i FROM Incident i 
        LEFT JOIN FETCH i.rcaReport r 
        LEFT JOIN FETCH r.manager
        LEFT JOIN FETCH i.rcaAiSuggestion
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
    fun findAllIncidentsWithFiltersAndRca(
        @Param("status") status: IncidentStatus?,
        @Param("severity") severity: IncidentSeverity?,
        @Param("search") search: String?,
        @Param("assignedTo") assignedTo: String?,
        pageable: Pageable
    ): Page<Incident>
}
