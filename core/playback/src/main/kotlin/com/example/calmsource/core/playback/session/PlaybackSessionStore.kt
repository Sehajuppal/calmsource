package com.example.calmsource.core.playback.session

import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

/**
 * Thread-safe store for the active [PlaybackSession]. Bumping the session id invalidates
 * in-flight stream races, fallback jobs, and other async prepare work.
 */
internal class PlaybackSessionStore {

    private val sessionId = AtomicLong(0)
    private val activeSession = AtomicReference<PlaybackSession?>(null)

    fun currentId(): Long = sessionId.get()

    fun active(): PlaybackSession? = activeSession.get()

    fun begin(
        request: PlaybackRequest,
        candidates: List<PlaybackSource>,
        phase: SessionPhase,
    ): Long {
        val id = sessionId.incrementAndGet()
        activeSession.set(
            PlaybackSession(
                id = id,
                request = request,
                candidates = candidates,
                phase = phase,
            )
        )
        return id
    }

    fun updatePhase(phase: SessionPhase) {
        val current = activeSession.get() ?: return
        if (current.phase != phase) {
            activeSession.set(current.copy(phase = phase))
        }
    }

    /** Invalidates the current session without attaching a new one (e.g. [release]). */
    fun invalidate(): Long = sessionId.incrementAndGet().also { activeSession.set(null) }

    fun isCurrent(id: Long): Boolean = sessionId.get() == id
}
