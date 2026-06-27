package com.example.calmsource.core.playback

import android.app.ActivityManager
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LivePlaybackSpeedControl
import com.example.calmsource.core.model.PlaybackSource

enum class PlaybackProfileKind {
    VOD_PROFILE,
    LIVE_IPTV_PROFILE,
    LOW_MEMORY_PROFILE,
    FALLBACK_SAFE_PROFILE
}

data class PlaybackDeviceProfile(
    val memoryClassMb: Int = 256,
    val lowRamDevice: Boolean = false
)

data class PlaybackProfileHistory(
    val useFallbackSafeProfile: Boolean = false
)

data class PlaybackResourceProfile(
    val kind: PlaybackProfileKind,
    val isLive: Boolean,
    val lowMemoryMode: Boolean,
    val memoryClassMb: Int,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val bufferForPlaybackMs: Int,
    val bufferForPlaybackAfterRebufferMs: Int,
    val targetBufferBytes: Int,
    val maxVideoHeight: Int? = null,
    val tunnelingEnabled: Boolean = true,
    val liveTargetOffsetMs: Long? = null,
    val liveMaxPlaybackSpeed: Float? = null
) {
    val compatibilityKey: String
        get() = "${kind.name}:${if (isLive) "live" else "vod"}:$lowMemoryMode"
}

@OptIn(UnstableApi::class)
object PlaybackProfileManager {
    fun profileFor(
        context: Context,
        source: PlaybackSource?,
        history: PlaybackProfileHistory = PlaybackProfileHistory()
    ): PlaybackResourceProfile {
        return profileFor(
            source = source,
            deviceProfile = deviceProfile(context),
            history = history
        )
    }

    fun profileFor(
        context: Context,
        isLive: Boolean,
        history: PlaybackProfileHistory = PlaybackProfileHistory()
    ): PlaybackResourceProfile {
        return profileFor(
            isLive = isLive,
            deviceProfile = deviceProfile(context),
            history = history
        )
    }

    fun profileFor(
        source: PlaybackSource?,
        deviceProfile: PlaybackDeviceProfile,
        history: PlaybackProfileHistory = PlaybackProfileHistory()
    ): PlaybackResourceProfile {
        return profileFor(
            isLive = source?.metadata?.isLive == true,
            deviceProfile = deviceProfile,
            history = history
        )
    }

    fun profileFor(
        isLive: Boolean,
        deviceProfile: PlaybackDeviceProfile,
        history: PlaybackProfileHistory = PlaybackProfileHistory()
    ): PlaybackResourceProfile {
        val memoryClassMb = deviceProfile.memoryClassMb.coerceAtLeast(1)
        val lowMemoryMode = deviceProfile.lowRamDevice || memoryClassMb <= LOW_MEMORY_CLASS_MB

        return when {
            history.useFallbackSafeProfile -> PlaybackResourceProfile(
                kind = PlaybackProfileKind.FALLBACK_SAFE_PROFILE,
                isLive = isLive,
                lowMemoryMode = lowMemoryMode,
                memoryClassMb = memoryClassMb,
                minBufferMs = 12_000,
                maxBufferMs = 24_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
                targetBufferBytes = constrainedTargetBufferBytes(memoryClassMb),
                maxVideoHeight = 720,
                tunnelingEnabled = false
            )
            lowMemoryMode -> PlaybackResourceProfile(
                kind = PlaybackProfileKind.LOW_MEMORY_PROFILE,
                isLive = isLive,
                lowMemoryMode = true,
                memoryClassMb = memoryClassMb,
                minBufferMs = 12_000,
                maxBufferMs = 36_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
                targetBufferBytes = constrainedTargetBufferBytes(memoryClassMb)
            )
            isLive -> PlaybackResourceProfile(
                kind = PlaybackProfileKind.LIVE_IPTV_PROFILE,
                isLive = true,
                lowMemoryMode = false,
                memoryClassMb = memoryClassMb,
                minBufferMs = 8_000,
                maxBufferMs = 24_000,
                bufferForPlaybackMs = 1_000,
                bufferForPlaybackAfterRebufferMs = 2_000,
                targetBufferBytes = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES,
                liveTargetOffsetMs = LIVE_TARGET_OFFSET_MS,
                liveMaxPlaybackSpeed = LIVE_MAX_PLAYBACK_SPEED
            )
            else -> PlaybackResourceProfile(
                kind = PlaybackProfileKind.VOD_PROFILE,
                isLive = false,
                lowMemoryMode = false,
                memoryClassMb = memoryClassMb,
                minBufferMs = 24_000,
                maxBufferMs = 72_000,
                bufferForPlaybackMs = 1_500,
                bufferForPlaybackAfterRebufferMs = 3_000,
                targetBufferBytes = DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES
            )
        }
    }

