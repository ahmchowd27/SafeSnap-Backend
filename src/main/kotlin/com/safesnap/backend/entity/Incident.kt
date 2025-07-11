package com.safesnap.backend.entity
import jakarta.persistence.CascadeType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.FetchType
import jakarta.persistence.Table
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.JoinColumn
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToOne
import jakarta.validation.constraints.DecimalMax
import jakarta.validation.constraints.DecimalMin
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime

@Entity
@Table(name = "incidents")
data class Incident(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reported_by", nullable = false)
    val reportedBy: User,

    @field:NotBlank
    val title: String,

    @field:NotBlank
    @Column(columnDefinition = "TEXT", nullable = false)
    val description: String,

    @field:NotBlank
    val imageUrl: String,

    val audioUrl: String? = null,
    @field:DecimalMin("-90.0") @field:DecimalMax("90.0")
    val latitude: Double,

    @field:DecimalMin("-180.0") @field:DecimalMax("180.0")
    val longitude: Double,

    val createdAt: LocalDateTime = LocalDateTime.now(),

    @OneToOne(mappedBy = "incident", cascade = [CascadeType.ALL])
    val imageAnalysis: ImageAnalysis? = null,

    @OneToOne(mappedBy = "incident", cascade = [CascadeType.ALL])
    val voiceTranscription: VoiceTranscription? = null,

    @OneToOne(mappedBy = "incident", cascade = [CascadeType.ALL])
    val aiSuggestion: AiSuggestion? = null,

    @OneToOne(mappedBy = "incident", cascade = [CascadeType.ALL])
    val rcaReport: RcaReport? = null


)
