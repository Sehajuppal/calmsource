package com.example.calmsource.core.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `continue_watching` (
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `progressMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_continue_watching_updatedAt` " +
                "ON `continue_watching` (`updatedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites` (
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_favorites_updatedAt` " +
                "ON `favorites` (`updatedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `watch_history` (
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `firstWatchedAt` INTEGER NOT NULL,
                `lastWatchedAt` INTEGER NOT NULL,
                `watchCount` INTEGER NOT NULL,
                `progressMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                PRIMARY KEY(`itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_watch_history_lastWatchedAt` " +
                "ON `watch_history` (`lastWatchedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recent_channels` (
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `lastWatchedAt` INTEGER NOT NULL,
                `watchCount` INTEGER NOT NULL,
                PRIMARY KEY(`itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_recent_channels_lastWatchedAt` " +
                "ON `recent_channels` (`lastWatchedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_history` (
                `normalizedQuery` TEXT NOT NULL,
                `query` TEXT NOT NULL,
                `lastSearchedAt` INTEGER NOT NULL,
                `searchCount` INTEGER NOT NULL,
                PRIMARY KEY(`normalizedQuery`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_search_history_lastSearchedAt` " +
                "ON `search_history` (`lastSearchedAt`)"
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `preference_signals` (
                `signalType` TEXT NOT NULL,
                `signalKey` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `lastSignaledAt` INTEGER NOT NULL,
                PRIMARY KEY(`signalType`, `signalKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_preference_signals_lastSignaledAt` " +
                "ON `preference_signals` (`lastSignaledAt`)"
        )
    }
}

val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `user_preferences` ADD COLUMN `separateIptvCategoriesByProvider` INTEGER NOT NULL DEFAULT 0"
        )
    }
}

val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_iptv_channels_groupTitle` ON `iptv_channels` (`groupTitle`)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_source_health_providerId` ON `source_health` (`providerId`)"
        )
    }
}

/**
 * Migrates the core DB to version 9:
 * 1. Composite index on EPG time range for point-in-time queries.
 * 2. FTS5 virtual tables for Xtream VOD and series search (contentless).
 *    Population happens in XtreamRepository at sync time, not via SQL triggers
 *    (contentless FTS5 tables need explicit rowid-tracked inserts).
 */
val MIGRATION_8_9 = object : Migration(8, 9) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS `index_epg_programs_time_range` " +
                "ON `epg_programs` (`startTimeMs`, `endTimeMs`)"
        )
        CalmSourceDatabase.createXtreamFtsTables(db)
        db.execSQL(
            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) " +
                "SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod"
        )
        db.execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) " +
                "SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }
}

val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `language` TEXT")
        db.execSQL("ALTER TABLE `iptv_channels` ADD COLUMN `country` TEXT")
    }
}

val MIGRATION_10_11 = object : Migration(10, 11) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP TABLE IF EXISTS `xtream_vod_fts`")
        db.execSQL("DROP TABLE IF EXISTS `xtream_series_fts`")
        CalmSourceDatabase.createXtreamFtsTables(db)
        db.execSQL(
            "INSERT INTO xtream_vod_fts(rowid, name, categoryName) " +
                "SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_vod"
        )
        db.execSQL(
            "INSERT INTO xtream_series_fts(rowid, name, categoryName) " +
                "SELECT rowid, name, IFNULL(categoryName, '') FROM xtream_series"
        )
    }
}

val MIGRATION_11_12 = object : Migration(11, 12) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
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
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_profileId` ON `user_telemetry` (`profileId`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_eventType` ON `user_telemetry` (`eventType`)")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_user_telemetry_timestamp` ON `user_telemetry` (`timestamp`)")
    }
}

