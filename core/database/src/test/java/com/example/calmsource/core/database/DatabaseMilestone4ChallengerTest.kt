package com.example.calmsource.core.database

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class DatabaseMilestone4ChallengerTest {

    @Test
    fun verifyDatabaseCallbackHandlesOnCreateAndOnDestructiveMigration() {
        val databaseSource = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt"
        )

        // Verify that onCreate is overridden and calls createXtreamFtsTables
        assertTrue(
            "onCreate callback should be present in CalmSourceDatabase.kt",
            databaseSource.contains("override fun onCreate(db: SupportSQLiteDatabase)")
        )
        assertTrue(
            "onCreate callback should call createXtreamFtsTables",
            databaseSource.contains("createXtreamFtsTables(db)")
        )

        // Verify if onDestructiveMigration is implemented in the database callback.
        // Under current implementation, it is overridden, which causes FTS virtual tables
        // to be correctly created when upgrading/re-creating the DB.
        val hasOnDestructiveMigration = databaseSource.contains("override fun onDestructiveMigration")
        
        System.out.println("Empirical Challenge Result: hasOnDestructiveMigration = $hasOnDestructiveMigration")
        
        // Assert true to document that this has been successfully resolved
        assertTrue(
            "CalmSourceDatabase does not override onDestructiveMigration callback! " +
            "This will cause FTS virtual tables to be missing and result in crashes/data-leaks " +
            "when falling back to destructive migration.",
            hasOnDestructiveMigration
        )
    }

    @Test
    fun verifyXtreamRepositoryFtsDeletionOrdering() {
        val repoSource = readProjectFile(
            "feature/iptv/src/main/kotlin/com/example/calmsource/feature/iptv/XtreamRepository.kt"
        )

        // 1. Verify deleteXtreamProvider reordering: FTS deletions must occur BEFORE base table deletions
        val deleteMethodStart = repoSource.indexOf("suspend fun deleteXtreamProvider")
        if (deleteMethodStart == -1) {
            fail("deleteXtreamProvider method not found in XtreamRepository.kt")
        }
        val deleteMethodEnd = repoSource.indexOf("secureTokenStore.clearProvider", deleteMethodStart)
        val deleteMethodScope = repoSource.substring(deleteMethodStart, deleteMethodEnd)

        val ftsVodDeleteIndex = deleteMethodScope.indexOf("xtream_vod_fts")
        val baseVodDeleteIndex = deleteMethodScope.indexOf("deleteVodByProvider")
        
        val ftsSeriesDeleteIndex = deleteMethodScope.indexOf("xtream_series_fts")
        val baseSeriesDeleteIndex = deleteMethodScope.indexOf("deleteSeriesByProvider")

        assertTrue(
            "FTS VOD delete query should be executed in deleteXtreamProvider",
            ftsVodDeleteIndex != -1
        )
        assertTrue(
            "Base VOD delete query should be executed in deleteXtreamProvider",
            baseVodDeleteIndex != -1
        )
        assertTrue(
            "FTS Series delete query should be executed in deleteXtreamProvider",
            ftsSeriesDeleteIndex != -1
        )
        assertTrue(
            "Base Series delete query should be executed in deleteXtreamProvider",
            baseSeriesDeleteIndex != -1
        )

        assertTrue(
            "CORRECTNESS VERIFICATION: FTS VOD deletion must occur before base VOD deletion " +
            "so the subquery can find the matching rowids.",
            ftsVodDeleteIndex < baseVodDeleteIndex
        )
        assertTrue(
            "CORRECTNESS VERIFICATION: FTS Series deletion must occur before base Series deletion " +
            "so the subquery can find the matching rowids.",
            ftsSeriesDeleteIndex < baseSeriesDeleteIndex
        )

        // 2. Verify VOD sync reordering: FTS deletion must occur BEFORE base VOD deletion
        val syncVodDeleteIndex = repoSource.indexOf("DELETE FROM xtream_vod_fts", deleteMethodEnd)
        val syncBaseVodDeleteIndex = repoSource.indexOf("deleteVodByProvider", deleteMethodEnd)

        assertTrue(
            "FTS VOD deletion must occur before base VOD deletion in syncProvider VOD sync step.",
            syncVodDeleteIndex < syncBaseVodDeleteIndex
        )

        // 3. Verify Series sync reordering: FTS deletion must occur BEFORE base Series deletion
        val syncSeriesDeleteIndex = repoSource.indexOf("DELETE FROM xtream_series_fts", deleteMethodEnd)
        val syncBaseSeriesDeleteIndex = repoSource.indexOf("deleteSeriesByProvider", deleteMethodEnd)

        assertTrue(
            "FTS Series deletion must occur before base Series deletion in syncProvider Series sync step.",
            syncSeriesDeleteIndex < syncBaseSeriesDeleteIndex
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
