package com.safesnap.backend.entity

import jakarta.persistence.*
import jakarta.validation.constraints.NotBlank
import java.time.LocalDateTime
import java.util.*

@Entity
@Table(name = "rca_reports")
data class RcaReport(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "manager_id", nullable = false)
    val manager: User,

    @field:NotBlank
    @Column(name = "five_whys", columnDefinition = "TEXT", nullable = false)
    var fiveWhys: String,

    @field:NotBlank
    @Column(name = "corrective_action", columnDefinition = "TEXT", nullable = false)
    var correctiveAction: String,

    @field:NotBlank
    @Column(name = "preventive_action", columnDefinition = "TEXT", nullable = false)
    var preventiveAction: String,

    @Column(name = "created_at", nullable = false)
    val createdAt: LocalDateTime = LocalDateTime.now()
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as RcaReport
        return id == other.id
    }

    override fun hashCode(): Int = id.hashCode()
    
    override fun toString(): String = "RcaReport(id=$id, incidentId=${incident.id})"
}
