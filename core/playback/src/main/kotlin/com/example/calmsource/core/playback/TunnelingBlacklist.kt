package com.example.calmsource.core.playback

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.calmsource.core.model.PlaybackSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

private val Context.tunnelingBlacklistStore by preferencesDataStore(
    name = "playback_tunneling_blacklist"
)

private val Context.tunnelingSettingsStore by preferencesDataStore(
    name = "playback_tunneling_settings"
)

enum class TunnelingMode {
    OFF,
    AUTO,
    ON
}

object TunnelingPreferences {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val modeKey = stringPreferencesKey("tunneling_mode")

    @Volatile
    var mode: TunnelingMode = TunnelingMode.OFF

    /** Bumped on every [setModeBestEffort] so in-flight warm loads cannot clobber newer writes. */
    @Volatile
    private var modeGeneration: Long = 0L

    fun warmBestEffort(context: Context) {
        val generationAtStart = modeGeneration
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                val loaded = readMode(appContext)
                if (generationAtStart == modeGeneration) {
                    mode = loaded
                }
            }.onFailure { throwable ->
                runCatching { Log.w("TunnelingPreferences", "Failed to load tunneling mode", throwable) }
            }
        }
    }

    fun setModeBestEffort(
        context: Context,
        newMode: TunnelingMode
    ) {
        modeGeneration++
        mode = newMode
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching {
                appContext.tunnelingSettingsStore.edit { preferences ->
                    preferences[modeKey] = newMode.name
                }
            }.onFailure { throwable ->
                runCatching { Log.w("TunnelingPreferences", "Failed to save tunneling mode", throwable) }
            }
        }
    }

    suspend fun readMode(context: Context): TunnelingMode {
        val value = context.tunnelingSettingsStore.data.first()[modeKey]
        return modeFromStorage(value)
    }

    fun modeFromStorage(value: String?): TunnelingMode {
        return TunnelingMode.entries.firstOrNull { it.name == value } ?: TunnelingMode.OFF
    }
}

data class TunnelingBlacklistKey(
    val deviceModel: String,
    val audioCodec: String,
    val videoCodec: String
) {
    val storageKey: String = listOf(deviceModel, audioCodec, videoCodec)
        .joinToString(separator = "~") { part -> sanitizePart(part) }

    companion object {
        private val UNSAFE_CHARS = Regex("[^a-z0-9._-]")

        fun sanitizePart(value: String): String {
            val sanitized = value
                .trim()
                .lowercase()
                .replace(UNSAFE_CHARS, "_")
                .replace(Regex("_+"), "_")
                .trim('_')
            return if (sanitized.isBlank()) "unknown" else sanitized
        }
    }
}

data class TunnelingBlacklistEntry(
    val key: TunnelingBlacklistKey,
    val failureCount: Int,
    val lastFailureAtMs: Long
)

object TunnelingBlacklist {
    const val FAILURE_THRESHOLD = 2

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val entriesKey = stringSetPreferencesKey("entries")
    private val memoryLock = Any()
    @Volatile private var loaded = false
    private var memoryEntries: Map<String, TunnelingBlacklistEntry> = emptyMap()

    fun keyFor(
        source: PlaybackSource,
        deviceModel: String = Build.MODEL
    ): TunnelingBlacklistKey? {
        val audioCodec = source.metadata?.audioCodec?.takeIf { it.isNotBlank() } ?: return null
        val videoCodec = source.metadata?.videoCodec?.takeIf { it.isNotBlank() } ?: return null
        return keyFor(
            deviceModel = deviceModel,
            audioCodec = audioCodec,
            videoCodec = videoCodec
        )
    }

    fun keyFor(
        deviceModel: String,
        audioCodec: String,
        videoCodec: String
    ): TunnelingBlacklistKey {
        return TunnelingBlacklistKey(
            deviceModel = deviceModel,
            audioCodec = audioCodec,
            videoCodec = videoCodec
        )
    }

    fun isBlacklisted(entry: TunnelingBlacklistEntry?): Boolean {
        return (entry?.failureCount ?: 0) >= FAILURE_THRESHOLD
    }

    fun recordFailure(
        entries: Map<String, TunnelingBlacklistEntry>,
        key: TunnelingBlacklistKey,
        nowMs: Long
    ): Map<String, TunnelingBlacklistEntry> {
        val current = entries[key.storageKey]
        val updated = TunnelingBlacklistEntry(
            key = key,
            failureCount = ((current?.failureCount ?: 0) + 1).coerceAtMost(Int.MAX_VALUE),
            lastFailureAtMs = nowMs
        )
        return entries + (key.storageKey to updated)
    }