val MIGRATION_12_13 = object : Migration(12, 13) {
    override fun migrate(db: SupportSQLiteDatabase) {
        // 1. Create profiles table
        db.execSQL(
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

        // 2. Seed 'default' profile
        db.execSQL(
            """
            INSERT OR IGNORE INTO `profiles` (`id`, `name`, `avatarUrl`, `createdAt`)
            VALUES ('default', 'Default Profile', NULL, (CAST(strftime('%s', 'now') AS INTEGER) * 1000))
            """.trimIndent()
        )

        // 3. Reconstruct continue_watching
        db.execSQL("ALTER TABLE `continue_watching` RENAME TO `temp_continue_watching`")
        db.execSQL(
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
        db.execSQL(
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
        db.execSQL("DROP TABLE `temp_continue_watching`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_continue_watching_updatedAt` ON `continue_watching` (`updatedAt`)")

        // 4. Reconstruct favorites
        db.execSQL("ALTER TABLE `favorites` RENAME TO `temp_favorites`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `favorites` (
                `profileId` TEXT NOT NULL,
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `createdAt` INTEGER NOT NULL,
                `updatedAt` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `favorites` (
                `profileId`, `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `createdAt`, `updatedAt`
            )
            SELECT
                'default', `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `createdAt`, `updatedAt`
            FROM `temp_favorites`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `temp_favorites`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_favorites_updatedAt` ON `favorites` (`updatedAt`)")

        // 5. Reconstruct watch_history
        db.execSQL("ALTER TABLE `watch_history` RENAME TO `temp_watch_history`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `watch_history` (
                `profileId` TEXT NOT NULL,
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `firstWatchedAt` INTEGER NOT NULL,
                `lastWatchedAt` INTEGER NOT NULL,
                `watchCount` INTEGER NOT NULL,
                `progressMs` INTEGER NOT NULL,
                `durationMs` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `watch_history` (
                `profileId`, `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `firstWatchedAt`, `lastWatchedAt`,
                `watchCount`, `progressMs`, `durationMs`
            )
            SELECT
                'default', `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `firstWatchedAt`, `lastWatchedAt`,
                `watchCount`, `progressMs`, `durationMs`
            FROM `temp_watch_history`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `temp_watch_history`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_watch_history_lastWatchedAt` ON `watch_history` (`lastWatchedAt`)")

        // 6. Reconstruct recent_channels
        db.execSQL("ALTER TABLE `recent_channels` RENAME TO `temp_recent_channels`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `recent_channels` (
                `profileId` TEXT NOT NULL,
                `itemKey` TEXT NOT NULL,
                `contentType` TEXT NOT NULL,
                `title` TEXT NOT NULL,
                `subtitle` TEXT,
                `providerId` TEXT,
                `sourceId` TEXT,
                `lastWatchedAt` INTEGER NOT NULL,
                `watchCount` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `itemKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `recent_channels` (
                `profileId`, `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `lastWatchedAt`, `watchCount`
            )
            SELECT
                'default', `itemKey`, `contentType`, `title`, `subtitle`,
                `providerId`, `sourceId`, `lastWatchedAt`, `watchCount`
            FROM `temp_recent_channels`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `temp_recent_channels`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_recent_channels_lastWatchedAt` ON `recent_channels` (`lastWatchedAt`)")

        // 7. Reconstruct search_history
        db.execSQL("ALTER TABLE `search_history` RENAME TO `temp_search_history`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `search_history` (
                `profileId` TEXT NOT NULL,
                `normalizedQuery` TEXT NOT NULL,
                `query` TEXT NOT NULL,
                `lastSearchedAt` INTEGER NOT NULL,
                `searchCount` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `normalizedQuery`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `search_history` (
                `profileId`, `normalizedQuery`, `query`, `lastSearchedAt`, `searchCount`
            )
            SELECT
                'default', `normalizedQuery`, `query`, `lastSearchedAt`, `searchCount`
            FROM `temp_search_history`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `temp_search_history`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_search_history_lastSearchedAt` ON `search_history` (`lastSearchedAt`)")

        // 8. Reconstruct preference_signals
        db.execSQL("ALTER TABLE `preference_signals` RENAME TO `temp_preference_signals`")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS `preference_signals` (
                `profileId` TEXT NOT NULL,
                `signalType` TEXT NOT NULL,
                `signalKey` TEXT NOT NULL,
                `count` INTEGER NOT NULL,
                `lastSignaledAt` INTEGER NOT NULL,
                PRIMARY KEY(`profileId`, `signalType`, `signalKey`)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            INSERT INTO `preference_signals` (
                `profileId`, `signalType`, `signalKey`, `count`, `lastSignaledAt`
            )
            SELECT
                'default', `signalType`, `signalKey`, `count`, `lastSignaledAt`
            FROM `temp_preference_signals`
            """.trimIndent()
        )
        db.execSQL("DROP TABLE `temp_preference_signals`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_preference_signals_lastSignaledAt` ON `preference_signals` (`lastSignaledAt`)")
    }
}

val MIGRATION_13_14 = object : Migration(13, 14) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DROP INDEX IF EXISTS `index_epg_programs_channelId`")
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_epg_programs_channelId_startTimeMs` ON `epg_programs` (`channelId`, `startTimeMs`)")
    }
}




