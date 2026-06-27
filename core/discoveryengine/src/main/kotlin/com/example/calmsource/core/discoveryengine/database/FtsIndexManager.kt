package com.example.calmsource.core.discoveryengine.database

import androidx.sqlite.db.SupportSQLiteDatabase
import android.util.Log

/**
 * Manages virtual search index tables in SQLite using raw SupportSQLiteDatabase interfaces.
 * Provides runtime detection of FTS5 and FTS4, falling back to a standard indexed table if unsupported.
 */
object FtsIndexManager {

    private const val TAG = "FtsIndexManager"

    data class IndexEntry(
        val id: String,
        val type: String,
        val title: String,
        val normalizedTitle: String,
        val overview: String?,
        val genres: String?,
        val castDirector: String?,
        val aliases: String? = null
    )

    enum class FtsMode {
        FTS5, FTS4, STANDARD
    }

    @Volatile
    var activeMode: FtsMode = FtsMode.STANDARD
        private set

    /**
     * Initializes the search index table on SQLite database connection open.
     */
    fun initialize(db: SupportSQLiteDatabase) {
        activeMode = detectFtsMode(db)
        // Surface the resolved mode once per process so debug screens and logcat
        // can correlate. Note: the underlying FTS5/FTS4 probe emits a native
        // `E SQLiteLog: no such module: fts5` line on devices without FTS5 —
        // that line comes from libsqlite and cannot be suppressed; the
        // Kotlin `Log.i` here is the visible-mode breadcrumb. We swallow
        // any RuntimeException so this breadcrumb never affects tests where
        // `android.util.Log` is not mocked.
        runCatching { Log.i(TAG, "FTS mode resolved: ${activeMode.name}") }
        val rebuildForAliases = searchIndexNeedsAliasRebuild(db)
        val mismatch = checkSearchIndexTypeMismatch(db, activeMode)
        if (rebuildForAliases || mismatch) {
            db.execSQL("DROP TABLE IF EXISTS fts_search_index")
        }
        createSearchIndexTable(db)
        if (rebuildForAliases || mismatch) {
            rebuildFromSourceTables(db)
        }
    }

    private fun detectFtsMode(db: SupportSQLiteDatabase): FtsMode {
        // Test FTS5 capability
        val hasFts5 = try {
            db.execSQL("CREATE VIRTUAL TABLE temp_fts5_check USING fts5(test_col)")
            db.execSQL("DROP TABLE temp_fts5_check")
            true
        } catch (e: Exception) {
            false
        }
        if (hasFts5) return FtsMode.FTS5

        // Test FTS4 capability
        val hasFts4 = try {
            db.execSQL("CREATE VIRTUAL TABLE temp_fts4_check USING fts4(test_col)")
            db.execSQL("DROP TABLE temp_fts4_check")
            true
        } catch (e: Exception) {
            false
        }
        if (hasFts4) return FtsMode.FTS4

        return FtsMode.STANDARD
    }

    private fun createSearchIndexTable(db: SupportSQLiteDatabase) {
        when (activeMode) {
            FtsMode.FTS5 -> {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS fts_search_index USING fts5(" +
                            "id, type, title, normalized_title, overview, genres, cast_director, aliases" +
                            ")"
                )
            }
            FtsMode.FTS4 -> {
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS fts_search_index USING fts4(" +
                            "id, type, title, normalized_title, overview, genres, cast_director, aliases" +
                            ")"
                )
            }
            FtsMode.STANDARD -> {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS fts_search_index (" +
                            "id TEXT PRIMARY KEY, type TEXT, title TEXT, normalized_title TEXT, " +
                            "overview TEXT, genres TEXT, cast_director TEXT, aliases TEXT" +
                            ")"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS idx_fts_norm_title ON fts_search_index(normalized_title)"
                )
            }
        }
    }

    /**
     * Upserts an entry into the search index.
     * Deletes existing entry by ID first to prevent constraint violations across virtual/standard tables.
     */
    fun upsertIndexEntry(
        db: SupportSQLiteDatabase,
        id: String,
        type: String,
        title: String,
        normalizedTitle: String,
        overview: String?,
        genres: String?,
        castDirector: String?,
        aliases: String? = null
    ) {
        upsertIndexEntries(
            db,
            listOf(IndexEntry(id, type, title, normalizedTitle, overview, genres, castDirector, aliases))
        )
    }

