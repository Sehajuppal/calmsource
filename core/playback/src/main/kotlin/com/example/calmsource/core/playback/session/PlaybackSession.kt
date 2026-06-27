package com.example.calmsource.core.playback.session

import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource

enum class SessionPhase {
    Idle,
    Racing,
    Preparing,
    Playing,
    Recovering,
    Failed,
}

/**
 * Monotonic playback session started by each [com.example.calmsource.core.playback.PlaybackManager.prepare]
 * (or invalidated by [com.example.calmsource.core.playback.PlaybackManager.release]).
 * Async work captures [id] and aborts when a newer prepare/release supersedes it.
 */
data class PlaybackSession(
    val id: Long,
    val request: PlaybackRequest,
    val candidates: List<PlaybackSource>,
    val phase: SessionPhase,
)
