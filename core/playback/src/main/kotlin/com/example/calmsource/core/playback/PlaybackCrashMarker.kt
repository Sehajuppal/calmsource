package com.example.calmsource.core.playback

import android.content.Context
import android.util.Log
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.calmsource.core.model.PlaybackSource
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit


private val Context.playbackCrashMarkerStore by preferencesDataStore(
    name = "playback_crash_marker"
)

data class PlaybackCrashMarkerRecord(
    val sessionId: String,
    val sourceId: String,
    val providerId: String,
    val startedAtMs: Long,
    val mediaUrlHash: String,
    val processCrashed: Boolean = false,
    val processCrashedAtMs: Long? = null
)

data class PlaybackCrashRecovery(
    val sourceIdToSkip: String,
    val providerId: String,
    val mediaUrlHash: String,
    val crashedAtMs: Long,
    val ageMs: Long
)

object PlaybackCrashMarker {
    const val MIN_CRASH_MARKER_AGE_MS = 10_000L
    const val MAX_CRASH_MARKER_AGE_MS = 5 * 60_000L

    private val markerScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val installLock = Any()
    @Volatile private var installedHandler: Thread.UncaughtExceptionHandler? = null
    private val processCrashedMarked = java.util.concurrent.atomic.AtomicBoolean(false)

    fun markStartedBestEffort(
        context: Context,
        source: PlaybackSource,
        nowMs: Long = System.currentTimeMillis(),
        sessionId: String = UUID.randomUUID().toString()
    ): PlaybackCrashMarkerRecord {
        val record = recordFor(source, nowMs, sessionId)
        val appContext = context.applicationContext ?: context
        markerScope.launch {
            runCatching { writeRecord(appContext, record) }
                .onFailure { logWarning("Failed to write playback crash marker", it) }
        }
        return record
    }

    fun clearBestEffort(context: Context, sessionId: String? = null) {
        val appContext = context.applicationContext ?: context
        markerScope.launch {
            runCatching {
                if (sessionId != null) {
                    val current = readRecord(appContext)
                    if (current?.sessionId == sessionId) {
                        clear(appContext)
                    }
                } else {
                    clear(appContext)
                }
            }
                .onFailure { logWarning("Failed to clear playback crash marker", it) }
        }
    }

    /**
     * Non-blocking async clear of the crash marker. Safe to call from the main thread.
     */
    fun clearAsync(context: Context, sessionId: String? = null) {
        val appContext = context.applicationContext ?: context
        markerScope.launch {
            runCatching {
                if (sessionId != null) {
                    val current = readRecord(appContext)
                    if (current?.sessionId == sessionId) {
                        clear(appContext)
                    }
                } else {
                    clear(appContext)
                }
            }.onFailure { logWarning("Failed to clear playback crash marker", it) }
        }
    }

    @Deprecated("Use clearAsync to avoid blocking the main thread", replaceWith = ReplaceWith("clearAsync(context, sessionId)"))
    fun clearBlocking(context: Context, sessionId: String? = null) {
        clearAsync(context, sessionId)
    }

    /**
     * Best-effort write of the process-crashed flag. Called from the crash handler
     * thread. Uses [markerScope] to dispatch the DataStore write, then waits on a
     * latch with a deadline so the crash handler thread never enters a coroutine
     * context. If DataStore I/O hangs, the latch times out and the crash propagates
     * unblocked.
     */
    fun markProcessCrashedBestEffort(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ) {
        if (!processCrashedMarked.compareAndSet(false, true)) return
        val appContext = context.applicationContext ?: context
        val latch = CountDownLatch(1)
        markerScope.launch {
            try {
                runCatching {
                    appContext.playbackCrashMarkerStore.edit { preferences ->
                        preferences[KEY_PROCESS_CRASHED] = true
                        preferences[KEY_PROCESS_CRASHED_AT_MS] = nowMs
                    }
                }.onFailure { throwable ->
                    logWarning("Failed to write process crash marker", throwable)
                }
            } finally {
                latch.countDown()
            }
        }
        latch.await(RUN_BLOCKING_TIMEOUT_MS, TimeUnit.MILLISECONDS)
    }

    fun installGlobalUncaughtHandler(context: Context) {
        val appContext = context.applicationContext ?: context
        synchronized(installLock) {
            val currentHandler = Thread.getDefaultUncaughtExceptionHandler()
            if (currentHandler === installedHandler) return
            val previousHandler = currentHandler
            val markerHandler = Thread.UncaughtExceptionHandler { thread, throwable ->
                markProcessCrashedBestEffort(appContext)
                if (previousHandler != null) {
                    previousHandler.uncaughtException(thread, throwable)
                } else {
                    throw throwable
                }
            }
            Thread.setDefaultUncaughtExceptionHandler(markerHandler)
            installedHandler = markerHandler
        }
    }

    suspend fun readRecord(context: Context): PlaybackCrashMarkerRecord? {
        return context.playbackCrashMarkerStore.data.first().toRecord()
    }

    suspend fun consumeRecoveryMarker(
        context: Context,
        nowMs: Long = System.currentTimeMillis()
    ): PlaybackCrashRecovery? {
        val data = context.playbackCrashMarkerStore.data.first()
        val record = data.toRecord()
        if (record == null) {
            if (data.contains(KEY_PROCESS_CRASHED) || data.contains(KEY_PROCESS_CRASHED_AT_MS)) {
                clear(context)
            }
            return null
        }
        val recovery = recoveryFor(record, nowMs)
        if (recovery != null || (nowMs - record.startedAtMs > MAX_CRASH_MARKER_AGE_MS)) {
            clearSession(context, record.sessionId)
        }
        return recovery
    }