    /**
     * Upserts multiple search-index entries in one transaction.
     */
    fun upsertIndexEntries(db: SupportSQLiteDatabase, entries: List<IndexEntry>) {
        if (entries.isEmpty()) return
        db.beginTransaction()
        try {
            entries.forEach { entry ->
                db.execSQL("DELETE FROM fts_search_index WHERE id = ?", arrayOf(entry.id))
                db.execSQL(
                    "INSERT INTO fts_search_index(id, type, title, normalized_title, overview, genres, cast_director, aliases) " +
                            "VALUES(?, ?, ?, ?, ?, ?, ?, ?)",
                    arrayOf(
                        entry.id,
                        entry.type,
                        entry.title,
                        entry.normalizedTitle,
                        entry.overview ?: "",
                        entry.genres ?: "",
                        entry.castDirector ?: "",
                        entry.aliases ?: ""
                    )
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Deletes an entry from the search index.
     */
    fun deleteIndexEntry(db: SupportSQLiteDatabase, id: String) {
        db.execSQL("DELETE FROM fts_search_index WHERE id = ?", arrayOf(id))
    }

    /**
     * Formats raw query terms into FTS match expressions (e.g. "Spider man" -> "spider* man*").
     */
    fun formatFtsQuery(query: String): String {
        val normalized = query.lowercase().replace(Regex("[^a-z0-9\\s]"), " ").trim()
        if (normalized.isEmpty()) return ""
        return normalized.split(Regex("\\s+")).joinToString(" ") { "$it*" }
    }

    /**
     * Executes a search on the search index, applying the correct querying syntax for the active mode.
     */
    fun search(db: SupportSQLiteDatabase, query: String, limit: Int = 50): List<Map<String, String>> {
        val cleanQuery = query.lowercase().trim()
        if (cleanQuery.isEmpty()) return emptyList()
        val safeLimit = limit.coerceIn(1, 200)

        val results = mutableListOf<Map<String, String>>()

        when (activeMode) {
            FtsMode.FTS5, FtsMode.FTS4 -> {
                val ftsQuery = formatFtsQuery(query)
                if (ftsQuery.isEmpty()) return emptyList()

                val cursor = db.query(
                    "SELECT id, type, title, normalized_title, overview, genres, cast_director, aliases " +
                            "FROM fts_search_index " +
                            "WHERE fts_search_index MATCH ? LIMIT ?",
                    arrayOf<Any?>(ftsQuery, safeLimit)
                )
                cursor.use { c ->
                    while (c.moveToNext()) {
                        results.add(
                            mapOf(
                                "id" to c.getString(0),
                                "type" to c.getString(1),
                                "title" to c.getString(2),
                                "normalized_title" to c.getString(3),
                                "overview" to c.getString(4),
                                "genres" to c.getString(5),
                                "cast_director" to c.getString(6),
                                "aliases" to c.getString(7)
                            )
                        )
                    }
                }
            }
            FtsMode.STANDARD -> {
                val normalizedQuery = cleanQuery.replace(Regex("[^a-z0-9\\s]"), "").trim()
                if (normalizedQuery.isEmpty()) return emptyList()

                val cursor = db.query(
                    "SELECT id, type, title, normalized_title, overview, genres, cast_director, aliases " +
                            "FROM fts_search_index " +
                            "WHERE normalized_title LIKE ? OR aliases LIKE ? LIMIT ?",
                    arrayOf<Any?>("$normalizedQuery%", "%$normalizedQuery%", safeLimit)
                )
                cursor.use { c ->
                    while (c.moveToNext()) {
                        results.add(
                            mapOf(
                                "id" to c.getString(0),
                                "type" to c.getString(1),
                                "title" to c.getString(2),
                                "normalized_title" to c.getString(3),
                                "overview" to c.getString(4),
                                "genres" to c.getString(5),
                                "cast_director" to c.getString(6),
                                "aliases" to c.getString(7)
                            )
                        )
                    }
                }
            }
        }
        return results
    }

    private fun searchIndexNeedsAliasRebuild(db: SupportSQLiteDatabase): Boolean {
        return runCatching {
            db.query("PRAGMA table_info(fts_search_index)").use { cursor ->
                var sawColumn = false
                var hasAliases = false
                while (cursor.moveToNext()) {
                    sawColumn = true
                    if (cursor.getString(1) == "aliases") {
                        hasAliases = true
                    }
                }
                sawColumn && !hasAliases
            }
        }.getOrDefault(false)
    }

    private fun checkSearchIndexTypeMismatch(db: SupportSQLiteDatabase, mode: FtsMode): Boolean {
        return runCatching {
            db.query("SELECT sql FROM sqlite_master WHERE type='table' AND name='fts_search_index'").use { cursor ->
                if (cursor.moveToFirst()) {
                    val sql = cursor.getString(0).orEmpty().lowercase()
                    when (mode) {
                        FtsMode.FTS5 -> !sql.contains("using fts5")
                        FtsMode.FTS4 -> !sql.contains("using fts4")
                        FtsMode.STANDARD -> sql.contains("using fts5") || sql.contains("using fts4")
                    }
                } else false
            }
        }.getOrDefault(false)
    }

    private fun rebuildFromSourceTables(db: SupportSQLiteDatabase) {
        var attempts = 0
        val maxAttempts = 3
        var lastException: Exception? = null
        
        while (attempts < maxAttempts) {
            attempts++
            try {
                performRebuildInternal(db)
                return
            } catch (e: android.database.sqlite.SQLiteDatabaseLockedException) {
                lastException = e
                if (attempts < maxAttempts) {
                    try { Thread.sleep(100) } catch (_: InterruptedException) {}
                }
            } catch (e: android.database.sqlite.SQLiteException) {
                val msg = e.message?.lowercase() ?: ""
                if (msg.contains("locked") || msg.contains("busy")) {
                    lastException = e
                    if (attempts < maxAttempts) {
                        try { Thread.sleep(100) } catch (_: InterruptedException) {}
                        continue
                    }
                }
                throw e
            }
        }
        throw lastException ?: IllegalStateException("FTS rebuild failed after $maxAttempts attempts")
    }

    private fun performRebuildInternal(db: SupportSQLiteDatabase) {
        // Rebuild media items with proper aliases from MetadataNormalizer
        val mediaEntries = mutableListOf<IndexEntry>()
        db.query("SELECT id, type, title, normalizedTitle, overview, genres, cast, director FROM media_items").use { cursor ->
            while (cursor.moveToNext()) {
                val title = cursor.getString(2)
                val normalizedTitle = cursor.getString(3)
                val aliases = com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
                    .generateTitleAliases(title).joinToString(" ")
                mediaEntries.add(
                    IndexEntry(
                        id = cursor.getString(0),
                        type = cursor.getString(1),
                        title = title,
                        normalizedTitle = normalizedTitle,
                        overview = cursor.getString(4) ?: "",
                        genres = cursor.getString(5) ?: "",
                        castDirector = "${cursor.getString(6) ?: ""},${cursor.getString(7) ?: ""}",
                        aliases = aliases
                    )
                )
            }
        }
        if (mediaEntries.isNotEmpty()) {
            upsertIndexEntries(db, mediaEntries)
        }

        // Rebuild channels with proper aliases from MetadataNormalizer
        val channelEntries = mutableListOf<IndexEntry>()
        db.query("SELECT id, name, category FROM channels").use { cursor ->
            while (cursor.moveToNext()) {
                val name = cursor.getString(1)
                val aliases = com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
                    .generateChannelAliases(name).joinToString(" ")
                channelEntries.add(
                    IndexEntry(
                        id = cursor.getString(0),
                        type = "channel",
                        title = name,
                        normalizedTitle = com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
                            .normalizeChannelName(name),
                        overview = cursor.getString(2) ?: "",
                        genres = "",
                        castDirector = "",
                        aliases = aliases
                    )
                )
            }
        }
        if (channelEntries.isNotEmpty()) {
            upsertIndexEntries(db, channelEntries)
        }
    }
}
