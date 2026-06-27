package com.example.calmsource.core.playback.diagnostics

import com.example.calmsource.core.model.PlaybackRecoveryEvent
import com.example.calmsource.core.model.PlaybackSessionDiagnostics
import java.util.ArrayDeque

/**
 * In-memory ring buffer of sanitized playback recovery events.
 * Shared between the active [com.example.calmsource.core.playback.PlaybackManager]
 * and Advanced Debug screens so soak sessions can be inspected without logcat.
 */
object PlaybackDiagnosticsRecorder {
    private const val MAX_EVENTS = 32

    private val lock = Any()
    private val events = ArrayDeque<PlaybackRecoveryEvent>(MAX_EVENTS)

    @Volatile
    var lastSessionSnapshot: PlaybackSessionDiagnostics = PlaybackSessionDiagnostics()
        private set

    fun record(kind: String, detail: String, sourceId: String? = null, atMs: Long = System.currentTimeMillis()) {
        val event = PlaybackRecoveryEvent(
            atMs = atMs,
            kind = kind,
            detail = detail,
            sourceId = sourceId,
        )
        synchronized(lock) {
            if (events.size >= MAX_EVENTS) {
                events.removeFirst()
            }
            events.addLast(event)
        }
    }

    fun recentEvents(): List<PlaybackRecoveryEvent> = synchronized(lock) {
        events.toList()
    }

    fun updateSnapshot(snapshot: PlaybackSessionDiagnostics) {
        lastSessionSnapshot = snapshot.copy(recentEvents = recentEvents())
    }

    fun clearForTests() {
        synchronized(lock) {
            events.clear()
        }
        lastSessionSnapshot = PlaybackSessionDiagnostics()
    }
}
