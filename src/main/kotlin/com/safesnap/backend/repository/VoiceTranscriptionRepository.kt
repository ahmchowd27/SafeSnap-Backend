package com.safesnap.backend.repository

import com.safesnap.backend.entity.VoiceTranscription
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.time.LocalDateTime
import java.util.*

@Repository
interface VoiceTranscriptionRepository : JpaRepository<VoiceTranscription, UUID> {
    
    /**
     * Find all voice transcriptions for a specific incident
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.incident.id = :incidentId ORDER BY v.createdAt ASC")
    fun findByIncidentId(@Param("incidentId") incidentId: UUID): List<VoiceTranscription>
    
    /**
     * Find transcriptions that have been processed successfully
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.processed = true AND v.transcriptionText IS NOT NULL")
    fun findProcessedTranscriptions(): List<VoiceTranscription>
    
    /**
     * Find transcriptions that failed processing
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.processed = true AND v.errorMessage IS NOT NULL")
    fun findFailedTranscriptions(): List<VoiceTranscription>
    
    /**
     * Find unprocessed transcriptions (pending processing)
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.processed = false ORDER BY v.createdAt ASC")
    fun findUnprocessedTranscriptions(): List<VoiceTranscription>
    
    /**
     * Find transcriptions by audio URL
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.audioUrl = :audioUrl")
    fun findByAudioUrl(@Param("audioUrl") audioUrl: String): VoiceTranscription?
    
    /**
     * Find transcriptions containing specific text
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE LOWER(v.transcriptionText) LIKE LOWER(CONCAT('%', :searchText, '%'))")
    fun findByTranscriptionTextContaining(@Param("searchText") searchText: String): List<VoiceTranscription>
    
    /**
     * Find transcriptions with confidence score above threshold
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.confidenceScore >= :minConfidence ORDER BY v.confidenceScore DESC")
    fun findByConfidenceScoreAbove(@Param("minConfidence") minConfidence: Double): List<VoiceTranscription>
    
    /**
     * Count processed transcriptions for an incident
     */
    @Query("SELECT COUNT(v) FROM VoiceTranscription v WHERE v.incident.id = :incidentId AND v.processed = true")
    fun countProcessedByIncidentId(@Param("incidentId") incidentId: UUID): Long
    
    /**
     * Find transcriptions for incidents reported by specific user
     */
    @Query("""
        SELECT v FROM VoiceTranscription v 
        JOIN v.incident i 
        WHERE i.reportedBy.email = :userEmail 
        ORDER BY v.createdAt DESC
    """)
    fun findByReporterEmail(@Param("userEmail") userEmail: String): List<VoiceTranscription>
    
    /**
     * Find recent transcriptions (within last N days)
     */
    @Query("SELECT v FROM VoiceTranscription v WHERE v.createdAt >= :since ORDER BY v.createdAt DESC")
    fun findRecentTranscriptions(@Param("since") since: LocalDateTime): List<VoiceTranscription>
    
    /**
     * Delete all transcriptions for a specific incident
     */
    @Query("DELETE FROM VoiceTranscription v WHERE v.incident.id = :incidentId")
    fun deleteByIncidentId(@Param("incidentId") incidentId: UUID)
    
    /**
     * Update transcription as processed
     */
    @Query("""
        UPDATE VoiceTranscription v 
        SET v.processed = true, v.processedAt = :processedAt, v.transcriptionText = :text, v.confidenceScore = :confidence 
        WHERE v.id = :id
    """)
    fun markAsProcessed(
        @Param("id") id: UUID,
        @Param("processedAt") processedAt: LocalDateTime,
        @Param("text") transcriptionText: String,
        @Param("confidence") confidenceScore: Double?
    )
    
    /**
     * Update transcription with error
     */
    @Query("""
        UPDATE VoiceTranscription v 
        SET v.processed = true, v.processedAt = :processedAt, v.errorMessage = :errorMessage 
        WHERE v.id = :id
    """)
    fun markAsError(
        @Param("id") id: UUID,
        @Param("processedAt") processedAt: LocalDateTime,
        @Param("errorMessage") errorMessage: String
    )
}
