package com.example.calmsource.core.model

/**
 * Sanitized playback recovery event for in-app diagnostics.
 * Must never contain raw URLs, tokens, or credentials.
 */
data class PlaybackRecoveryEvent(
    val atMs: Long,
    val kind: String,
    val detail: String,
    val sourceId: String? = null,
)

/**
 * Live snapshot of the active playback session for debug UI and soak inspection.
 */
data class PlaybackSessionDiagnostics(
    val sessionId: Long = 0L,
    val phase: String? = null,
    val activeBackend: String? = null,
    val consecutiveFailures: Int = 0,
    val fallbackPolicy: String? = null,
    val sourceId: String? = null,
    val recentEvents: List<PlaybackRecoveryEvent> = emptyList(),
)
