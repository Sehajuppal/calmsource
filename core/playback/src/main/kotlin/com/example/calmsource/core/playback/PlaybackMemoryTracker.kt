package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import kotlin.math.abs
import kotlin.math.max

internal sealed interface PlaybackMemoryWrite {
    data class VodCheckpoint(
        val reference: UserMemoryReference,
        val progressMs: Long,
        val durationMs: Long,
        val watchedAt: Long,
        val recordHistory: Boolean,
        val completed: Boolean
    ) : PlaybackMemoryWrite

    data class LiveStarted(
        val reference: UserMemoryReference,
        val watchedAt: Long
    ) : PlaybackMemoryWrite
}

/**
 * Decides what may be persisted for one playback session.
 *
 * Identity comes exclusively from PlaybackRequest.userMemoryReference. Source URLs and headers
 * are deliberately unavailable to the persistence actions emitted by this class.
 */
internal class PlaybackMemoryTracker(
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var session: Session? = null

    @Synchronized
    fun begin(request: PlaybackRequest) {
        val reference = request.userMemoryReference
        session = Session(
            reference = reference,
            isLive = reference?.contentType == UserMemoryContentType.LIVE_CHANNEL ||
                request.source.metadata?.isLive == true,
            startPositionMs = request.startPositionMs.coerceAtLeast(0L),
            metadataDurationMs = request.source.metadata?.durationMs?.coerceAtLeast(0L) ?: 0L
        )
    }

    @Synchronized
    fun onPlaying(positionMs: Long, durationMs: Long): List<PlaybackMemoryWrite> {
        val current = session ?: return emptyList()
        if (current.terminal) return emptyList()
        current.hasPlayed = true

        val reference = current.reference ?: return emptyList()
        if (!current.isLive || reference.contentType != UserMemoryContentType.LIVE_CHANNEL) {
            return emptyList()
        }
        if (current.liveRecorded) return emptyList()

        current.liveRecorded = true
        return listOf(PlaybackMemoryWrite.LiveStarted(reference, clock()))
    }

    @Synchronized
    fun onPeriodicCheckpoint(positionMs: Long, durationMs: Long): List<PlaybackMemoryWrite> {
        return checkpoint(positionMs, durationMs, force = false, ended = false)
    }

    @Synchronized
    fun onPause(positionMs: Long, durationMs: Long): List<PlaybackMemoryWrite> {
        return checkpoint(positionMs, durationMs, force = true, ended = false)
    }

    @Synchronized
    fun onEnded(positionMs: Long, durationMs: Long): List<PlaybackMemoryWrite> {
        val writes = checkpoint(positionMs, durationMs, force = true, ended = true)
        session?.terminal = true
        return writes
    }

    @Synchronized
    fun onRelease(positionMs: Long, durationMs: Long): List<PlaybackMemoryWrite> {
        val writes = checkpoint(positionMs, durationMs, force = true, ended = false)
        session?.terminal = true
        return writes
    }

    private fun checkpoint(
        positionMs: Long,
        durationMs: Long,
        force: Boolean,
        ended: Boolean
    ): List<PlaybackMemoryWrite> {
        val current = session ?: return emptyList()
        if (
            current.terminal ||
            current.completionRecorded ||
            !current.hasPlayed ||
            current.isLive
        ) {
            return emptyList()
        }

        val reference = current.reference ?: return emptyList()
        if (reference.contentType == UserMemoryContentType.LIVE_CHANNEL) return emptyList()

        val safeDuration = when {
            durationMs > 0L -> durationMs
            current.metadataDurationMs > 0L -> current.metadataDurationMs
            else -> 0L
        }
        var safePosition = max(positionMs.coerceAtLeast(0L), current.startPositionMs)
        if (safeDuration > 0L) {
            safePosition = safePosition.coerceAtMost(safeDuration)
        }

        val completed = ended ||
            (safeDuration > 0L && safePosition >= (safeDuration * COMPLETION_PERCENT) / 100L)
        val checkpointDue = abs(safePosition - current.lastCheckpointPositionMs) >=
            CHECKPOINT_INTERVAL_MS

        if (!completed && !force && !checkpointDue) return emptyList()
        if (
            !completed &&
            current.historyRecorded &&
            safePosition == current.lastCheckpointPositionMs &&
            safeDuration == current.lastCheckpointDurationMs
        ) {
            return emptyList()
        }
        val recordHistory = !current.historyRecorded
        current.historyRecorded = true
        current.completionRecorded = completed
        current.lastCheckpointPositionMs = safePosition
        current.lastCheckpointDurationMs = safeDuration

        return listOf(
            PlaybackMemoryWrite.VodCheckpoint(
                reference = reference,
                progressMs = if (ended && safeDuration > 0L) safeDuration else safePosition,
                durationMs = safeDuration,
                watchedAt = clock(),
                recordHistory = recordHistory,
                completed = completed
            )
        )
    }

    private data class Session(
        val reference: UserMemoryReference?,
        val isLive: Boolean,
        val startPositionMs: Long,
        val metadataDurationMs: Long,
        var hasPlayed: Boolean = false,
        var historyRecorded: Boolean = false,
        var liveRecorded: Boolean = false,
        var completionRecorded: Boolean = false,
        var terminal: Boolean = false,
        var lastCheckpointPositionMs: Long = startPositionMs,
        var lastCheckpointDurationMs: Long = metadataDurationMs
    )

    private companion object {
        const val CHECKPOINT_INTERVAL_MS = 15_000L
        const val COMPLETION_PERCENT = 95L
    }
}

internal fun PlaybackRequest.forFallback(
    source: PlaybackSource,
    currentPositionMs: Long
): PlaybackRequest {
    return copy(
        source = source,
        startPositionMs = max(startPositionMs, currentPositionMs.coerceAtLeast(0L)),
        playWhenReady = true
    )
}
