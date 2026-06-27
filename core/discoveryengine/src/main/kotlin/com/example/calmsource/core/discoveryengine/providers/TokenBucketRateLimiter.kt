package com.example.calmsource.core.discoveryengine.providers

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.math.ceil

enum class ProviderRequestPriority {
    USER_VISIBLE,
    BACKGROUND
}

data class RateLimitConfig(
    val tokensPerSecond: Double,
    val burstCapacity: Int,
    val maxWaitMs: Long
)

data class RateLimiterSnapshot(
    val providerId: String,
    val requestType: String,
    val availableTokens: Double,
    val burstCapacity: Int,
    val deniedRequests: Long
)

class TokenBucketRateLimiter(
    private val clockMs: () -> Long = { System.currentTimeMillis() },
    private val configs: Map<String, RateLimitConfig> = DEFAULT_CONFIGS
) {
    private data class Bucket(
        var tokens: Double,
        var lastRefillMs: Long,
        var deniedRequests: Long = 0
    )

    private val mutex = Mutex()
    private val buckets = mutableMapOf<String, Bucket>()

    suspend fun acquire(
        providerId: String,
        requestType: String,
        priority: ProviderRequestPriority = ProviderRequestPriority.BACKGROUND
    ): Boolean {
        val config = configFor(requestType)
        val maxWaitMs = if (priority == ProviderRequestPriority.USER_VISIBLE) {
            config.maxWaitMs * 2L
        } else {
            config.maxWaitMs
        }
        val startedAt = clockMs()

        while (true) {
            val waitMs = mutex.withLock {
                val now = clockMs()
                val key = key(providerId, requestType)
                val bucket = buckets.getOrPut(key) {
                    Bucket(tokens = config.burstCapacity.toDouble(), lastRefillMs = now)
                }
                refill(bucket, config, now)

                if (bucket.tokens >= 1.0) {
                    bucket.tokens -= 1.0
                    return@withLock 0L
                }

                val elapsedMs = now - startedAt
                val refillPerMs = config.tokensPerSecond / 1000.0
                val nextWaitMs = if (refillPerMs <= 0.0) {
                    maxWaitMs + 1L
                } else {
                    ceil((1.0 - bucket.tokens) / refillPerMs).toLong().coerceAtLeast(1L)
                }

                if (elapsedMs + nextWaitMs > maxWaitMs) {
                    bucket.deniedRequests++
                    return@withLock -1L
                }

                nextWaitMs.coerceAtMost(250L)
            }

            when {
                waitMs == 0L -> return true
                waitMs < 0L -> return false
                else -> delay(waitMs)
            }
        }
    }

    suspend fun snapshot(): List<RateLimiterSnapshot> = mutex.withLock {
        buckets.entries.map { (key, bucket) ->
            val parts = key.split(":", limit = 2)
            val requestType = parts.getOrNull(1).orEmpty()
            val config = configFor(requestType)
            RateLimiterSnapshot(
                providerId = parts.firstOrNull().orEmpty(),
                requestType = requestType,
                availableTokens = bucket.tokens,
                burstCapacity = config.burstCapacity,
                deniedRequests = bucket.deniedRequests
            )
        }
    }

    private fun refill(bucket: Bucket, config: RateLimitConfig, now: Long) {
        val elapsedMs = (now - bucket.lastRefillMs).coerceAtLeast(0L)
        if (elapsedMs == 0L) return
        val refill = (elapsedMs / 1000.0) * config.tokensPerSecond
        bucket.tokens = (bucket.tokens + refill).coerceAtMost(config.burstCapacity.toDouble())
        bucket.lastRefillMs = now
    }

    private fun configFor(requestType: String): RateLimitConfig {
        return configs[requestType] ?: configs.getValue(DEFAULT)
    }

    private fun key(providerId: String, requestType: String): String = "$providerId:$requestType"

    companion object {
        private const val DEFAULT = "default"

        val DEFAULT_CONFIGS: Map<String, RateLimitConfig> = mapOf(
            "metadata" to RateLimitConfig(tokensPerSecond = 3.0, burstCapacity = 5, maxWaitMs = 5_000L),
            "ratings" to RateLimitConfig(tokensPerSecond = 2.0, burstCapacity = 4, maxWaitMs = 5_000L),
            "similar" to RateLimitConfig(tokensPerSecond = 2.0, burstCapacity = 4, maxWaitMs = 5_000L),
            "subtitles" to RateLimitConfig(tokensPerSecond = 2.0, burstCapacity = 4, maxWaitMs = 5_000L),
            "availability" to RateLimitConfig(tokensPerSecond = 1.0, burstCapacity = 3, maxWaitMs = 3_000L),
            "artwork" to RateLimitConfig(tokensPerSecond = 1.0, burstCapacity = 3, maxWaitMs = 3_000L),
            DEFAULT to RateLimitConfig(tokensPerSecond = 2.0, burstCapacity = 4, maxWaitMs = 5_000L)
        )
    }
}
