package com.safesnap.backend.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.OneToOne
import jakarta.persistence.Table
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

@Entity
@Table(name = "ai_suggestions")
data class AiSuggestion(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @field:NotBlank
    @Column(columnDefinition = "TEXT")
    val suggestionText: String,

    @field:NotBlank
    @Column(columnDefinition = "TEXT")
    val usedVisionTags: String,

    @Column(columnDefinition = "TEXT")
    val usedTranscript: String? = null,

    val generatedAt: LocalDateTime = LocalDateTime.now()
)
