package com.safesnap.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "ai_suggestions")
data class AiSuggestion(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID? = null,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @Column(name = "summary", columnDefinition = "TEXT", nullable = false)
    val summary: String,

    @Column(name = "keywords", columnDefinition = "TEXT")
    val keywords: String, // JSON array stored as string: ["hard hat", "safety vest"]

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as AiSuggestion
        return id != null && id == other.id
    }

    override fun hashCode(): Int = id?.hashCode() ?: 0
    
    override fun toString(): String = "AiSuggestion(id=$id, incidentId=${incident.id})"
}
