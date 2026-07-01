package com.example.calmsource.core.database

import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Test
import org.mockito.Mockito.*
import org.mockito.ArgumentMatchers.contains
import java.io.File
import org.junit.Assert.assertTrue

class DatabaseMigrationTest {

    @Test
    fun migration_8_9_with_fts5_support() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_8_9.migrate(db)

        // Verify index creation on epg_programs
        verify(db).execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_programs_time_range` ON `epg_programs` (`startTimeMs`, `endTimeMs`)"
        )

        // Verify temp fts5 check table creation and drop
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")
        verify(db).execSQL("DROP TABLE temp_fts5_migration_check")

        // Verify fts5 virtual tables creation
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts5"))
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts5"))

        // Verify insert statements
        verify(db).execSQL(
            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod"
        )
        verify(db).execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }

    @Test
    fun migration_8_9_with_fts4_fallback() {
        val db = mock(SupportSQLiteDatabase::class.java)

        // Mock fts5 check to fail
        doThrow(RuntimeException("no such module: fts5"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        MIGRATION_8_9.migrate(db)

        // Verify index creation on epg_programs
        verify(db).execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_programs_time_range` ON `epg_programs` (`startTimeMs`, `endTimeMs`)"
        )

        // Verify fts5 check table creation was attempted
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        // Verify fts4 check table creation and drop
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts4_migration_check USING fts4(test_col)")
        verify(db).execSQL("DROP TABLE temp_fts4_migration_check")

        // Verify fts4 virtual tables creation
        verify(db).execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts4(name, categoryName)")
        verify(db).execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts4(name, categoryName)")

        // Verify insert statements
        verify(db).execSQL(
            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod"
        )
        verify(db).execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }

    @Test
    fun migration_8_9_with_standard_table_fallback() {
        val db = mock(SupportSQLiteDatabase::class.java)

        // Mock fts5 check to fail
        doThrow(RuntimeException("no such module: fts5"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        // Mock fts4 check to fail
        doThrow(RuntimeException("no such module: fts4"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts4_migration_check USING fts4(test_col)")

        MIGRATION_8_9.migrate(db)

        // Verify index creation on epg_programs
        verify(db).execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_programs_time_range` ON `epg_programs` (`startTimeMs`, `endTimeMs`)"
        )

        // Verify standard tables and indexes creation
        verify(db).execSQL("CREATE TABLE IF NOT EXISTS `xtream_vod_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
        verify(db).execSQL("CREATE TABLE IF NOT EXISTS `xtream_series_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_vod_fts_name` ON `xtream_vod_fts` (`name`)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_series_fts_name` ON `xtream_series_fts` (`name`)")

        // Verify insert statements
        verify(db).execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }

    @Test
    fun createXtreamFtsTables_with_fts5_support() {
        val db = mock(SupportSQLiteDatabase::class.java)

        CalmSourceDatabase.createXtreamFtsTables(db)

        // Verify temp fts5 check table creation and drop
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")
        verify(db).execSQL("DROP TABLE temp_fts5_migration_check")

        // Verify fts5 virtual tables creation
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts5"))
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts5"))
    }

    @Test
    fun createXtreamFtsTables_with_fts4_fallback() {
        val db = mock(SupportSQLiteDatabase::class.java)

        // Mock fts5 check to fail
        doThrow(RuntimeException("no such module: fts5"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        CalmSourceDatabase.createXtreamFtsTables(db)

        // Verify fts5 check was attempted
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        // Verify fts4 check table creation and drop
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts4_migration_check USING fts4(test_col)")
        verify(db).execSQL("DROP TABLE temp_fts4_migration_check")

        // Verify fts4 virtual tables creation
        verify(db).execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts4(name, categoryName)")
        verify(db).execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts4(name, categoryName)")
    }

    @Test
    fun createXtreamFtsTables_with_standard_table_fallback() {
        val db = mock(SupportSQLiteDatabase::class.java)

        // Mock fts5 check to fail
        doThrow(RuntimeException("no such module: fts5"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")

        // Mock fts4 check to fail
        doThrow(RuntimeException("no such module: fts4"))
            .`when`(db).execSQL("CREATE VIRTUAL TABLE temp_fts4_migration_check USING fts4(test_col)")

        CalmSourceDatabase.createXtreamFtsTables(db)

        // Verify standard tables and indexes creation
        verify(db).execSQL("CREATE TABLE IF NOT EXISTS `xtream_vod_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
        verify(db).execSQL("CREATE TABLE IF NOT EXISTS `xtream_series_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_vod_fts_name` ON `xtream_vod_fts` (`name`)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_series_fts_name` ON `xtream_series_fts` (`name`)")
    }

    @Test
    fun onDestructiveMigration_dropsFtsTables() {
        val databaseSource = readProjectFile(
            "core/database/src/main/kotlin/com/example/calmsource/core/database/CalmSourceDatabase.kt"
        )
        assertTrue(
            "onDestructiveMigration should drop xtream_vod_fts",
            databaseSource.contains("db.execSQL(\"DROP TABLE IF EXISTS `xtream_vod_fts`\")")
        )
        assertTrue(
            "onDestructiveMigration should drop xtream_series_fts",
            databaseSource.contains("db.execSQL(\"DROP TABLE IF EXISTS `xtream_series_fts`\")")
        )
    }

    @Test
    fun migration_10_11_recreates_fts_tables() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_10_11.migrate(db)

        // Verify dropping existing tables
        verify(db).execSQL("DROP TABLE IF EXISTS `xtream_vod_fts`")
        verify(db).execSQL("DROP TABLE IF EXISTS `xtream_series_fts`")

        // Verify temp fts5 check table creation and drop
        verify(db).execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")
        verify(db).execSQL("DROP TABLE temp_fts5_migration_check")

        // Verify fts5 virtual tables creation
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts5"))
        verify(db).execSQL(contains("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts5"))

        // Verify insert statements
        verify(db).execSQL(
            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod"
        )
        verify(db).execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }

    @Test
    fun migration_11_12_creates_user_telemetry_table() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_11_12.migrate(db)

        // Verify user_telemetry table creation
        verify(db).execSQL(
            """
            CREATE TABLE IF NOT EXISTS `user_telemetry` (
                `telemetryId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                `profileId` TEXT NOT NULL,
                `eventType` TEXT NOT NULL,
                `eventData` TEXT NOT NULL,
                `timestamp` INTEGER NOT NULL
            )
            """.trimIndent()
        )

        // Verify indexes creation
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_profileId` ON `user_telemetry` (`profileId`)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_eventType` ON `user_telemetry` (`eventType`)")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_timestamp` ON `user_telemetry` (`timestamp`)")
    }

    @Test
    fun migration_12_13_reconstructs_user_memory_tables() {
        val db = mock(SupportSQLiteDatabase::class.java)

        MIGRATION_12_13.migrate(db)

        // Verify profiles table creation and seed
        verify(db).execSQL(
            """
            CREATE TABLE IF NOT EXISTS `profiles` (
                `id` TEXT NOT NULL,
                `name` TEXT NOT NULL,
                `avatarUrl` TEXT,
                `createdAt` INTEGER NOT NULL,
                PRIMARY KEY(`id`)
            )
            """.trimIndent()
        )
        verify(db).execSQL(
            """
            INSERT OR IGNORE INTO `profiles` (`id`, `name`, `avatarUrl`, `createdAt`)
            VALUES ('default', 'Default Profile', NULL, (CAST(strftime('%s', 'now') AS INTEGER) * 1000))
            """.trimIndent()
        )

        // Verify continue_watching reconstruction
        verify(db).execSQL("ALTER TABLE `continue_watching` RENAME TO `temp_continue_watching`")
        verify(db).execSQL(
            """
            CREATE TABLE IF NOT EXISTS `continue_watching` (
                `profileId` TEXT NOT NULL,
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `progressMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `itemKey`)
            )
            """.trimIndent()
        )
        verify(db).execSQL(
            """
            INSERT INTO `continue_watching` (
                `profileId`, `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `progressMs`, `durationMs`, `updatedAt`
            )
            SELECT
                'default', `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `progressMs`, `durationMs`, `updatedAt`
            FROM `temp_continue_watching`
            """.trimIndent()
        )
        verify(db).execSQL("DROP TABLE `temp_continue_watching`")
        verify(db).execSQL("CREATE INDEX IF NOT EXISTS `index_continue_watching_updatedAt` ON `continue_watching` (`updatedAt`)")
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
