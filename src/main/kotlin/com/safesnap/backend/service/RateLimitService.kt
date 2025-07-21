package com.safesnap.backend.service

import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import org.springframework.stereotype.Service
import java.time.Duration
import java.util.concurrent.TimeUnit

@Service
class RateLimitService {
    
    // Cache to store rate limit buckets per user/IP
    private val cache: Cache<String, Bucket> = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(1, TimeUnit.HOURS)
        .build()
    
    /**
     * Rate limits for different operations
     */
    enum class RateLimitType(
        val capacity: Long,
        val refillTokens: Long,
        val refillPeriod: Duration,
        val description: String
    ) {
        // Authentication limits
        LOGIN_ATTEMPTS(5, 5, Duration.ofMinutes(15), "Login attempts per 15 minutes"),
        REGISTRATION(3, 3, Duration.ofHours(1), "Registrations per hour"),
        
        // File upload limits
        FILE_UPLOADS(20, 20, Duration.ofHours(1), "File uploads per hour"),
        LARGE_FILE_UPLOADS(5, 5, Duration.ofHours(1), "Large file uploads per hour"),
        
        // API limits
        INCIDENT_CREATION(10, 10, Duration.ofMinutes(10), "Incident creation per 10 minutes"),
        GENERAL_API(100, 100, Duration.ofMinutes(1), "General API calls per minute"),
        
        // Vision API limits (expensive operations)
        VISION_API(50, 50, Duration.ofHours(1), "Vision API calls per hour")
    }
    
    /**
     * Check if operation is allowed for the given key
     */
    fun isAllowed(key: String, rateLimitType: RateLimitType): Boolean {
        val bucket = cache.get(key) { createBucket(rateLimitType) }
        return bucket.tryConsume(1)
    }
    
    /**
     * Get remaining tokens for the given key
     */
    fun getRemainingTokens(key: String, rateLimitType: RateLimitType): Long {
        val bucket = cache.get(key) { createBucket(rateLimitType) }
        return bucket.availableTokens
    }
    
    /**
     * Get time until next refill
     */
    fun getTimeUntilRefill(key: String, rateLimitType: RateLimitType): Duration? {
        val bucket = cache.get(key) { createBucket(rateLimitType) }
        return try {
            val probe = bucket.tryConsumeAndReturnRemaining(0)
            if (probe.isConsumed) null else Duration.ofNanos(probe.nanosToWaitForRefill)
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * Create rate limit bucket for specific type
     */
    private fun createBucket(rateLimitType: RateLimitType): Bucket {
        val bandwidth = Bandwidth.classic(
            rateLimitType.capacity,
            Refill.intervally(rateLimitType.refillTokens, rateLimitType.refillPeriod)
        )
        return Bucket.builder()
            .addLimit(bandwidth)
            .build()
    }
    
    /**
     * Generate cache key for user-based rate limiting
     */
    fun getUserKey(userEmail: String, operation: String): String {
        return "user:$userEmail:$operation"
    }
    
    /**
     * Generate cache key for IP-based rate limiting
     */
    fun getIpKey(ipAddress: String, operation: String): String {
        return "ip:$ipAddress:$operation"
    }
    
    /**
     * Clear rate limit for a specific key (admin function)
     */
    fun clearRateLimit(key: String) {
        cache.invalidate(key)
    }
    
    /**
     * Get rate limit statistics
     */
    fun getRateLimitStats(): Map<String, Any> {
        return mapOf(
            "cache_size" to cache.estimatedSize(),
            "cache_stats" to cache.stats().toString(),
            "rate_limit_types" to RateLimitType.values().map { 
                mapOf(
                    "name" to it.name,
                    "description" to it.description,
                    "capacity" to it.capacity,
                    "refill_tokens" to it.refillTokens,
                    "refill_period" to it.refillPeriod.toString()
                )
            }
        )
    }
}
