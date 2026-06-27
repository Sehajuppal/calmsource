package com.example.calmsource.core.discoveryengine.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.calmsource.core.database.WindowedHelperFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Database(
    entities = [
        ProfileEntity::class,
        MediaItemEntity::class,
        MediaStreamEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class,
        WatchEventEntity::class,
        SearchEventEntity::class,
        UserItemStateEntity::class,
        UserChannelStateEntity::class,
        SuggestionEntity::class,
        RecommendationCacheEntity::class,
        DiscoveryPackEntity::class,
        PackInterestSignalEntity::class,
        EngineSettingEntity::class,
        MediaEmbeddingEntity::class,
        StreamPlaybackHistoryEntity::class,
        UserFeedbackEntity::class,
        ProviderRegistryEntity::class,
        MetadataCacheEntity::class,
        RatingsCacheEntity::class,
        SimilarCacheEntity::class,
        SubtitlesCacheEntity::class,
        AvailabilityCacheEntity::class,
        AddonAvailabilityEntity::class,
        ProviderFailureLogEntity::class,
        ProviderUsageLogEntity::class,
        MediaExternalIdEntity::class
    ],
    version = 8,
    exportSchema = true
)
abstract class DiscoveryEngineDatabase : RoomDatabase() {

    abstract fun discoveryDao(): DiscoveryEngineDao
    abstract fun providerRegistryDao(): ProviderRegistryDao
    abstract fun providerCacheDao(): ProviderCacheDao
    abstract fun providerTelemetryDao(): ProviderTelemetryDao

    companion object {
        @Volatile
        private var INSTANCE: DiscoveryEngineDatabase? = null

        fun getInstance(context: Context): DiscoveryEngineDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }
        }

        private fun buildDatabase(context: Context): DiscoveryEngineDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                DiscoveryEngineDatabase::class.java,
                "discovery_engine.db"
            )
            .setJournalMode(JournalMode.WRITE_AHEAD_LOGGING)
            .addCallback(object : Callback() {
                override fun onOpen(db: SupportSQLiteDatabase) {
                    super.onOpen(db)
                    db.execSQL("PRAGMA synchronous = NORMAL;")
                    db.execSQL("PRAGMA foreign_keys = ON;")
                    val pragmaError = runCatching {
                        db.execSQL("PRAGMA busy_timeout = 5000;")
                    }.exceptionOrNull()
                    val journalMode = queryPragmaString(db, "PRAGMA journal_mode")
                    val busyTimeoutMs = queryPragmaInt(db, "PRAGMA busy_timeout")
                    FtsIndexManager.initialize(db)
                    DiscoveryDatabaseRuntimeStatus.recordOpen(
                        journalMode = journalMode,
                        busyTimeoutMs = busyTimeoutMs,
                        ftsMode = FtsIndexManager.activeMode.name,
                        error = pragmaError?.javaClass?.simpleName
                    )
                }
            })
            .addMigrations(DiscoveryEngineMigrations.MIGRATION_5_6, DiscoveryEngineMigrations.MIGRATION_6_7, DiscoveryEngineMigrations.MIGRATION_7_8)
            .fallbackToDestructiveMigration()
            .openHelperFactory(WindowedHelperFactory())
            .build()
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