    suspend fun clear(context: Context) {
        context.playbackCrashMarkerStore.edit { preferences ->
            preferences.remove(KEY_SESSION_ID)
            preferences.remove(KEY_SOURCE_ID)
            preferences.remove(KEY_PROVIDER_ID)
            preferences.remove(KEY_STARTED_AT_MS)
            preferences.remove(KEY_MEDIA_URL_HASH)
            preferences.remove(KEY_PROCESS_CRASHED)
            preferences.remove(KEY_PROCESS_CRASHED_AT_MS)
        }
    }

    suspend fun clearSession(context: Context, sessionId: String) {
        context.playbackCrashMarkerStore.edit { preferences ->
            if (preferences[KEY_SESSION_ID] == sessionId) {
                preferences.remove(KEY_SESSION_ID)
                preferences.remove(KEY_SOURCE_ID)
                preferences.remove(KEY_PROVIDER_ID)
                preferences.remove(KEY_STARTED_AT_MS)
                preferences.remove(KEY_MEDIA_URL_HASH)
                preferences.remove(KEY_PROCESS_CRASHED)
                preferences.remove(KEY_PROCESS_CRASHED_AT_MS)
            }
        }
    }

    fun recoveryFor(
        record: PlaybackCrashMarkerRecord,
        nowMs: Long
    ): PlaybackCrashRecovery? {
        if (!record.processCrashed) return null
        val ageMs = nowMs - record.startedAtMs
        if (ageMs < MIN_CRASH_MARKER_AGE_MS) return null
        if (ageMs > MAX_CRASH_MARKER_AGE_MS) return null
        return PlaybackCrashRecovery(
            sourceIdToSkip = record.sourceId,
            providerId = record.providerId,
            mediaUrlHash = record.mediaUrlHash,
            crashedAtMs = record.processCrashedAtMs ?: record.startedAtMs,
            ageMs = ageMs
        )
    }

    fun matchesRecovery(
        source: PlaybackSource,
        recovery: PlaybackCrashRecovery
    ): Boolean {
        if (source.safeSourceId != recovery.sourceIdToSkip) return false
        val sourceHash = mediaUrlHash(source.rawUrl)
        return sourceHash == recovery.mediaUrlHash
    }

    fun recordFor(
        source: PlaybackSource,
        nowMs: Long,
        sessionId: String = UUID.randomUUID().toString()
    ): PlaybackCrashMarkerRecord {
        return PlaybackCrashMarkerRecord(
            sessionId = sessionId,
            sourceId = source.safeSourceId,
            providerId = providerIdFor(source),
            startedAtMs = nowMs,
            mediaUrlHash = mediaUrlHash(source.rawUrl)
        )
    }

    fun mediaUrlHash(rawUrl: String): String {
        return MessageDigest
            .getInstance("SHA-256")
            .digest(rawUrl.toByteArray(Charsets.UTF_8))
            .take(HASH_BYTES)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    fun providerIdFor(source: PlaybackSource): String {
        return source.resolveProviderIdForHealth()
    }

    private fun logWarning(message: String, throwable: Throwable) {
        runCatching { Log.w(TAG, message, throwable) }
    }

    private suspend fun writeRecord(
        context: Context,
        record: PlaybackCrashMarkerRecord
    ) {
        context.playbackCrashMarkerStore.edit { preferences ->
            preferences[KEY_SESSION_ID] = record.sessionId
            preferences[KEY_SOURCE_ID] = record.sourceId
            preferences[KEY_PROVIDER_ID] = record.providerId
            preferences[KEY_STARTED_AT_MS] = record.startedAtMs
            preferences[KEY_MEDIA_URL_HASH] = record.mediaUrlHash
            preferences[KEY_PROCESS_CRASHED] = record.processCrashed
            record.processCrashedAtMs?.let { preferences[KEY_PROCESS_CRASHED_AT_MS] = it }
                ?: preferences.remove(KEY_PROCESS_CRASHED_AT_MS)
        }
    }

    private fun Preferences.toRecord(): PlaybackCrashMarkerRecord? {
        val sessionId = this[KEY_SESSION_ID] ?: return null
        val sourceId = this[KEY_SOURCE_ID] ?: return null
        val providerId = this[KEY_PROVIDER_ID] ?: return null
        val startedAtMs = this[KEY_STARTED_AT_MS] ?: return null
        val mediaUrlHash = this[KEY_MEDIA_URL_HASH] ?: return null
        return PlaybackCrashMarkerRecord(
            sessionId = sessionId,
            sourceId = sourceId,
            providerId = providerId,
            startedAtMs = startedAtMs,
            mediaUrlHash = mediaUrlHash,
            processCrashed = this[KEY_PROCESS_CRASHED] == true,
            processCrashedAtMs = this[KEY_PROCESS_CRASHED_AT_MS]
        )
    }

    private val KEY_SESSION_ID = stringPreferencesKey("session_id")
    private val KEY_SOURCE_ID = stringPreferencesKey("source_id")
    private val KEY_PROVIDER_ID = stringPreferencesKey("provider_id")
    private val KEY_STARTED_AT_MS = longPreferencesKey("started_at_ms")
    private val KEY_MEDIA_URL_HASH = stringPreferencesKey("media_url_hash")
    private val KEY_PROCESS_CRASHED = booleanPreferencesKey("process_crashed")
    private val KEY_PROCESS_CRASHED_AT_MS = longPreferencesKey("process_crashed_at_ms")

    private const val HASH_BYTES = 16
    private const val RUN_BLOCKING_TIMEOUT_MS = 2_000L
    private const val TAG = "PlaybackCrashMarker"
}
