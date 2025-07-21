package com.safesnap.backend.entity

import jakarta.persistence.*
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "voice_transcriptions")
data class VoiceTranscription(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @Column(name = "audio_url", nullable = false)
    val audioUrl: String,

    @Column(name = "transcription_text", columnDefinition = "TEXT")
    val transcriptionText: String? = null,

    @Column(name = "confidence_score")
    val confidenceScore: Double? = null,

    @Column(name = "processed", nullable = false)
    val processed: Boolean = false,

    @Column(name = "processed_at")
    val processedAt: LocalDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    val errorMessage: String? = null,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as VoiceTranscription
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    
    override fun toString(): String = "VoiceTranscription(id=$id, incidentId=${incident.id}, processed=$processed)"
}
