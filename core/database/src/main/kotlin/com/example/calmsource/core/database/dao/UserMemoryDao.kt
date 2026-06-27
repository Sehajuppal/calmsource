package com.example.calmsource.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.calmsource.core.database.entity.ContinueWatchingEntity
import com.example.calmsource.core.database.entity.FavoriteEntity
import com.example.calmsource.core.database.entity.PreferenceSignalEntity
import com.example.calmsource.core.database.entity.RecentChannelEntity
import com.example.calmsource.core.database.entity.SearchHistoryEntity
import com.example.calmsource.core.database.entity.WatchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserMemoryDao {

    @Query("SELECT * FROM continue_watching WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun observeContinueWatching(limit: Int, profileId: String = "default"): Flow<List<ContinueWatchingEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertContinueWatching(item: ContinueWatchingEntity): Long

    @Query("DELETE FROM continue_watching WHERE profileId = :profileId AND itemKey = :itemKey")
    suspend fun removeContinueWatching(itemKey: String, profileId: String = "default"): Int

    @Query("DELETE FROM continue_watching WHERE profileId = :profileId")
    suspend fun clearContinueWatching(profileId: String = "default"): Int

    @Query(
        """
        DELETE FROM continue_watching
        WHERE profileId = :profileId AND itemKey NOT IN (
            SELECT itemKey FROM continue_watching WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimContinueWatching(keep: Int, profileId: String = "default"): Int

    @Query("SELECT * FROM favorites WHERE profileId = :profileId ORDER BY updatedAt DESC LIMIT :limit")
    fun observeFavorites(limit: Int, profileId: String = "default"): Flow<List<FavoriteEntity>>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND itemKey = :itemKey LIMIT 1")
    fun observeFavorite(itemKey: String, profileId: String = "default"): Flow<FavoriteEntity?>

    @Query("SELECT * FROM favorites WHERE profileId = :profileId AND itemKey = :itemKey LIMIT 1")
    suspend fun getFavorite(itemKey: String, profileId: String = "default"): FavoriteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFavorite(item: FavoriteEntity): Long

    @Query("DELETE FROM favorites WHERE profileId = :profileId AND itemKey = :itemKey")
    suspend fun removeFavorite(itemKey: String, profileId: String = "default"): Int

    @Query("DELETE FROM favorites WHERE profileId = :profileId")
    suspend fun clearFavorites(profileId: String = "default"): Int

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeWatchHistory(limit: Int, profileId: String = "default"): Flow<List<WatchHistoryEntity>>

    @Query("SELECT * FROM watch_history WHERE profileId = :profileId AND itemKey = :itemKey LIMIT 1")
    suspend fun getWatchHistory(itemKey: String, profileId: String = "default"): WatchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchHistory(item: WatchHistoryEntity): Long

    @Query("DELETE FROM watch_history WHERE profileId = :profileId AND itemKey = :itemKey")
    suspend fun removeWatchHistory(itemKey: String, profileId: String = "default"): Int

    @Query("DELETE FROM watch_history WHERE profileId = :profileId")
    suspend fun clearWatchHistory(profileId: String = "default"): Int

    @Query(
        """
        DELETE FROM watch_history
        WHERE profileId = :profileId AND itemKey NOT IN (
            SELECT itemKey FROM watch_history WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimWatchHistory(keep: Int, profileId: String = "default"): Int

    @Query("SELECT * FROM recent_channels WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun observeRecentChannels(limit: Int, profileId: String = "default"): Flow<List<RecentChannelEntity>>

    @Query("SELECT * FROM recent_channels WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT 1")
    fun observeLastWatchedChannel(profileId: String = "default"): Flow<RecentChannelEntity?>

    @Query("SELECT * FROM recent_channels WHERE profileId = :profileId AND itemKey = :itemKey LIMIT 1")
    suspend fun getRecentChannel(itemKey: String, profileId: String = "default"): RecentChannelEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecentChannel(item: RecentChannelEntity): Long

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId AND itemKey = :itemKey")
    suspend fun removeRecentChannel(itemKey: String, profileId: String = "default"): Int

    @Query("DELETE FROM recent_channels WHERE profileId = :profileId")
    suspend fun clearRecentChannels(profileId: String = "default"): Int

    @Query(
        """
        DELETE FROM recent_channels
        WHERE profileId = :profileId AND itemKey NOT IN (
            SELECT itemKey FROM recent_channels WHERE profileId = :profileId ORDER BY lastWatchedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimRecentChannels(keep: Int, profileId: String = "default"): Int

    @Query("SELECT * FROM search_history WHERE profileId = :profileId ORDER BY lastSearchedAt DESC LIMIT :limit")
    fun observeSearchHistory(limit: Int, profileId: String = "default"): Flow<List<SearchHistoryEntity>>

    @Query("SELECT * FROM search_history WHERE profileId = :profileId AND normalizedQuery = :normalizedQuery LIMIT 1")
    suspend fun getSearchHistory(normalizedQuery: String, profileId: String = "default"): SearchHistoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSearchHistory(item: SearchHistoryEntity): Long

    @Query("DELETE FROM search_history WHERE profileId = :profileId AND normalizedQuery = :normalizedQuery")
    suspend fun removeSearchHistory(normalizedQuery: String, profileId: String = "default"): Int

    @Query("DELETE FROM search_history WHERE profileId = :profileId")
    suspend fun clearSearchHistory(profileId: String = "default"): Int

    @Query(
        """
        DELETE FROM search_history
        WHERE profileId = :profileId AND normalizedQuery NOT IN (
            SELECT normalizedQuery FROM search_history WHERE profileId = :profileId ORDER BY lastSearchedAt DESC LIMIT :keep
        )
        """
    )
    suspend fun trimSearchHistory(keep: Int, profileId: String = "default"): Int

    @Query("SELECT * FROM preference_signals WHERE profileId = :profileId ORDER BY count DESC, lastSignaledAt DESC LIMIT :limit")
    fun observePreferenceSignals(limit: Int, profileId: String = "default"): Flow<List<PreferenceSignalEntity>>

    @Query(
        """
        SELECT * FROM preference_signals
        WHERE profileId = :profileId AND signalType = :signalType AND signalKey = :signalKey
        LIMIT 1
        """
    )
    suspend fun getPreferenceSignal(signalType: String, signalKey: String, profileId: String = "default"): PreferenceSignalEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreferenceSignal(item: PreferenceSignalEntity): Long

    @Query("DELETE FROM preference_signals WHERE profileId = :profileId")
    suspend fun clearPreferenceSignals(profileId: String = "default"): Int

    @Query(
        """
        DELETE FROM preference_signals
        WHERE profileId = :profileId AND rowid NOT IN (
            SELECT rowid
            FROM preference_signals
            WHERE profileId = :profileId
            ORDER BY lastSignaledAt DESC
            LIMIT :keep
        )
        """
    )
    suspend fun trimPreferenceSignals(keep: Int, profileId: String = "default"): Int
}
