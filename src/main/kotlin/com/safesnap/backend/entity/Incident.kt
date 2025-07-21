package com.safesnap.backend.entity

import jakarta.persistence.*
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "incidents")
data class Incident(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    val reportedBy: User,

    @field:NotBlank
    @Column(nullable = false)
    var title: String,

    @field:NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    var description: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var severity: IncidentSeverity,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: IncidentStatus,

    @field:DecimalMin("-90.0") 
    @field:DecimalMax("90.0")
    @Column(nullable = true)
    var latitude: Double?,

    @field:DecimalMin("-180.0") 
    @field:DecimalMax("180.0")
    @Column(nullable = true)
    var longitude: Double?,

    @Column(name = "location_description")
    var locationDescription: String? = null,

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "incident_image_urls", joinColumns = [JoinColumn(name = "incident_id")])
    @Column(name = "image_url")
    var imageUrls: List<String> = emptyList(),

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "incident_audio_urls", joinColumns = [JoinColumn(name = "incident_id")])
    @Column(name = "audio_url")
    var audioUrls: List<String> = emptyList(),

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_to")
    var assignedTo: User? = null,

    @Column(name = "reported_at", nullable = false)
    val reportedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "updated_at")
    var updatedAt: LocalDateTime? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "updated_by")
    var updatedBy: User? = null,

    @OneToMany(mappedBy = "incident", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val imageAnalyses: MutableList<ImageAnalysis> = mutableListOf(),

    @OneToMany(mappedBy = "incident", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val voiceTranscriptions: MutableList<VoiceTranscription> = mutableListOf(),

    @OneToMany(mappedBy = "incident", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val aiSuggestions: MutableList<AiSuggestion> = mutableListOf(),

    @OneToOne(mappedBy = "incident", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    var rcaReport: RcaReport? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as Incident
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    
    override fun toString(): String = "Incident(id=$id, title='$title', status=$status, severity=$severity)"
}

@Entity
@Table(name = "incident_status_history")
data class IncidentStatusHistory(
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    val id: UUID = UUID.randomUUID(),

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @Enumerated(EnumType.STRING)
    @Column(name = "old_status")
    val oldStatus: IncidentStatus?,

    @Enumerated(EnumType.STRING)
    @Column(name = "new_status", nullable = false)
    val newStatus: IncidentStatus,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "changed_by", nullable = false)
    val changedBy: User,

    @Column(name = "changed_at", nullable = false)
    val changedAt: LocalDateTime = LocalDateTime.now(),

    @Column(name = "reason")
    val reason: String? = null
)

enum class IncidentStatus {
    OPEN,
    IN_PROGRESS,
    UNDER_REVIEW,
    RESOLVED,
    CLOSED,
    CANCELLED
}

enum class IncidentSeverity {
    LOW,
    MEDIUM,
    HIGH,
    CRITICAL
}
