package com.example.calmsource.core.playback

import android.util.Log
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.UserPreferences
import android.content.Context
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

/**
 * Global fallback policy preference.
 *
 * Default is [AutoFallbackPolicy.AUTO_FALLBACK_LIMITED] — silently falls back
 * through stream candidates until a playable stream is found.
 */
object FallbackPreferences {
    private const val PREFS_NAME = "fallback_preferences"
    private const val KEY_POLICY = "fallback_policy"
    private const val KEY_DECODER_FALLBACK = "decoder_fallback"

    @Volatile
    var policy: AutoFallbackPolicy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED

    @Volatile
    var enableFallbackSafeProfileOnDecoderError: Boolean = false

    fun warmBestEffort(context: Context) {
        try {
            val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val name = prefs.getString(KEY_POLICY, AutoFallbackPolicy.AUTO_FALLBACK_LIMITED.name)
            policy = try {
                AutoFallbackPolicy.valueOf(name ?: AutoFallbackPolicy.AUTO_FALLBACK_LIMITED.name)
            } catch (_: Exception) {
                AutoFallbackPolicy.AUTO_FALLBACK_LIMITED
            }
            enableFallbackSafeProfileOnDecoderError = prefs.getBoolean(KEY_DECODER_FALLBACK, false)
        } catch (_: Exception) {
            // Best-effort
        }
    }

    fun setPolicyAndPersist(context: Context, newPolicy: AutoFallbackPolicy) {
        policy = newPolicy
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_POLICY, newPolicy.name)
                .apply()
        } catch (_: Exception) {
            // Best-effort
        }
    }

    fun setDecoderFallbackAndPersist(context: Context, enabled: Boolean) {
        enableFallbackSafeProfileOnDecoderError = enabled
        try {
            context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_DECODER_FALLBACK, enabled)
                .apply()
        } catch (_: Exception) {
            // Best-effort
        }
    }
}

val UserPreferences.autoFallbackPolicy: AutoFallbackPolicy
    get() = FallbackPreferences.policy

class FallbackManager {
    private val failedSourceIds = mutableSetOf<String>()
    private val blockedCodecs = mutableSetOf<String>()
    @Volatile private var candidates = listOf<PlaybackSource>()
    private var attemptCount = 0

    @Synchronized
    fun reset(newCandidates: List<PlaybackSource>) {
        candidates = newCandidates
        failedSourceIds.clear()
        blockedCodecs.clear()
        attemptCount = 0
    }

    private fun codecMatches(codec: String, candidate: PlaybackSource): Boolean {
        val clean = codec.lowercase().trim()
        if (clean.isEmpty()) return false
        val matchKeywords = when {
            clean.contains("hevc") || clean.contains("h265") || clean.contains("h.265") || clean.contains("video/hevc") -> listOf("hevc", "h265", "h.265")
            clean.contains("h264") || clean.contains("h.264") || clean.contains("avc") || clean.contains("video/avc") -> listOf("h264", "h.264", "avc")
            clean.contains("vp9") || clean.contains("video/x-vnd.on2.vp9") -> listOf("vp9")
            clean.contains("av1") || clean.contains("video/av01") -> listOf("av1")
            else -> listOf(clean)
        }
        val vCodec = candidate.metadata?.videoCodec?.lowercase()?.trim() ?: ""
        val aCodec = candidate.metadata?.audioCodec?.lowercase()?.trim() ?: ""
        return matchKeywords.any { kw ->
            (vCodec.isNotEmpty() && vCodec.contains(kw)) ||
            (aCodec.isNotEmpty() && aCodec.contains(kw)) ||
            (vCodec.isNotEmpty() && kw.contains(vCodec)) ||
            (aCodec.isNotEmpty() && kw.contains(aCodec))
        }
    }

    @Synchronized
    fun pruneCandidatesByCodec(codec: String) {
        val clean = codec.lowercase().trim()
        blockedCodecs.add(clean)
        candidates.forEach { candidate ->
            if (codecMatches(clean, candidate)) {
                failedSourceIds.add(candidate.id)
            }
        }
    }

    @Synchronized
    fun markFailed(sourceId: String) {
        failedSourceIds.add(sourceId)
    }

    @Synchronized
    fun incrementAttempts() {
        attemptCount++
    }

    @Synchronized
    fun getAttemptCount(): Int = attemptCount

    @Synchronized
    fun getFailedSources(): Set<String> = failedSourceIds.toSet()

    @Synchronized
    fun getBlockedCodecs(): Set<String> = blockedCodecs.toSet()

    @Synchronized
    fun getRemainingCandidateIds(): List<String> = getRemainingCandidates().map { it.id }

    @Synchronized
    fun getRemainingCandidates(): List<PlaybackSource> = candidates
        .filter { candidate ->
            if (candidate.id in failedSourceIds) return@filter false
            val matchesBlocked = blockedCodecs.any { blocked ->
                codecMatches(blocked, candidate)
            }
            !matchesBlocked
        }

    @Synchronized
    fun isFallbackAllowed(policy: AutoFallbackPolicy): Boolean {
        val hasRemaining = getRemainingCandidates().isNotEmpty()
        if (!hasRemaining) return false

        return when (policy) {
            AutoFallbackPolicy.OFF -> false
            AutoFallbackPolicy.ASK_BEFORE_FALLBACK -> true
            AutoFallbackPolicy.AUTO_FALLBACK_ONCE -> attemptCount < 1
            AutoFallbackPolicy.AUTO_FALLBACK_LIMITED -> {
                val limit = minOf(candidates.size, 5)
                attemptCount < limit
            }
        }
    }

    suspend fun selectNextBestCandidate(): PlaybackSource? {
        val remaining = synchronized(this) {
            getRemainingCandidates()
        }
        if (remaining.isEmpty()) return null
        if (remaining.size == 1) return remaining.first()

        val sorted = coroutineScope {
            remaining.map { source ->
                async(SourceHealthRepository.dispatcher) {
                    val health = runCatching {
                        SourceHealthRepository.getSourceHealth(sourceId = source.safeSourceId, readonly = true)
                    }.getOrNull()
                    val score = health?.healthScore ?: 100
                    source to score
                }
            }.awaitAll()
        }.sortedByDescending { it.second }

        return sorted.firstOrNull()?.first
    }
}