    fun deviceProfile(context: Context): PlaybackDeviceProfile {
        val activityManager = runCatching {
            context.applicationContext.getSystemService(ActivityManager::class.java)
        }.getOrNull()
        return PlaybackDeviceProfile(
            memoryClassMb = activityManager?.memoryClass ?: DEFAULT_MEMORY_CLASS_MB,
            lowRamDevice = activityManager?.isLowRamDevice == true
        )
    }

    fun loadControl(profile: PlaybackResourceProfile): DefaultLoadControl {
        return DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                profile.minBufferMs,
                profile.maxBufferMs,
                profile.bufferForPlaybackMs,
                profile.bufferForPlaybackAfterRebufferMs
            )
            .setTargetBufferBytes(profile.targetBufferBytes)
            .build()
    }

    fun livePlaybackSpeedControl(profile: PlaybackResourceProfile): LivePlaybackSpeedControl? {
        val maxPlaybackSpeed = profile.liveMaxPlaybackSpeed ?: return null
        return DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMaxPlaybackSpeed(maxPlaybackSpeed)
            .build()
    }

    fun liveConfiguration(profile: PlaybackResourceProfile): MediaItem.LiveConfiguration? {
        val targetOffsetMs = profile.liveTargetOffsetMs
        val maxPlaybackSpeed = profile.liveMaxPlaybackSpeed
        if (targetOffsetMs == null && maxPlaybackSpeed == null) return null

        val builder = MediaItem.LiveConfiguration.Builder()
        targetOffsetMs?.let { builder.setTargetOffsetMs(it) }
        maxPlaybackSpeed?.let { builder.setMaxPlaybackSpeed(it) }
        return builder.build()
    }

    fun logProfile(profile: PlaybackResourceProfile) {
        try {
            Log.i(
                "PlaybackProfileManager",
                "profile=${profile.compatibilityKey} memoryClassMb=${profile.memoryClassMb} " +
                    "bufferMs=${profile.minBufferMs}-${profile.maxBufferMs} " +
                    "targetBufferBytes=${profile.targetBufferBytes}"
            )
        } catch (_: Throwable) {
            // Local JVM tests use Android stubs.
        }
    }

    fun constrainedTargetBufferBytes(memoryClassMb: Int): Int {
        val maxTargetBufferMb = if (memoryClassMb <= LOW_MEMORY_CLASS_MB) 16 else MAX_TARGET_BUFFER_MB
        val budgetMb = (memoryClassMb.coerceAtLeast(1) / 4)
            .coerceAtLeast(1)
            .coerceAtMost(maxTargetBufferMb)
        return budgetMb * BYTES_PER_MB
    }

    const val LOW_MEMORY_CLASS_MB = 256
    const val LIVE_TARGET_OFFSET_MS = 2_000L
    const val LIVE_MAX_PLAYBACK_SPEED = 1.05f

    private const val DEFAULT_MEMORY_CLASS_MB = 256
    private const val MAX_TARGET_BUFFER_MB = 64
    private const val BYTES_PER_MB = 1024 * 1024
}
