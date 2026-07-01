package com.example.calmsource.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.RoomDatabase.JournalMode
import androidx.room.TypeConverters
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.calmsource.core.database.dao.*
import com.example.calmsource.core.database.entity.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Database(
    entities = [
        DebridAccountEntity::class,
        EPGProgramEntity::class,
        EPGSourceEntity::class,
        ExtensionProviderEntity::class,
        IPTVChannelEntity::class,
        IPTVProviderEntity::class,
        UserPreferencesEntity::class,
        SourceHealthEntity::class,
        ProviderHealthScoreEntity::class,
        XtreamVodEntity::class,
        XtreamSeriesEntity::class,
        ContinueWatchingEntity::class,
        FavoriteEntity::class,
        WatchHistoryEntity::class,
        RecentChannelEntity::class,
        SearchHistoryEntity::class,
        PreferenceSignalEntity::class,
        UserTelemetryEntity::class,
        ProfileEntity::class
    ],
    version = 14,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class CalmSourceDatabase : RoomDatabase() {
    abstract fun iptvDao(): IPTVDao
    abstract fun extensionDao(): ExtensionDao
    abstract fun debridDao(): DebridDao
    abstract fun preferencesDao(): PreferencesDao
    abstract fun healthDao(): HealthDao
    abstract fun xtreamDao(): XtreamDao
    abstract fun userMemoryDao(): UserMemoryDao
    abstract fun userTelemetryDao(): UserTelemetryDao
    abstract fun profileDao(): ProfileDao

    companion object {
        const val DATABASE_NAME = "calmsource_db"

        fun buildDatabase(
            context: Context,
            databaseName: String = DATABASE_NAME
        ): CalmSourceDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                CalmSourceDatabase::class.java,
                databaseName
            )
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : Callback() {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    super.onCreate(db)
                    createXtreamFtsTables(db)
                }

                override fun onDestructiveMigration(db: SupportSQLiteDatabase) {
                    super.onDestructiveMigration(db)
                    db.execSQL("DROP TABLE IF EXISTS `xtream_vod_fts`")
                    db.execSQL("DROP TABLE IF EXISTS `xtream_series_fts`")
                    createXtreamFtsTables(db)
                }

                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    val pragmaError = runCatching {
                        db.execSQL("PRAGMA synchronous = NORMAL;")
                        db.execSQL("PRAGMA foreign_keys = ON;")
                        db.execSQL("PRAGMA busy_timeout = 5000;")
                    }.exceptionOrNull()
                    SlowQueryLogger.installOnOpen(db)
                    CoreDatabaseRuntimeStatus.recordOpen(
                        journalMode = queryPragmaString(db, "PRAGMA journal_mode"),
                        busyTimeoutMs = queryPragmaInt(db, "PRAGMA busy_timeout"),
                        error = pragmaError?.javaClass?.simpleName
                    )
                }
            })
            .openHelperFactory(WindowedHelperFactory())
            .fallbackToDestructiveMigrationFrom(1, 2, 3, 4)
            .addMigrations(MIGRATION_5_6, MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10, MIGRATION_10_11, MIGRATION_11_12, MIGRATION_12_13, MIGRATION_13_14)
            .build()
        }

        fun createXtreamFtsTables(db: SupportSQLiteDatabase) {
            val hasFts5 = runCatching {
                db.execSQL("CREATE VIRTUAL TABLE temp_fts5_migration_check USING fts5(test_col)")
                db.execSQL("DROP TABLE temp_fts5_migration_check")
                true
            }.getOrDefault(false)
            if (hasFts5) {
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts5(
                        name, categoryName,
                        tokenize='unicode61'
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts5(
                        name, categoryName,
                        tokenize='unicode61'
                    )
                    """.trimIndent()
                )
            } else {
                val hasFts4 = runCatching {
                    db.execSQL("CREATE VIRTUAL TABLE temp_fts4_migration_check USING fts4(test_col)")
                    db.execSQL("DROP TABLE temp_fts4_migration_check")
                    true
                }.getOrDefault(false)
                if (hasFts4) {
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_vod_fts` USING fts4(name, categoryName)")
                    db.execSQL("CREATE VIRTUAL TABLE IF NOT EXISTS `xtream_series_fts` USING fts4(name, categoryName)")
                } else {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `xtream_vod_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `xtream_series_fts` (rowid INTEGER PRIMARY KEY, name TEXT, categoryName TEXT)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_vod_fts_name` ON `xtream_vod_fts` (`name`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `idx_xtream_series_fts_name` ON `xtream_series_fts` (`name`)")
                }
            }
        }

        private fun queryPragmaString(db: SupportSQLiteDatabase, sql: String): String {
            return runCatching {
                db.query(sql).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else ""
                }
            }.getOrDefault("")
        }

        private fun queryPragmaInt(db: SupportSQLiteDatabase, sql: String): Int {
            return runCatching {
                db.query(sql).use { cursor ->
                    if (cursor.moveToFirst()) cursor.getInt(0) else 0
                }
            }.getOrDefault(0)
        }
    }
}

data class CoreDatabaseDebugState(
    val openedAtMs: Long = 0L,
    val journalMode: String = "unknown",
    val walEnabled: Boolean = false,
    val busyTimeoutMs: Int = 0,
    val lastOpenError: String? = null
)

object CoreDatabaseRuntimeStatus {
    private val _state = MutableStateFlow(CoreDatabaseDebugState())
    val state: StateFlow<CoreDatabaseDebugState> = _state.asStateFlow()

    fun recordOpen(
        journalMode: String,
        busyTimeoutMs: Int,
        error: String? = null
    ) {
        val normalizedJournal = journalMode.trim().lowercase()
        _state.value = CoreDatabaseDebugState(
            openedAtMs = System.currentTimeMillis(),
            journalMode = normalizedJournal.ifBlank { "unknown" },
            walEnabled = normalizedJournal == "wal",
            busyTimeoutMs = busyTimeoutMs,
            lastOpenError = error
        )
    }
}
