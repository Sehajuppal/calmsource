package com.example.calmsource.core.playback

import android.content.Context
import android.util.Log
import coil.Coil
import coil.memory.MemoryCache
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImageCacheControllerState(
    val playbackTrimActive: Boolean = false,
    val nonCriticalRequestsPaused: Boolean = false,
    val cacheSizeBytes: Int = 0,
    val cacheMaxSizeBytes: Int = 0,
    val targetSizeBytes: Int = 0,
    val removedEntries: Int = 0,
    val protectedEntries: Int = 0,
    val restoreScheduledAtMs: Long? = null
)

data class ImageCacheTrimResult(
    val beforeSizeBytes: Int,
    val afterSizeBytes: Int,
    val maxSizeBytes: Int,
    val targetSizeBytes: Int,
    val removedEntries: Int,
    val protectedEntries: Int
)

object ImageCacheController {
    const val PLAYBACK_CACHE_FRACTION = 0.25
    const val RESTORE_DELAY_MS = 5_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val restoreGeneration = AtomicLong(0L)
    private val _state = MutableStateFlow(ImageCacheControllerState())
    private var restoreJob: Job? = null

    val state: StateFlow<ImageCacheControllerState> = _state.asStateFlow()

    fun trimForPlayback(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): ImageCacheControllerState {
        val generation = synchronized(this) {
            val gen = restoreGeneration.incrementAndGet()
            restoreJob?.cancel()
            restoreJob = null
            gen
        }

        val trimResult = runCatching {
            val appContext = context.applicationContext ?: context
            val memoryCache = Coil.imageLoader(appContext).memoryCache
            memoryCache?.let { trimMemoryCacheForPlayback(it) }
        }.onFailure { throwable ->
            logWarning("Failed to trim Coil memory cache for playback", throwable)
        }.getOrNull()

        val nextState = ImageCacheControllerState(
            playbackTrimActive = true,
            nonCriticalRequestsPaused = true,
            cacheSizeBytes = trimResult?.afterSizeBytes ?: 0,
            cacheMaxSizeBytes = trimResult?.maxSizeBytes ?: 0,
            targetSizeBytes = trimResult?.targetSizeBytes ?: 0,
            removedEntries = trimResult?.removedEntries ?: 0,
            protectedEntries = trimResult?.protectedEntries ?: 0,
            restoreScheduledAtMs = null
        )
        _state.value = nextState
        synchronized(this) {
            if (generation == Long.MIN_VALUE) {
                restoreGeneration.set(0L)
            }
        }
        return nextState
    }

    fun scheduleRestoreAfterPlayback(
        context: Context,
        delayMs: Long = RESTORE_DELAY_MS,
        nowMs: Long = System.currentTimeMillis()
    ) {
        synchronized(this) {
            val generation = restoreGeneration.incrementAndGet()
            restoreJob?.cancel()
            _state.update {
                it.copy(restoreScheduledAtMs = nowMs + delayMs)
            }
            restoreJob = scope.launch {
                delay(delayMs)
                if (synchronized(this@ImageCacheController) { restoreGeneration.get() } == generation) {
                    restoreAfterPlayback(context, generation)
                }
            }
        }
    }

    fun restoreAfterPlayback(context: Context, generation: Long = synchronized(this) { restoreGeneration.get() }): ImageCacheControllerState {
        synchronized(this) {
            if (restoreGeneration.get() != generation) {
                return state.value
            }
            restoreJob?.cancel()
            restoreJob = null
        }
        val cacheSnapshot = runCatching {
            val appContext = context.applicationContext ?: context
            Coil.imageLoader(appContext).memoryCache
        }.onFailure { throwable ->
            logWarning("Failed to read Coil memory cache after playback", throwable)
        }.getOrNull()

        val nextState = ImageCacheControllerState(
            playbackTrimActive = false,
            nonCriticalRequestsPaused = false,
            cacheSizeBytes = cacheSnapshot?.size ?: 0,
            cacheMaxSizeBytes = cacheSnapshot?.maxSize ?: 0,
            targetSizeBytes = playbackTargetSizeBytes(cacheSnapshot?.maxSize ?: 0),
            restoreScheduledAtMs = null
        )
        _state.update { current ->
            if (synchronized(this) { restoreGeneration.get() } == generation) nextState else current
        }
        return nextState
    }

    fun shouldAllowNonCriticalRequests(): Boolean {
        return !state.value.nonCriticalRequestsPaused
    }

    /**
     * Trims the Coil memory cache in response to a system memory-pressure callback
     * ([android.content.ComponentCallbacks2.onTrimMemory]). When [clearAll] is true the entire
     * memory cache is dropped (use for severe/background pressure); otherwise the cache is reduced
     * to the playback target size, preserving player-critical entries. Best-effort; never throws.
     */
    fun trimForMemoryPressure(context: Context, clearAll: Boolean) {
        runCatching {
            val appContext = context.applicationContext ?: context
            val memoryCache = Coil.imageLoader(appContext).memoryCache ?: return
            if (clearAll) {
                memoryCache.clear()
            } else {
                trimMemoryCacheForPlayback(memoryCache)
            }
        }.onFailure { throwable ->
            logWarning("Failed to trim Coil memory cache on memory pressure", throwable)
        }
    }

    fun trimMemoryCacheForPlayback(memoryCache: MemoryCache): ImageCacheTrimResult {
        val beforeSize = memoryCache.size
        val targetSize = playbackTargetSizeBytes(memoryCache.maxSize)
        var removedEntries = 0
        var protectedEntries = 0

        for (key in memoryCache.keys.toList()) {
            if (memoryCache.size <= targetSize) break
            if (isPlayerCriticalKey(key)) {
                protectedEntries++
                continue
            }
            if (memoryCache.remove(key)) {
                removedEntries++
            }
        }

        return ImageCacheTrimResult(
            beforeSizeBytes = beforeSize,
            afterSizeBytes = memoryCache.size,
            maxSizeBytes = memoryCache.maxSize,
            targetSizeBytes = targetSize,
            removedEntries = removedEntries,
            protectedEntries = protectedEntries
        )
    }

    fun playbackTargetSizeBytes(maxSizeBytes: Int): Int {
        if (maxSizeBytes <= 0) return 0
        return max(1, (maxSizeBytes * PLAYBACK_CACHE_FRACTION).toInt())
    }

    fun isPlayerCriticalKey(key: MemoryCache.Key): Boolean {
        val values = sequenceOf(key.key) + key.extras.asSequence().flatMap { (extraKey, extraValue) ->
            sequenceOf(extraKey, extraValue)
        }
        return values.any { value ->
            val normalized = value.lowercase()
            PROTECTED_KEY_MARKERS.any { marker -> normalized.contains(marker) }
        }
    }

    private fun logWarning(message: String, throwable: Throwable) {
        runCatching { Log.w(TAG, message, throwable) }
    }

    private val PROTECTED_KEY_MARKERS = listOf(
        "player-control",
        "player_controls",
        "playback-control",
        "playback_controls",
        "scrubber",
        "transport-control",
        "transport_controls"
    )

    private const val TAG = "ImageCacheController"
}
