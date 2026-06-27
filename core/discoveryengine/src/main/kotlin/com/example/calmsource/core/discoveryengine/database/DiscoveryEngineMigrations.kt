package com.example.calmsource.core.discoveryengine.database

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Migrations for the Discovery Engine database.
 *
 * All migrations in this file are additive — they create new tables for the
 * provider layer (registry, caches, telemetry) without touching the original
 * 17 entities. Existing user data is preserved across the 5 -> 6 upgrade.
 */
object DiscoveryEngineMigrations {

    val MIGRATION_5_6: Migration = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Provider registry
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_registry` (
                    `providerId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `kind` TEXT NOT NULL,
                    `endpointUrl` TEXT,
                    `isEnabled` INTEGER NOT NULL,
                    `isSystemProvider` INTEGER NOT NULL,
                    `isUserInstalled` INTEGER NOT NULL,
                    `priority` INTEGER NOT NULL,
                    `supportsCatalog` INTEGER NOT NULL,
                    `supportsMeta` INTEGER NOT NULL,
                    `supportsStream` INTEGER NOT NULL,
                    `supportsSubtitles` INTEGER NOT NULL,
                    `supportsSearch` INTEGER NOT NULL,
                    `supportsRatings` INTEGER NOT NULL,
                    `supportsSimilar` INTEGER NOT NULL,
                    `supportsArtwork` INTEGER NOT NULL,
                    `supportsAvailability` INTEGER NOT NULL,
                    `privacySensitive` INTEGER NOT NULL,
                    `lastSuccessAt` INTEGER,
                    `lastFailureAt` INTEGER,
                    `failureCount` INTEGER NOT NULL,
                    `reliabilityScore` REAL NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`providerId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_registry_type` ON `provider_registry` (`type`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_registry_priority` ON `provider_registry` (`priority`)")

            // Metadata cache
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `metadata_cache` (
                    `mediaId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `title` TEXT,
                    `originalTitle` TEXT,
                    `aliases` TEXT,
                    `overview` TEXT,
                    `genres` TEXT,
                    `cast` TEXT,
                    `director` TEXT,
                    `runtimeMinutes` INTEGER,
                    `language` TEXT,
                    `country` TEXT,
                    `posterUrl` TEXT,
                    `backdropUrl` TEXT,
                    `externalIdsJson` TEXT,
                    `collectionJson` TEXT,
                    `seasonEpisodeJson` TEXT,
                    `confidenceScore` REAL NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `providerId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_cache_expiresAt` ON `metadata_cache` (`expiresAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_metadata_cache_providerId` ON `metadata_cache` (`providerId`)")

            // Ratings cache
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `ratings_cache` (
                    `mediaId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `ratingValue` REAL NOT NULL,
                    `ratingScale` REAL NOT NULL,
                    `voteCount` INTEGER,
                    `popularityScore` REAL,
                    `qualityScore` REAL,
                    `confidenceScore` REAL NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `providerId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_ratings_cache_expiresAt` ON `ratings_cache` (`expiresAt`)")

            // Similar cache
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `similar_cache` (
                    `mediaId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `similarMediaId` TEXT NOT NULL,
                    `similarExternalIdsJson` TEXT,
                    `similarTitle` TEXT,
                    `providerScore` REAL,
                    `reason` TEXT,
                    `confidenceScore` REAL NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `providerId`, `similarMediaId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_similar_cache_expiresAt` ON `similar_cache` (`expiresAt`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_similar_cache_similarMediaId` ON `similar_cache` (`similarMediaId`)")

            // Subtitles cache
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `subtitles_cache` (
                    `id` TEXT NOT NULL,
                    `mediaId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `streamHash` TEXT,
                    `filename` TEXT,
                    `language` TEXT NOT NULL,
                    `subtitleUrl` TEXT NOT NULL,
                    `subtitleFormat` TEXT,
                    `matchConfidence` REAL NOT NULL,
                    `fetchedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_cache_mediaId` ON `subtitles_cache` (`mediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_subtitles_cache_expiresAt` ON `subtitles_cache` (`expiresAt`)")

            // Availability cache
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `availability_cache` (
                    `mediaId` TEXT NOT NULL,
                    `providerId` TEXT NOT NULL,
                    `addonId` TEXT NOT NULL,
                    `streamCount` INTEGER NOT NULL,
                    `bestQuality` TEXT,
                    `hasSubtitles` INTEGER NOT NULL,
                    `languagesJson` TEXT,
                    `confidenceScore` REAL NOT NULL,
                    `lastCheckedAt` INTEGER NOT NULL,
                    `expiresAt` INTEGER NOT NULL,
                    PRIMARY KEY(`mediaId`, `providerId`, `addonId`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_availability_cache_expiresAt` ON `availability_cache` (`expiresAt`)")

            // Addon availability (per-addon reliability)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `addon_availability` (
                    `mediaId` TEXT NOT NULL,
                    `addonId` TEXT NOT NULL,
                    `streamCount` INTEGER NOT NULL,
                    `bestQuality` TEXT,
                    `hasSubtitles` INTEGER NOT NULL,
                    `lastCheckedAt` INTEGER NOT NULL,
                    `lastSuccessAt` INTEGER,
                    `lastFailureAt` INTEGER,
                    `reliabilityScore` REAL NOT NULL,
                    PRIMARY KEY(`mediaId`, `addonId`)
                )
                """.trimIndent()
            )

            // Provider failure log
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_failure_log` (
                    `failureId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `providerId` TEXT NOT NULL,
                    `requestType` TEXT NOT NULL,
                    `mediaId` TEXT,
                    `errorCode` TEXT NOT NULL,
                    `errorMessage` TEXT,
                    `occurredAt` INTEGER NOT NULL,
                    `retryAfter` INTEGER
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_failure_log_providerId` ON `provider_failure_log` (`providerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_failure_log_occurredAt` ON `provider_failure_log` (`occurredAt`)")

            // Provider usage log (developer/debug)
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `provider_usage_log` (
                    `usageId` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                    `providerId` TEXT NOT NULL,
                    `requestType` TEXT NOT NULL,
                    `mediaId` TEXT,
                    `cacheHit` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `success` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_usage_log_providerId` ON `provider_usage_log` (`providerId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_provider_usage_log_createdAt` ON `provider_usage_log` (`createdAt`)")
        }
    }

    /**
     * Adds a composite index for watch event queries.
     * The primary query pattern joins on (profileId, itemId) then orders by
     * timestamp. Individual indices exist per column but SQLite can only use
     * one per query — this composite covers the full access pattern.
     */
    val MIGRATION_6_7: Migration = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_watch_events_lookup` " +
                    "ON `watch_events` (`profileId`, `itemId`, `timestamp`)"
            )
        }
    }

    /**
     * Creates a normalized external-IDs junction table and populates it from
     * existing externalIdsJson column data. This replaces the O(n) LIKE '%id%'
     * full-table scan with an O(log n) indexed primary-key lookup.
     */
    val MIGRATION_7_8: Migration = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. Create the junction table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `media_external_ids` (
                    `mediaId` TEXT NOT NULL,
                    `idType` TEXT NOT NULL,
                    `idValue` TEXT NOT NULL,
                    PRIMARY KEY(`idType`, `idValue`)
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_external_ids_mediaId` ON `media_external_ids` (`mediaId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_media_external_ids_idValue` ON `media_external_ids` (`idValue`)")

            // 2. Populate from existing externalIdsJson using json_each (SQLite 3.38+)
            val hasJsonEach = try {
                db.query("SELECT json_each.key FROM json_each('{\"test\":1}') LIMIT 1").use { it.moveToFirst() }
                true
            } catch (e: Exception) {
                false
            }

            if (hasJsonEach) {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO media_external_ids (mediaId, idType, idValue)
                    SELECT m.id, j.key, j.value
                    FROM media_items m, json_each(m.externalIdsJson) j
                    WHERE m.externalIdsJson IS NOT NULL
                      AND m.externalIdsJson != '{}'
                      AND m.externalIdsJson != ''
                      AND length(CAST(j.value AS TEXT)) > 0
                    """.trimIndent()
                )
            } else {
                // Fallback: populate from the externalId column (primary ID only)
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO media_external_ids (mediaId, idType, idValue)
                    SELECT id, 'primary', externalId
                    FROM media_items
                    WHERE externalId IS NOT NULL AND externalId != ''
                    """.trimIndent()
                )
            }
        }
    }
}
