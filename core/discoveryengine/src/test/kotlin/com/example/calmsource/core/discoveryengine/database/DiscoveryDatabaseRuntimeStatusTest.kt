package com.example.calmsource.core.discoveryengine.database

import org.junit.Assert.assertEquals
import org.junit.Test

class DiscoveryDatabaseRuntimeStatusTest {

    @Test
    fun recordOpen_normalizesJournalAndIncludesFtsMode() {
        DiscoveryDatabaseRuntimeStatus.recordOpen(
            journalMode = "  WAL  ",
            busyTimeoutMs = 5000,
            ftsMode = "FTS5",
            error = null
        )
        val state = DiscoveryDatabaseRuntimeStatus.state.value
        assertEquals("wal", state.journalMode)
        assertEquals(true, state.walEnabled)
        assertEquals(5000, state.busyTimeoutMs)
        assertEquals("FTS5", state.ftsMode)
    }

    @Test
    fun recordOpen_defaultsFtsModeToUnknown() {
        DiscoveryDatabaseRuntimeStatus.recordOpen(
            journalMode = "delete",
            busyTimeoutMs = 1000
        )
        val state = DiscoveryDatabaseRuntimeStatus.state.value
        assertEquals("delete", state.journalMode)
        assertEquals(false, state.walEnabled)
        assertEquals("unknown", state.ftsMode)
    }

    @Test
    fun recordOpen_replacesBlankFtsModeWithUnknown() {
        DiscoveryDatabaseRuntimeStatus.recordOpen(
            journalMode = "wal",
            busyTimeoutMs = 1000,
            ftsMode = "   "
        )
        assertEquals("unknown", DiscoveryDatabaseRuntimeStatus.state.value.ftsMode)
    }
}
