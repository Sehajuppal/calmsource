package com.example.calmsource.core.database

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseFallbackChallengerTest {

    @Test
    fun verifyHiltUsesSingleDatabaseProviderInstance() {
        val hiltModulesFile = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/HiltModules.kt"
        )

        assertTrue(
            "HiltModules should initialize DatabaseProvider before opening the database",
            hiltModulesFile.contains("DatabaseProvider.init(context)")
        )
        assertTrue(
            "HiltModules should use DatabaseProvider.getDatabase for the singleton",
            hiltModulesFile.contains("DatabaseProvider.getDatabase(context)")
        )
        assertFalse(
            "HiltModules must not create a separate in-memory Room instance",
            hiltModulesFile.contains("Room.inMemoryDatabaseBuilder")
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
