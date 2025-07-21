package com.safesnap.backend.repository

import com.safesnap.backend.entity.AiSuggestion
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface AiSuggestionRepository : JpaRepository<AiSuggestion, UUID> {
    
    /**
     * Find all AI suggestions for a specific incident
     */
    @Query("SELECT a FROM AiSuggestion a WHERE a.incident.id = :incidentId ORDER BY a.createdAt DESC")
    fun findByIncidentId(@Param("incidentId") incidentId: UUID): List<AiSuggestion>
    
    /**
     * Find AI suggestions containing specific keywords
     */
    @Query("SELECT a FROM AiSuggestion a WHERE LOWER(a.keywords) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    fun findByKeywordsContaining(@Param("keyword") keyword: String): List<AiSuggestion>
    
    /**
     * Find AI suggestions with summary containing specific text
     */
    @Query("SELECT a FROM AiSuggestion a WHERE LOWER(a.summary) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    fun findBySummaryContaining(@Param("searchText") searchText: String): List<AiSuggestion>
    
    /**
     * Find recent AI suggestions (within last N days)
     */
    @Query("SELECT a FROM AiSuggestion a WHERE a.createdAt >= :since ORDER BY a.createdAt DESC")
    fun findRecentSuggestions(@Param("since") since: LocalDateTime): List<AiSuggestion>
    
    /**
     * Count AI suggestions for a specific incident
     */
    @Query("SELECT COUNT(a) FROM AiSuggestion a WHERE a.incident.id = :incidentId")
    fun countByIncidentId(@Param("incidentId") incidentId: UUID): Long
    
    /**
     * Find AI suggestions for incidents reported by specific user
     */
    @Query("""
        SELECT a FROM AiSuggestion a 
        JOIN a.incident i 
        WHERE i.reportedBy.email = :userEmail 
        ORDER BY a.createdAt DESC
    """)
    fun findByReporterEmail(@Param("userEmail") userEmail: String): List<AiSuggestion>
    
    /**
     * Delete all AI suggestions for a specific incident
     */
    @Query("DELETE FROM AiSuggestion a WHERE a.incident.id = :incidentId")
    fun deleteByIncidentId(@Param("incidentId") incidentId: UUID)
}
