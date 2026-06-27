package com.example.calmsource.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Mission27CoreDatabaseHardeningTest {
    @Test
    fun `core database factory enables WAL and records pragmas`() {
        val source = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt"
        )

        assertTrue(source.contains("fun buildDatabase("))
        assertTrue(source.contains(".setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)"))
        assertTrue(source.contains("PRAGMA synchronous = NORMAL"))
        assertTrue(source.contains("PRAGMA foreign_keys = ON"))
        assertTrue(source.contains("PRAGMA busy_timeout = 5000"))
        assertTrue(source.contains("CoreDatabaseRuntimeStatus.recordOpen"))
        assertTrue(source.contains("SlowQueryLogger.installOnOpen"))
        assertFalse(source.contains(".fallbackToDestructiveMigration()"))
    }

    @Test
    fun `database provider uses the shared hardened factory`() {
        val source = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/DatabaseProvider.kt"
        )

        assertTrue(source.contains("CalmSourceDatabase.buildDatabase(context)"))
        assertFalse(source.contains("Room.databaseBuilder("))
    }

    @Test
    fun `runtime status normalizes WAL state`() {
        CoreDatabaseRuntimeStatus.recordOpen(
            journalMode = " WAL ",
            busyTimeoutMs = 5000
        )

        val state = CoreDatabaseRuntimeStatus.state.value
        assertEquals("wal", state.journalMode)
        assertTrue(state.walEnabled)
        assertEquals(5000, state.busyTimeoutMs)
    }

    @Test
    fun `database builder specifies destructive fallback from versions 1 2 3 4`() {
        val source = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt"
        )
        assertTrue(
            "Should fallback to destructive migration specifically from versions 1, 2, 3, 4",
            source.contains(".fallbackToDestructiveMigrationFrom(1, 2, 3, 4)")
        )
    }

    private fun readProjectFile(relativePath: String): String {
        val candidates = listOf(
            File(relativePath),
            File("../$relativePath"),
            File("../../$relativePath"),
            File("d:/Program Files/iptv/$relativePath")
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Could not find source file: $relativePath")
    }
}
