package com.safesnap.backend.jwt

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.slf4j.LoggerFactory
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.core.userdetails.UserDetailsService
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthFilter(
    private val jwtService: JwtService,
    private val userDetailsService: UserDetailsService
) : OncePerRequestFilter() {

    private val logger = LoggerFactory.getLogger(JwtAuthFilter::class.java)

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        
        // Skip JWT processing for auth endpoints
        if (request.requestURI.startsWith("/api/auth/")) {
            filterChain.doFilter(request, response)
            return
        }
        
        if (authHeader.isNullOrBlank() || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response)
            return
        }

        try {
            val token = authHeader.substring(7)
            val username = jwtService.extractUsername(token)

            if (SecurityContextHolder.getContext().authentication == null) {
                val userDetails = userDetailsService.loadUserByUsername(username)
                if (jwtService.isTokenValid(token, userDetails.username)) {
                    val role = jwtService.extractRole(token)
                    val authorities = listOf(SimpleGrantedAuthority("ROLE_$role"))

                    val authToken = UsernamePasswordAuthenticationToken(userDetails, null, authorities)
                    authToken.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authToken
                }
            }
        } catch (ex: Exception) {
            // Log the exception but let Spring Security handle the response
            logger.warn("JWT processing failed: ${ex.message}")
        }

        filterChain.doFilter(request, response)
    }
}
