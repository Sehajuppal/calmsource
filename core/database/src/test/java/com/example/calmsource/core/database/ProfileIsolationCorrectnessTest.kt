package com.example.calmsource.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileIsolationCorrectnessTest {

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

    @Test
    fun testProfileIdPresenceAndPrimaryKeyIntegration() {
        val entitiesFile = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/entity/UserMemoryEntities.kt"
        )

        val targetEntities = listOf(
            "ContinueWatchingEntity",
            "FavoriteEntity",
            "WatchHistoryEntity",
            "RecentChannelEntity",
            "SearchHistoryEntity",
            "PreferenceSignalEntity"
        )

        for (entity in targetEntities) {
            // Find the index of the class definition
            val classIndex = entitiesFile.indexOf("data class $entity")
            assertTrue("Entity $entity class declaration should exist in UserMemoryEntities.kt", classIndex != -1)

            // Look at the annotation block just before the class declaration
            val annotationBlock = entitiesFile.substring(0, classIndex)
                .substringAfterLast("@Entity")

            // Verify profileId is part of primaryKeys
            assertTrue(
                "Entity $entity should declare 'profileId' in its primaryKeys list",
                annotationBlock.contains("profileId") && annotationBlock.contains("primaryKeys")
            )

            // Verify profileId field is present in constructor/properties of the data class
            val classBody = entitiesFile.substring(classIndex).substringBefore(")")
            assertTrue(
                "Entity $entity should have a non-null profileId property",
                classBody.contains("val profileId: String")
            )
        }
    }

    @Test
    fun testRoomDaoHasProfileIdIsolation() {
        val daoFile = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/dao/UserMemoryDao.kt"
        )

        // Find all occurrences of @Query in the DAO file
        var currentIndex = 0
        val nonIsolatedQueries = mutableListOf<String>()
        val nonIsolatedDeletes = mutableListOf<String>()

        while (true) {
            val queryIndex = daoFile.indexOf("@Query(", currentIndex)
            if (queryIndex == -1) break

            // Extract the SQL string
            val startQuote = daoFile.indexOf("\"", queryIndex)
            val endQuote = daoFile.indexOf("\"", startQuote + 1)
            // Handle triple quotes just in case
            val sql = if (daoFile.substring(startQuote, startQuote + 3) == "\"\"\"") {
                val endTripleQuote = daoFile.indexOf("\"\"\"", startQuote + 3)
                daoFile.substring(startQuote + 3, endTripleQuote).trim()
            } else {
                daoFile.substring(startQuote + 1, endQuote)
            }

            // Find the method name that follows
            val functionIndex = daoFile.indexOf("fun ", queryIndex)
            val suspendIndex = daoFile.indexOf("suspend fun ", queryIndex)
            val actualFuncIndex = if (suspendIndex != -1 && suspendIndex < functionIndex) suspendIndex else functionIndex
            val methodSignature = daoFile.substring(actualFuncIndex).substringBefore("\n")

            val sqlLower = sql.lowercase()
            val isUserMemoryTable = sqlLower.contains("continue_watching") ||
                    sqlLower.contains("favorites") ||
                    sqlLower.contains("watch_history") ||
                    sqlLower.contains("recent_channels") ||
                    sqlLower.contains("search_history") ||
                    sqlLower.contains("preference_signals")

            if (isUserMemoryTable) {
                val hasProfileIdFilter = sqlLower.contains("profileid")
                if (!hasProfileIdFilter) {
                    if (sqlLower.contains("select")) {
                        nonIsolatedQueries.add("$methodSignature -> SQL: $sql")
                    } else if (sqlLower.contains("delete")) {
                        nonIsolatedDeletes.add("$methodSignature -> SQL: $sql")
                    }
                }
            }

            currentIndex = queryIndex + 1
        }

        // Print results to stdout
        println("--- Empirical Isolation Analysis via Source Parsing ---")
        println("Non-isolated queries found: ${nonIsolatedQueries.size}")
        nonIsolatedQueries.forEach { println("  - $it") }
        println("Non-isolated delete/trims found: ${nonIsolatedDeletes.size}")
        nonIsolatedDeletes.forEach { println("  - $it") }

        // Assert that isolation is indeed present in current Room DAOs
        assertTrue(
            "Expected zero non-isolated query methods in current UserMemoryDao implementation, but found: $nonIsolatedQueries",
            nonIsolatedQueries.isEmpty()
        )
        assertTrue(
            "Expected zero non-isolated delete methods in current UserMemoryDao implementation, but found: $nonIsolatedDeletes",
            nonIsolatedDeletes.isEmpty()
        )
    }
}
