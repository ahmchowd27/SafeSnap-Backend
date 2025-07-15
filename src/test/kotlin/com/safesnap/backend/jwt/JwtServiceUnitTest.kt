package com.safesnap.backend.jwt

import com.safesnap.backend.entity.Role
import io.jsonwebtoken.MalformedJwtException
import io.jsonwebtoken.security.SignatureException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

/**
 * Minimal unit tests for JWT edge cases not covered by BDD integration tests
 * Focus only on internal logic that can't be tested through API calls
 */
class JwtServiceUnitTest {

    private lateinit var jwtService: JwtService
    private val testSecret = "uqGxrUwv74rDuyxWE7c1I2VuCugiCnhV11FS3X2PxvU="

    @BeforeEach
    fun setUp() {
        jwtService = JwtService(testSecret)
    }

    @Test
    fun `should throw exception for malformed token when extracting username`() {
        // Given
        val malformedToken = "not.a.valid.jwt"

        // When & Then
        assertThrows<MalformedJwtException> {
            jwtService.extractUsername(malformedToken)
        }
    }

    @Test
    fun `should throw exception for malformed token when extracting role`() {
        // Given
        val malformedToken = "invalid.token"

        // When & Then
        assertThrows<MalformedJwtException> {
            jwtService.extractRole(malformedToken)
        }
    }

    @Test
    fun `should throw exception for token with wrong signature`() {
        // Given - Create token with different secret
        val wrongSecretService = JwtService("ZGlmZmVyZW50U2VjcmV0S2V5Rm9yVGVzdGluZ1B1cnBvc2Vz")
        val tokenWithWrongSignature = wrongSecretService.generateToken("test@example.com", Role.USER)

        // When & Then
        assertThrows<SignatureException> {
            jwtService.extractUsername(tokenWithWrongSignature)
        }
    }

    @Test
    fun `should detect token with different username during validation`() {
        // Given
        val token = jwtService.generateToken("user1@example.com", Role.USER)

        // When & Then
        assertThat(jwtService.isTokenValid(token, "user2@example.com")).isFalse()
    }

    @Test
    fun `should handle empty string token gracefully`() {
        // When & Then
        assertThrows<IllegalArgumentException> {
            jwtService.extractUsername("")
        }
    }
}
