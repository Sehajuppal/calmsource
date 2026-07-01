package com.example.calmsource.core.model

import java.security.MessageDigest

/**
 * Reliability tiers determined by health score.
 */
enum class SourceReliabilityTier {
    EXCELLENT,
    GOOD,
    UNSTABLE,
    POOR,
    BLOCKED,
    UNKNOWN
}

/**
 * Health signals emitted by the playback/source subsystems.
 */
enum class SourceHealthSignal {
    PLAYBACK_SUCCESS,
    PLAYBACK_FAILURE,
    PLAYBACK_TIMEOUT,
    UNSUPPORTED_FORMAT,
    PROVIDER_UNREACHABLE,
    USER_SKIPPED,
    USER_MARKED_BAD,
    STARTUP_TIME,
    BUFFERING_SEVERITY
}

/**
 * Fallback policies when a source is unhealthy.
 */
enum class AutoFallbackPolicy {
    OFF,
    ASK_BEFORE_FALLBACK,
    AUTO_FALLBACK_ONCE,
    AUTO_FALLBACK_LIMITED
}

/**
 * Simple pairing of score and its computed reliability tier.
 */
data class SourceHealthScore(
    val score: Int,
    val tier: SourceReliabilityTier
) {
    companion object {
        /**
         * Resolves the reliability tier based on score.
         * If the source is user-hidden, it is forced to BLOCKED.
         */
        fun fromScore(score: Int, userHidden: Boolean = false): SourceHealthScore {
            if (userHidden) {
                return SourceHealthScore(0, SourceReliabilityTier.BLOCKED)
            }
            val clamped = score.coerceIn(0, 100)
            val tier = when (clamped) {
                in 90..100 -> SourceReliabilityTier.EXCELLENT
                in 70..89 -> SourceReliabilityTier.GOOD
                in 40..69 -> SourceReliabilityTier.UNSTABLE
                in 1..39 -> SourceReliabilityTier.POOR
                0 -> SourceReliabilityTier.BLOCKED
                else -> SourceReliabilityTier.UNKNOWN
            }
            return SourceHealthScore(clamped, tier)
        }
    }
}

/**
 * Health statistics and state for an individual playback source.
 */
data class SourceHealth(
    val sourceId: String,
    val providerId: String,
    val sourceType: PlaybackSourceType,
    val failureCount: Int = 0,
    val lastSuccessTime: Long = 0L,
    val lastFailureTime: Long = 0L,
    val averageStartupTime: Long = 0L,
    val averageBufferingSeverity: Float = 0.0f,
    val lastErrorCategory: String = "",
    val healthScore: Int = 100,
    val userHidden: Boolean = false
) {
    val reliabilityTier: SourceReliabilityTier
        get() = SourceHealthScore.fromScore(healthScore, userHidden).tier

    /**
     * Creates a new instance of [SourceHealth] with the health signal applied.
     */
    fun applySignal(
        signal: SourceHealthSignal,
        timestamp: Long = System.currentTimeMillis(),
        startupTimeMs: Long = 0L,
        bufferingSeverity: Float = 0.0f,
        errorCategory: String = ""
    ): SourceHealth {
        return when (signal) {
            SourceHealthSignal.PLAYBACK_SUCCESS -> {
                this.copy(
                    failureCount = 0,
                    lastSuccessTime = timestamp,
                    healthScore = 100
                )
            }
            SourceHealthSignal.PLAYBACK_FAILURE,
            SourceHealthSignal.PLAYBACK_TIMEOUT,
            SourceHealthSignal.UNSUPPORTED_FORMAT -> {
                val newScore = (healthScore - 20).coerceIn(0, 100)
                this.copy(
                    failureCount = failureCount + 1,
                    lastFailureTime = timestamp,
                    healthScore = newScore,
                    lastErrorCategory = errorCategory.ifEmpty { signal.name }
                )
            }
            SourceHealthSignal.PROVIDER_UNREACHABLE -> {
                val newScore = (healthScore - 15).coerceIn(0, 100)
                this.copy(
                    lastFailureTime = timestamp,
                    healthScore = newScore,
                    lastErrorCategory = errorCategory.ifEmpty { signal.name }
                )
            }
            SourceHealthSignal.USER_MARKED_BAD -> {
                this.copy(
                    healthScore = 0,
                    userHidden = true,
                    lastFailureTime = timestamp,
                    lastErrorCategory = "USER_MARKED_BAD"
                )
            }
            SourceHealthSignal.USER_SKIPPED -> {
                val newScore = (healthScore - 5).coerceIn(0, 100)
                this.copy(
                    healthScore = newScore
                )
            }
            SourceHealthSignal.STARTUP_TIME -> {
                val newAvg = if (averageStartupTime == 0L) {
                    startupTimeMs
                } else {
                    (averageStartupTime * 3 + startupTimeMs) / 4
                }
                this.copy(averageStartupTime = newAvg)
            }
            SourceHealthSignal.BUFFERING_SEVERITY -> {
                val newAvg = if (averageBufferingSeverity == 0.0f) {
                    bufferingSeverity
                } else {
                    (averageBufferingSeverity * 3.0f + bufferingSeverity) / 4.0f
                }
                this.copy(averageBufferingSeverity = newAvg)
            }
        }
    }

    /**
     * Recovery logic: if last failure was > 1 hour ago, allow recovery steps.
     * We recover 10 points per hour elapsed since lastFailureTime.
     * If fully recovered to 100, reset the failure count.
     */
    fun getUpdatedHealth(currentTime: Long = System.currentTimeMillis()): SourceHealth {
        if (healthScore >= 100 || lastFailureTime <= 0L || userHidden) return this
        val timeDiff = currentTime - lastFailureTime
        if (timeDiff > 3600_000L) { // > 1 hour
            val hoursElapsed = timeDiff / 3600_000L
            val recoveryPoints = (hoursElapsed * 10).toInt()
            val newScore = (healthScore + recoveryPoints).coerceAtMost(100)
            val newFailureCount = if (newScore == 100) 0 else failureCount
            return this.copy(
                healthScore = newScore,
                failureCount = newFailureCount
            )
        }
        return this
    }
}

