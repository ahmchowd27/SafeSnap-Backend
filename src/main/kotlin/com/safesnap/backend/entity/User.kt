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
import com.safesnap.backend.config.SafeSnapConstants

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
    @field:Size(min = SafeSnapConstants.MIN_PASSWORD_LENGTH, max = SafeSnapConstants.MAX_PASSWORD_LENGTH, 
                message = "Password must be between ${SafeSnapConstants.MIN_PASSWORD_LENGTH} and ${SafeSnapConstants.MAX_PASSWORD_LENGTH} characters long")
    @Column(nullable = false)
    val password: String,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val role: Role = Role.WORKER,

    @field:NotBlank
    @Column(nullable = false, length = SafeSnapConstants.MAX_NAME_LENGTH)
    val fullName: String
)
enum class Role {
    WORKER,
    MANAGER
}
