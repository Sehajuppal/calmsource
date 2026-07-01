package com.example.calmsource.core.database.dao

import androidx.room.*
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import com.example.calmsource.core.database.entity.XtreamSeriesEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface XtreamDao {

    // ── VOD ──────────────────────────────────────────────────────────────

    @Query("SELECT * FROM xtream_vod WHERE providerId = :providerId LIMIT 20000")
    fun getVodByProvider(providerId: String): Flow<List<XtreamVodEntity>>

    @Query("SELECT * FROM xtream_vod LIMIT 20000")
    fun getAllVod(): Flow<List<XtreamVodEntity>>

    @Query("SELECT * FROM xtream_vod WHERE id = :id LIMIT 1")
    suspend fun getVodById(id: String): XtreamVodEntity?

    @Query(
        """
        SELECT * FROM xtream_vod
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' ESCAPE '\'
           OR LOWER(
                REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    name, ' ', ''), '-', ''), ':', ''), '.', ''), '(', ''), ')', ''), '_', '')
              ) LIKE '%' || :normalizedQuery || '%' ESCAPE '\'
        ORDER BY addedTimestamp DESC
        LIMIT :limit
        """
    )
    suspend fun searchVod(query: String, normalizedQuery: String, limit: Int): List<XtreamVodEntity>

    @RawQuery(observedEntities = [XtreamVodEntity::class])
    suspend fun searchVodFts(query: SupportSQLiteQuery): List<XtreamVodEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVod(items: List<XtreamVodEntity>): List<Long>

    @Query("DELETE FROM xtream_vod WHERE providerId = :providerId")
    suspend fun deleteVodByProvider(providerId: String): Int

    @Query("SELECT COUNT(*) FROM xtream_vod WHERE providerId = :providerId")
    suspend fun getVodCount(providerId: String): Int

    // ── Series ───────────────────────────────────────────────────────────

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId LIMIT 20000")
    fun getSeriesByProvider(providerId: String): Flow<List<XtreamSeriesEntity>>

    @Query("SELECT * FROM xtream_series LIMIT 20000")
    fun getAllSeries(): Flow<List<XtreamSeriesEntity>>

    @Query("SELECT * FROM xtream_series WHERE id = :id LIMIT 1")
    suspend fun getSeriesById(id: String): XtreamSeriesEntity?

    @Query("SELECT * FROM xtream_series WHERE providerId = :providerId AND seriesId = :seriesId LIMIT 1")
    suspend fun getSeriesByProviderAndSeriesId(providerId: String, seriesId: String): XtreamSeriesEntity?

    @Query(
        """
        SELECT * FROM xtream_series
        WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' ESCAPE '\'
           OR LOWER(
                REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(REPLACE(
                    name, ' ', ''), '-', ''), ':', ''), '.', ''), '(', ''), ')', ''), '_', '')
              ) LIKE '%' || :normalizedQuery || '%' ESCAPE '\'
        LIMIT :limit
        """
    )
    suspend fun searchSeries(query: String, normalizedQuery: String, limit: Int): List<XtreamSeriesEntity>

    @RawQuery(observedEntities = [XtreamSeriesEntity::class])
    suspend fun searchSeriesFts(query: SupportSQLiteQuery): List<XtreamSeriesEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSeries(items: List<XtreamSeriesEntity>): List<Long>

    @Query("DELETE FROM xtream_series WHERE providerId = :providerId")
    suspend fun deleteSeriesByProvider(providerId: String): Int

    @Query("SELECT COUNT(*) FROM xtream_series WHERE providerId = :providerId")
    suspend fun getSeriesCount(providerId: String): Int


    // ── FTS helpers (KSP can't validate FTS virtual tables) ──────────────

    companion object {
        fun ftsVodQuery(rawQuery: String, limit: Int): SupportSQLiteQuery {
            return SimpleSQLiteQuery(
                "SELECT xtream_vod.* FROM xtream_vod JOIN xtream_vod_fts " +
                    "ON xtream_vod.rowid = xtream_vod_fts.rowid " +
                    "WHERE xtream_vod_fts MATCH ? " +
                    "ORDER BY xtream_vod.addedTimestamp DESC LIMIT ?",
                arrayOf(rawQuery as Any, limit as Any)
            )
        }

        fun ftsSeriesQuery(rawQuery: String, limit: Int): SupportSQLiteQuery {
            return SimpleSQLiteQuery(
                "SELECT xtream_series.* FROM xtream_series JOIN xtream_series_fts " +
                    "ON xtream_series.rowid = xtream_series_fts.rowid " +
                    "WHERE xtream_series_fts MATCH ? LIMIT ?",
                arrayOf(rawQuery as Any, limit as Any)
            )
        }

        fun ftsPruneVodOrphansQuery(): SupportSQLiteQuery {
            return SimpleSQLiteQuery(
                "DELETE FROM xtream_vod_fts WHERE rowid NOT IN " +
                    "(SELECT rowid FROM xtream_vod)",
                emptyArray<Any>()
            )
        }

        fun ftsPruneSeriesOrphansQuery(): SupportSQLiteQuery {
            return SimpleSQLiteQuery(
                "DELETE FROM xtream_series_fts WHERE rowid NOT IN " +
                    "(SELECT rowid FROM xtream_series)",
                emptyArray<Any>()
            )
        }
    }
}