    fun snapshot(): Map<String, TunnelingBlacklistEntry> {
        return synchronized(memoryLock) { memoryEntries.toMap() }
    }

    fun warmBestEffort(context: Context) {
        if (loaded) return
        val appContext = context.applicationContext ?: context
        scope.launch {
            runCatching { loadIntoMemory(appContext) }
                .onFailure { logWarning("Failed to load tunneling blacklist", it) }
        }
    }

    fun warmBlocking(context: Context) {
        if (loaded) return
        runCatching {
            kotlinx.coroutines.runBlocking {
                loadIntoMemory(context.applicationContext ?: context)
            }
        }.onFailure { logWarning("Failed to load tunneling blacklist (blocking)", it) }
    }

    fun isBlacklistedBestEffort(
        context: Context,
        key: TunnelingBlacklistKey
    ): Boolean {
        if (!loaded) {
            warmBestEffort(context.applicationContext ?: context)
        }
        return isBlacklisted(snapshot()[key.storageKey])
    }

    fun recordFailureBestEffort(
        context: Context,
        key: TunnelingBlacklistKey,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val appContext = context.applicationContext ?: context
        synchronized(memoryLock) {
            memoryEntries = recordFailure(memoryEntries, key, nowMs)
            loaded = true
        }
        scope.launch {
            runCatching {
                appContext.tunnelingBlacklistStore.edit { preferences ->
                    val current = preferences[entriesKey]
                        ?.let(::decodeEntries)
                        .orEmpty()
                    val updated = recordFailure(current, key, nowMs)
                    preferences[entriesKey] = encodeEntries(updated)
                    synchronized(memoryLock) {
                        val merged = memoryEntries.toMutableMap()
                        updated.forEach { (k, diskEntry) ->
                            val memEntry = merged[k]
                            if (memEntry == null) {
                                merged[k] = diskEntry
                            } else {
                                merged[k] = memEntry.copy(
                                    failureCount = maxOf(memEntry.failureCount, diskEntry.failureCount),
                                    lastFailureAtMs = maxOf(memEntry.lastFailureAtMs, diskEntry.lastFailureAtMs)
                                )
                            }
                        }
                        memoryEntries = merged
                        loaded = true
                    }
                }
            }.onFailure { logWarning("Failed to record tunneling failure", it) }
        }
    }

    suspend fun readEntries(context: Context): Map<String, TunnelingBlacklistEntry> {
        return context.tunnelingBlacklistStore.data.first()[entriesKey]
            ?.let(::decodeEntries)
            .orEmpty()
    }

    suspend fun clear(context: Context) {
        context.tunnelingBlacklistStore.edit { preferences ->
            preferences.remove(entriesKey)
        }
        synchronized(memoryLock) {
            memoryEntries = emptyMap()
            loaded = true
        }
    }

    fun encodeEntries(entries: Map<String, TunnelingBlacklistEntry>): Set<String> {
        return entries.values.map { entry ->
            listOf(
                TunnelingBlacklistKey.sanitizePart(entry.key.deviceModel),
                TunnelingBlacklistKey.sanitizePart(entry.key.audioCodec),
                TunnelingBlacklistKey.sanitizePart(entry.key.videoCodec),
                entry.failureCount.toString(),
                entry.lastFailureAtMs.toString()
            ).joinToString(separator = FIELD_SEPARATOR)
        }.toSet()
    }

    fun decodeEntries(encoded: Set<String>): Map<String, TunnelingBlacklistEntry> {
        return encoded.mapNotNull { row ->
            val parts = row.split(FIELD_SEPARATOR)
            if (parts.size != 5) return@mapNotNull null
            val failureCount = parts[3].toIntOrNull() ?: return@mapNotNull null
            val lastFailureAtMs = parts[4].toLongOrNull() ?: return@mapNotNull null
            val key = keyFor(
                deviceModel = parts[0],
                audioCodec = parts[1],
                videoCodec = parts[2]
            )
            key.storageKey to TunnelingBlacklistEntry(
                key = key,
                failureCount = failureCount,
                lastFailureAtMs = lastFailureAtMs
            )
        }.toMap()
    }

    private suspend fun loadIntoMemory(context: Context) {
        val loadedEntries = readEntries(context)
        synchronized(memoryLock) {
            memoryEntries = loadedEntries
            loaded = true
        }
    }

    private fun logWarning(message: String, throwable: Throwable) {
        runCatching { Log.w(TAG, message, throwable) }
    }

    private const val FIELD_SEPARATOR = "|"
    private const val TAG = "TunnelingBlacklist"
}
