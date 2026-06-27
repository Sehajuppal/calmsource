package com.example.calmsource.core.discoveryengine.database

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class DiscoveryDatabaseDebugState(
    val openedAtMs: Long = 0L,
    val journalMode: String = "unknown",
    val walEnabled: Boolean = false,
    val busyTimeoutMs: Int = 0,
    val ftsMode: String = "unknown",
    val lastOpenError: String? = null
)

object DiscoveryDatabaseRuntimeStatus {
    private val _state = MutableStateFlow(DiscoveryDatabaseDebugState())
    val state: StateFlow<DiscoveryDatabaseDebugState> = _state.asStateFlow()

    fun recordOpen(
        journalMode: String,
        busyTimeoutMs: Int,
        ftsMode: String = "unknown",
        error: String? = null
    ) {
        val normalizedJournal = journalMode.trim().lowercase()
        _state.value = DiscoveryDatabaseDebugState(
            openedAtMs = System.currentTimeMillis(),
            journalMode = normalizedJournal.ifBlank { "unknown" },
            walEnabled = normalizedJournal == "wal",
            busyTimeoutMs = busyTimeoutMs,
            ftsMode = ftsMode.ifBlank { "unknown" },
            lastOpenError = error
        )
    }
}
