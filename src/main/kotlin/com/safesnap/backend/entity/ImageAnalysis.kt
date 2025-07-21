package com.safesnap.backend.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "image_analysis")
data class ImageAnalysis(
    @Id 
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @Column(name = "incident_id", nullable = false)
    val incidentId: UUID,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    val incident: Incident? = null,

    @Column(name = "image_url", nullable = false)
    val imageUrl: String,

    @Column(name = "tags", columnDefinition = "TEXT")
    val tags: String,

    @Column(name = "all_labels", columnDefinition = "TEXT")
    val allLabels: String? = null,

    @Column(name = "text_detected", columnDefinition = "TEXT")
    val textDetected: String? = null,

    @Column(name = "confidence_score")
    val confidenceScore: Double? = null,

    @Column(name = "processed", nullable = false)
    val processed: Boolean = false,

    @Column(name = "processed_at")
    val processedAt: LocalDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ImageAnalysis
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    
    override fun toString(): String = "ImageAnalysis(id=$id, incidentId=$incidentId, processed=$processed)"
}
