package com.safesnap.backend.repository

import com.safesnap.backend.entity.RcaReport
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface RcaReportRepository : JpaRepository<RcaReport, Long> {
    
    /**
     * Find RCA report by incident ID
     */
    @Query("SELECT r FROM RcaReport r WHERE r.incident.id = :incidentId")
    fun findByIncidentId(@Param("incidentId") incidentId: UUID): RcaReport?
    
    /**
     * Find all RCA reports created by a specific manager
     */
    @Query("SELECT r FROM RcaReport r WHERE r.manager.id = :managerId ORDER BY r.createdAt DESC")
    fun findByManagerId(@Param("managerId") managerId: Long): List<RcaReport>
    
    /**
     * Find all RCA reports created by a manager's email
     */
    @Query("SELECT r FROM RcaReport r WHERE r.manager.email = :managerEmail ORDER BY r.createdAt DESC")
    fun findByManagerEmail(@Param("managerEmail") managerEmail: String): List<RcaReport>
    
    /**
     * Check if incident already has an RCA report
     */
    @Query("SELECT COUNT(r) > 0 FROM RcaReport r WHERE r.incident.id = :incidentId")
    fun existsByIncidentId(@Param("incidentId") incidentId: UUID): Boolean
    
    /**
     * Find RCA reports for incidents with specific status
     */
    @Query("""
        SELECT r FROM RcaReport r 
        JOIN r.incident i 
        WHERE i.status = :status 
        ORDER BY r.createdAt DESC
    """)
    fun findByIncidentStatus(@Param("status") status: String): List<RcaReport>
}