/**
 * Health statistics and state for a playback provider (which hosts multiple sources).
 */
data class ProviderHealthScore(
    val providerId: String,
    val sourceType: PlaybackSourceType,
    val failureCount: Int = 0,
    val successCount: Int = 0,
    val lastFailureTime: Long = 0L,
    val lastSuccessTime: Long = 0L,
    val timeoutCount: Int = 0,
    val healthScore: Int = 100
) {
    /**
     * Creates a new instance of [ProviderHealthScore] with the signal applied.
     */
    fun applySignal(
        signal: SourceHealthSignal,
        timestamp: Long = System.currentTimeMillis()
    ): ProviderHealthScore {
        return when (signal) {
            SourceHealthSignal.PLAYBACK_SUCCESS -> {
                this.copy(
                    successCount = successCount + 1,
                    failureCount = 0,
                    timeoutCount = 0,
                    lastSuccessTime = timestamp,
                    healthScore = 100
                )
            }
            SourceHealthSignal.PLAYBACK_FAILURE,
            SourceHealthSignal.UNSUPPORTED_FORMAT -> {
                val newScore = (healthScore - 20).coerceIn(0, 100)
                this.copy(
                    failureCount = failureCount + 1,
                    lastFailureTime = timestamp,
                    healthScore = newScore
                )
            }
            SourceHealthSignal.PLAYBACK_TIMEOUT -> {
                val newScore = (healthScore - 20).coerceIn(0, 100)
                this.copy(
                    timeoutCount = timeoutCount + 1,
                    failureCount = failureCount + 1,
                    lastFailureTime = timestamp,
                    healthScore = newScore
                )
            }
            SourceHealthSignal.PROVIDER_UNREACHABLE -> {
                val newScore = (healthScore - 15).coerceIn(0, 100)
                this.copy(
                    failureCount = failureCount + 1,
                    lastFailureTime = timestamp,
                    healthScore = newScore
                )
            }
            SourceHealthSignal.USER_MARKED_BAD -> {
                val newScore = (healthScore - 30).coerceIn(0, 100)
                this.copy(
                    lastFailureTime = timestamp,
                    healthScore = newScore
                )
            }
            SourceHealthSignal.USER_SKIPPED -> {
                val newScore = (healthScore - 5).coerceIn(0, 100)
                this.copy(healthScore = newScore)
            }
            SourceHealthSignal.STARTUP_TIME,
            SourceHealthSignal.BUFFERING_SEVERITY -> this
        }
    }

    /**
     * Recovery logic: if last failure was > 1 hour ago, recover 10 points per hour elapsed.
     */
    fun getUpdatedHealth(currentTime: Long = System.currentTimeMillis()): ProviderHealthScore {
        if (healthScore >= 100 || lastFailureTime <= 0L) return this
        val timeDiff = currentTime - lastFailureTime
        if (timeDiff > 3600_000L) {
            val hoursElapsed = timeDiff / 3600_000L
            val recoveryPoints = (hoursElapsed * 10).toInt()
            val newScore = (healthScore + recoveryPoints).coerceAtMost(100)
            val newFailureCount = if (newScore == 100) 0 else failureCount
            val newTimeoutCount = if (newScore == 100) 0 else timeoutCount
            return this.copy(
                healthScore = newScore,
                failureCount = newFailureCount,
                timeoutCount = newTimeoutCount
            )
        }
        return this
    }
}

/**
 * Representation of a source candidate for auto-fallback, paired with its health state.
 */
data class SourceFallbackCandidate(
    val source: PlaybackSource,
    val healthScore: Int,
    val reliabilityTier: SourceReliabilityTier
)

/** Hex lookup table for fast byte→hex conversion (PERF: avoids String.format per byte). */
private val HEX_CHARS = "0123456789abcdef".toCharArray()

/** Fast hex encoding without String.format overhead. */
private fun ByteArray.toHexString(): String {
    val result = CharArray(size * 2)
    for (i in indices) {
        val v = this[i].toInt() and 0xFF
        result[i * 2] = HEX_CHARS[v ushr 4]
        result[i * 2 + 1] = HEX_CHARS[v and 0x0F]
    }
    return String(result)
}

/**
 * Utility to generate a safe, privacy-preserving source identifier from an arbitrary URL.
 * Ensures raw URLs, secret tokens, or credentials are never exposed in logs or UI models.
 */
fun generateSafeSourceId(url: String): String {
    return try {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(url.toByteArray(Charsets.UTF_8))
        hashBytes.toHexString().take(16)
    } catch (e: Exception) {
        "safe-id-${url.hashCode()}"
    }
}
