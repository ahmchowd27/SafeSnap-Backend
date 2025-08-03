package com.safesnap.backend.dto.incident

import com.safesnap.backend.dto.user.UserResponseDTO
import com.safesnap.backend.entity.RcaReport
import java.time.LocalDateTime

data class RcaResponseDTO(
    val id: Long,
    val fiveWhys: String,
    val correctiveAction: String,
    val preventiveAction: String,
    val createdAt: LocalDateTime,
    val manager: UserResponseDTO
) {
    companion object {
        fun fromEntity(rcaReport: RcaReport): RcaResponseDTO {
            return RcaResponseDTO(
                id = rcaReport.id,
                fiveWhys = rcaReport.fiveWhys,
                correctiveAction = rcaReport.correctiveAction,
                preventiveAction = rcaReport.preventiveAction,
                createdAt = rcaReport.createdAt,
                manager = UserResponseDTO(
                    id = rcaReport.manager.id,
                    name = rcaReport.manager.fullName,
                    email = rcaReport.manager.email,
                    role = rcaReport.manager.role
                )
            )
        }
    }
}