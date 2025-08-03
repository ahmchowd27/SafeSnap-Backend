package com.safesnap.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "rca_ai_suggestions")
data class RcaAiSuggestion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @Version
    var version: Long = 0,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    // AI-Generated Content
    @Column(name = "suggested_five_whys", columnDefinition = "TEXT", nullable = false)
    val suggestedFiveWhys: String,

    @Column(name = "suggested_corrective_action", columnDefinition = "TEXT", nullable = false)
    val suggestedCorrectiveAction: String,

    @Column(name = "suggested_preventive_action", columnDefinition = "TEXT", nullable = false)
    val suggestedPreventiveAction: String,

    // AI Metadata
    @Column(name = "confidence_score")
    val confidenceScore: Double,

    @Column(name = "incident_category")
    @Enumerated(EnumType.STRING)
    val incidentCategory: IncidentCategory,

    @Column(name = "template_used")
    val templateUsed: String,

    @Column(name = "openai_model")
    val openaiModel: String = "gpt-3.5-turbo",

    @Column(name = "tokens_used")
    val tokensUsed: Int? = null,

    @Column(name = "processing_time_ms")
    val processingTimeMs: Long? = null,

    // Status Tracking
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: RcaAiStatus = RcaAiStatus.GENERATED,

    @Column(name = "generated_at", nullable = false)
    val generatedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "reviewed_at")
    var reviewedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by")
    var reviewedBy: User? = null,

    @Column(name = "error_message")
    var errorMessage: String? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RcaAiSuggestion
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
    
    override fun toString(): String = "RcaAiSuggestion(id=$id, incidentId=${incident.id}, status=$status)"
}

enum class RcaAiStatus {
    GENERATING,    // AI is processing
    GENERATED,     // AI completed, waiting for manager review
    REVIEWED,      // Manager has seen it
    APPROVED,      // Manager approved (will create final RCA)
    MODIFIED,      // Manager modified before approving
    FAILED         // AI generation failed
}

enum class IncidentCategory {
    PPE_VIOLATION,
    EQUIPMENT_MALFUNCTION, 
    SLIP_TRIP_FALL,
    LIFTING_INJURY,
    CHEMICAL_EXPOSURE,
    ELECTRICAL_INCIDENT,
    VEHICLE_INCIDENT,
    FIRE_EXPLOSION,
    CONFINED_SPACE,
    GENERAL_SAFETY
}
