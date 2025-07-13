package com.safesnap.backend.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.SignatureAlgorithm
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.util.*
import javax.crypto.SecretKey
import java.time.Duration

@Component
class JwtService(
    @Value("\${jwt.secret}") private val secret: String
) {
    private val jwtExpiration = Duration.ofHours(24)
    private val signingKey: SecretKey = Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret))

    fun generateToken(username: String, role: Enum<*>): String {
        val now = Date()
        val expiry = Date(now.time + jwtExpiration.toMillis())

        return Jwts.builder()
            .setSubject(username)
            .claim("role", role.name)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(signingKey, SignatureAlgorithm.HS256)
            .compact()
    }

    fun extractUsername(token: String): String =
        Jwts.parserBuilder().setSigningKey(signingKey).build()
            .parseClaimsJws(token).body.subject

    fun extractRole(token: String): String =
        Jwts.parserBuilder().setSigningKey(signingKey).build()
            .parseClaimsJws(token).body["role"] as String

    fun isTokenValid(token: String, username: String): Boolean {
        return extractUsername(token) == username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        val expiration = Jwts.parserBuilder().setSigningKey(signingKey).build()
            .parseClaimsJws(token).body.expiration
        return expiration.before(Date())
    }
}
