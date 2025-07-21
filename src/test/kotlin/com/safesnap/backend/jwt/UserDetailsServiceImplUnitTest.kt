package com.safesnap.backend.jwt

import com.safesnap.backend.entity.Role
import com.safesnap.backend.entity.User
import com.safesnap.backend.repository.UserRepository
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.extension.ExtendWith
import org.mockito.InjectMocks
import org.mockito.Mock
import org.mockito.junit.jupiter.MockitoExtension
import org.mockito.kotlin.whenever
import org.springframework.security.core.userdetails.UsernameNotFoundException

/**
 * Unit test for UserDetailsServiceImpl - only testing specific logic not covered by integration
 */
@ExtendWith(MockitoExtension::class)
class UserDetailsServiceImplUnitTest {

    @Mock
    private lateinit var userRepository: UserRepository

    @InjectMocks
    private lateinit var userDetailsService: UserDetailsServiceImpl

    @Test
    fun `should throw UsernameNotFoundException when user not found`() {
        // Given
        whenever(userRepository.findByEmail("nonexistent@example.com")).thenReturn(null)

        // When & Then
        val exception = assertThrows<UsernameNotFoundException> {
            userDetailsService.loadUserByUsername("nonexistent@example.com")
        }
        
        assertThat(exception.message).contains("User not found")
    }

    @Test
    fun `should map user roles correctly to authorities`() {
        // Given
        val user = User(
            id = 1L,
            fullName = "Test User",
            email = "test@example.com",
            password = "encodedPassword",
            role = Role.MANAGER
        )
        whenever(userRepository.findByEmail("test@example.com")).thenReturn(user)

        // When
        val userDetails = userDetailsService.loadUserByUsername("test@example.com")

        // Then
        assertThat(userDetails.authorities).hasSize(1)
        assertThat(userDetails.authorities.first().authority).isEqualTo("ROLE_MANAGER")
    }
}
