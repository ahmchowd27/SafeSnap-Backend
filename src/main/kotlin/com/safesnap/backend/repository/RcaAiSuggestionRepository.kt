package com.safesnap.backend.repository

import com.safesnap.backend.entity.RcaAiSuggestion
import com.safesnap.backend.entity.RcaAiStatus
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface RcaAiSuggestionRepository : JpaRepository<RcaAiSuggestion, UUID> {
    
    /**
     * Find RCA AI suggestion by incident ID
     */
    @Query("SELECT r FROM RcaAiSuggestion r WHERE r.incident.id = :incidentId")
    fun findByIncidentId(@Param("incidentId") incidentId: UUID): RcaAiSuggestion?
    
    /**
     * Find all suggestions by status
     */
    fun findByStatus(status: RcaAiStatus): List<RcaAiSuggestion>
    
    /**
     * Find suggestions that need manager review
     */
    @Query("SELECT r FROM RcaAiSuggestion r WHERE r.status = 'GENERATED' ORDER BY r.generatedAt ASC")
    fun findPendingReview(): List<RcaAiSuggestion>
    
    /**
     * Find suggestions reviewed by a specific manager
     */
    @Query("SELECT r FROM RcaAiSuggestion r WHERE r.reviewedBy.email = :managerEmail ORDER BY r.reviewedAt DESC")
    fun findByReviewedByEmail(@Param("managerEmail") managerEmail: String): List<RcaAiSuggestion>
    
    /**
     * Count suggestions by status
     */
    fun countByStatus(status: RcaAiStatus): Long
    
    /**
     * Check if incident already has AI suggestions
     */
    @Query("SELECT COUNT(r) > 0 FROM RcaAiSuggestion r WHERE r.incident.id = :incidentId")
    fun existsByIncidentId(@Param("incidentId") incidentId: UUID): Boolean
    
    /**
     * Find failed generations for retry
     */
    @Query("""
        SELECT r FROM RcaAiSuggestion r 
        WHERE r.status = 'FAILED' 
        AND r.generatedAt > :since 
        ORDER BY r.generatedAt DESC
    """)
    fun findFailedSince(@Param("since") since: LocalDateTime): List<RcaAiSuggestion>
    
    /**
     * Statistics for monitoring
     */
    @Query("""
        SELECT AVG(r.processingTimeMs) FROM RcaAiSuggestion r 
        WHERE r.status = 'GENERATED' 
        AND r.processingTimeMs IS NOT NULL
    """)
    fun getAverageProcessingTime(): Double?
    
    @Query("""
        SELECT AVG(r.tokensUsed) FROM RcaAiSuggestion r 
        WHERE r.tokensUsed IS NOT NULL 
        AND r.generatedAt > :since
    """)
    fun getAverageTokenUsage(@Param("since") since: LocalDateTime): Double?
}
