package com.example.calmsource.core.playback.diagnostics

import com.example.calmsource.core.model.PlaybackRecoveryEvent
import com.example.calmsource.core.model.PlaybackSessionDiagnostics

object PlaybackDiagnosticsFormatter {
    fun formatEvent(event: PlaybackRecoveryEvent): String {
        val sourceSuffix = event.sourceId?.let { " ($it)" }.orEmpty()
        return "${event.kind}: ${event.detail}$sourceSuffix"
    }

    fun snapshotRows(snapshot: PlaybackSessionDiagnostics): List<Pair<String, String>> = listOf(
        "Session" to snapshot.sessionId.toString(),
        "Phase" to (snapshot.phase ?: "—"),
        "Backend" to (snapshot.activeBackend ?: "—"),
        "Source" to (snapshot.sourceId ?: "—"),
        "Failures" to snapshot.consecutiveFailures.toString(),
        "Policy" to (snapshot.fallbackPolicy ?: "—"),
    )
}
