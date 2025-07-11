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
import jakarta.validation.constraints.NotNull
import java.time.LocalDateTime

@Entity
@Table(name = "rca_reports")
data class RcaReport(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @OneToOne
    @JoinColumn(name = "incident_id", nullable = false)
    val incident: Incident,

    @field:NotBlank
    @Column(columnDefinition = "TEXT")
    val fiveWhys: String,

    @field:NotBlank
    @Column(columnDefinition = "TEXT")
    val correctiveAction: String,

    @field:NotBlank
    @Column(columnDefinition = "TEXT")
    val preventiveAction: String,

    @field:NotNull
    val submittedBy: Long, // Manager ID

    val submittedAt: LocalDateTime = LocalDateTime.now()
)
