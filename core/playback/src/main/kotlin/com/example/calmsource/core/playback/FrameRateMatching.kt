package com.example.calmsource.core.playback

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.media3.common.C
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.frameRateMatchingStore by preferencesDataStore(
    name = "playback_frame_rate_matching"
)

enum class FrameRateMatchingMode {
    OFF,
    SEAMLESS_ONLY
}

object FrameRateMatchingPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modeKey = stringPreferencesKey("frame_rate_matching_mode")

    @Volatile
    var mode: FrameRateMatchingMode = FrameRateMatchingMode.OFF

    /**
     * Shuts down the internal coroutine scope. Call from app-level teardown
     * to prevent a dangling [SupervisorJob] and IO thread from leaking.
     */
    fun shutdown() {
        scope.cancel()
    }

    fun warmBestEffort(context: Context) {
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                mode = readMode(appContext)
            }.onFailure { throwable ->
                runCatching {
                    Log.w("FrameRateMatching", "Failed to load frame-rate matching mode", throwable)
                }
            }
        }
    }

    fun setModeBestEffort(
        context: Context,
        newMode: FrameRateMatchingMode
    ) {
        mode = newMode
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                appContext.frameRateMatchingStore.edit { preferences ->
                    preferences[modeKey] = newMode.name
                }
            }.onFailure { throwable ->
                runCatching {
                    Log.w("FrameRateMatching", "Failed to save frame-rate matching mode", throwable)
                }
            }
        }
    }

    suspend fun readMode(context: Context): FrameRateMatchingMode {
        val value = context.frameRateMatchingStore.data.first()[modeKey]
        return modeFromStorage(value)
    }

    fun modeFromStorage(value: String?): FrameRateMatchingMode {
        return FrameRateMatchingMode.entries.firstOrNull { it.name == value }
            ?: FrameRateMatchingMode.OFF
    }
}

object FrameRateMatchingPolicy {
    fun media3Strategy(mode: FrameRateMatchingMode): Int {
        return when (mode) {
            FrameRateMatchingMode.OFF -> C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF
            FrameRateMatchingMode.SEAMLESS_ONLY ->
                C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS
        }
    }
}
