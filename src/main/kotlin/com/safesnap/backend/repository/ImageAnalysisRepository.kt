package com.safesnap.backend.repository

import com.safesnap.backend.entity.ImageAnalysis
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import org.springframework.stereotype.Repository
import java.util.*

@Repository
interface ImageAnalysisRepository : JpaRepository<ImageAnalysis, UUID> {

    fun findByIncidentIdOrderByProcessedAtDesc(incidentId: UUID): List<ImageAnalysis>
    
    fun findByIncidentId(incidentId: UUID): List<ImageAnalysis>
    
    fun findByIncidentIdAndImageUrl(incidentId: UUID, imageUrl: String): ImageAnalysis?
    
    fun findByIncidentIdAndProcessedTrue(incidentId: UUID): List<ImageAnalysis>
    
    fun findByIncidentIdAndProcessedFalseAndErrorMessageIsNotNull(incidentId: UUID): List<ImageAnalysis>
    
    fun findByProcessedFalse(): List<ImageAnalysis>
    
    fun findByProcessedTrue(): List<ImageAnalysis>
    
    fun findByProcessedFalseAndErrorMessageIsNotNull(): List<ImageAnalysis>
    
    fun countByProcessedTrue(): Long
    
    fun countByProcessedFalse(): Long
    
    @Query("""
        SELECT ia FROM ImageAnalysis ia 
        WHERE ia.tags LIKE %:tag%
        ORDER BY ia.processedAt DESC
    """)
    fun findByTagsContaining(@Param("tag") tag: String): List<ImageAnalysis>
    
    @Query("""
        SELECT ia FROM ImageAnalysis ia 
        WHERE ia.tags NOT LIKE %:tag%
        ORDER BY ia.processedAt DESC
    """)
    fun findByTagsNotContaining(@Param("tag") tag: String): List<ImageAnalysis>
    
    @Query("""
        SELECT COUNT(ia) FROM ImageAnalysis ia 
        WHERE ia.tags NOT LIKE %:tag%
    """)
    fun countByTagsNotContaining(@Param("tag") tag: String): Long
    
    @Query("""
        SELECT ia FROM ImageAnalysis ia 
        WHERE ia.confidenceScore >= :minConfidence
        ORDER BY ia.confidenceScore DESC
    """)
    fun findByConfidenceScoreGreaterThanEqual(@Param("minConfidence") minConfidence: Double): List<ImageAnalysis>
    
    @Query("""
        SELECT ia FROM ImageAnalysis ia 
        WHERE ia.incidentId IN :incidentIds
        AND ia.processed = true
        ORDER BY ia.processedAt DESC
    """)
    fun findProcessedAnalysesByIncidentIds(@Param("incidentIds") incidentIds: List<UUID>): List<ImageAnalysis>
    
    fun deleteByIncidentId(incidentId: UUID)

    @Query("SELECT i.imageUrls FROM Incident i WHERE i.id = :incidentId")
    fun findImageUrlsByIncidentId(incidentId: UUID): List<String>
}
