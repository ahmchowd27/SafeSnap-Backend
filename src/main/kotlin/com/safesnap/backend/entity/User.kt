package com.safesnap.backend.entity
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.Table
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.validation.constraints.Email
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size

@Entity
@Table(name = "users")
data class User(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0L,
    @field:Email
    @field:NotBlank
    @Column(unique = true, nullable = false)
    val email: String,

    @field:NotBlank
    @field:Size(min = 8, max = 64, message = "Password must be between 8 and 64 characters long")
    @Column(nullable = false)
    val password: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.WORKER,

    @field:NotBlank
    @Column(nullable = false, length = 50)
    val fullName: String
)
enum class Role {
    WORKER,
    MANAGER
}
