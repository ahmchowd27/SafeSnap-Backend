package com.safesnap.backend.jwt

import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.time.Duration
import io.jsonwebtoken.SignatureAlgorithm

import java.util.Date


@Component
class JwtService(
    @Value("\${jwt.secret}") private val secretKey: String
) {

    private val jwtExpiration = Duration.ofHours(24)

    fun generateToken(username: String): String {
        val claims = Jwts.claims().setSubject(username)
        val now = Date()
        val expiry = Date(now.time + jwtExpiration.toMillis())

        return Jwts.builder()
            .setClaims(claims)
            .setIssuedAt(now)
            .setExpiration(expiry)
            .signWith(Keys.hmacShaKeyFor(secretKey.toByteArray()), SignatureAlgorithm.HS256)
            .compact()
    }

    fun extractUsername(token: String): String =
        Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secretKey.toByteArray()))
            .build()
            .parseClaimsJws(token)
            .body
            .subject

    fun isTokenValid(token: String, username: String): Boolean {
        val extracted = extractUsername(token)
        return extracted == username && !isTokenExpired(token)
    }

    private fun isTokenExpired(token: String): Boolean {
        val expiration = Jwts.parserBuilder()
            .setSigningKey(Keys.hmacShaKeyFor(secretKey.toByteArray()))
            .build()
            .parseClaimsJws(token)
            .body
            .expiration
        return expiration.before(Date())
    }
}
