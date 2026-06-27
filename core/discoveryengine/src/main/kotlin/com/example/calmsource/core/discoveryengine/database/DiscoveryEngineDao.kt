package com.example.calmsource.core.discoveryengine.database

import androidx.room.*

data class PlaybackCount(
    val streamId: String,
    val count: Int
)

@Dao
interface DiscoveryEngineDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMediaItems(items: List<MediaItemEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertExternalIds(ids: List<MediaExternalIdEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMediaStreams(streams: List<MediaStreamEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertChannels(channels: List<ChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEpgPrograms(programs: List<EpgProgramEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertWatchEvent(event: WatchEventEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertSearchEvent(event: SearchEventEntity)

    @Query(
        """
        SELECT * FROM search_events
        WHERE profileId = :profileId AND selectedItemId IS NOT NULL
        ORDER BY timestamp DESC
        LIMIT :limit
        """
    )
    fun getRecentSelectedSearchEvents(profileId: String, limit: Int): List<SearchEventEntity>

    @Query("DELETE FROM epg_programs WHERE endTime < :cutoffTime")
    fun clearExpiredEpgPrograms(cutoffTime: Long): Int

    @Query("SELECT COUNT(*) FROM profiles")
    fun getProfileCount(): Int

    @Query("SELECT COUNT(*) FROM media_items")
    fun getMediaItemCount(): Int

    @Query("SELECT COUNT(*) FROM media_streams")
    fun getMediaStreamCount(): Int

    @Query("SELECT COUNT(*) FROM channels")
    fun getChannelCount(): Int

    @Query("SELECT COUNT(*) FROM epg_programs")
    fun getEpgProgramCount(): Int

    @Query("SELECT COUNT(*) FROM watch_events")
    fun getWatchEventCount(): Int

    @Query("SELECT COUNT(*) FROM search_events")
    fun getSearchEventCount(): Int

    @Query("SELECT COUNT(*) FROM user_item_state")
    fun getUserItemStateCount(): Int

    @Query("SELECT COUNT(*) FROM user_channel_state")
    fun getUserChannelStateCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertProfile(profile: ProfileEntity)

    @Query("SELECT * FROM profiles")
    fun getAllProfiles(): List<ProfileEntity>

    @Query("SELECT * FROM media_items WHERE id = :itemId LIMIT 1")
    fun getMediaItem(itemId: String): MediaItemEntity?

    @Query("SELECT * FROM media_items WHERE seriesId = :seriesId AND type = 'episode' ORDER BY seasonNumber ASC, episodeNumber ASC LIMIT 2000")
    fun getEpisodesForSeries(seriesId: String): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE id IN (:ids) AND type = 'episode' LIMIT :limit")
    fun getEpisodesByIds(ids: List<String>, limit: Int = 500): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE type = 'episode' AND normalizedTitle LIKE :query || '%' LIMIT :limit")
    fun getEpisodesByTitlePrefix(query: String, limit: Int = 100): List<MediaItemEntity>

    @Query("""
        SELECT w.* FROM watch_events w
        INNER JOIN (
            SELECT itemId, MAX(timestamp) as max_ts 
            FROM watch_events 
            WHERE profileId = :profileId 
            GROUP BY itemId
        ) latest ON w.itemId = latest.itemId AND w.timestamp = latest.max_ts
        WHERE w.profileId = :profileId
    """)
    fun getLatestWatchEventsForProfile(profileId: String): List<WatchEventEntity>

    @Query("SELECT * FROM user_item_state WHERE profileId = :profileId")
    fun getUserItemStatesForProfile(profileId: String): List<UserItemStateEntity>

    @Query("SELECT * FROM user_channel_state WHERE profileId = :profileId")
    fun getUserChannelStatesForProfile(profileId: String): List<UserChannelStateEntity>

    @Query("SELECT * FROM user_item_state WHERE profileId = :profileId AND itemId = :itemId LIMIT 1")
    fun getUserItemState(profileId: String, itemId: String): UserItemStateEntity?

    @Query("SELECT * FROM user_channel_state WHERE profileId = :profileId AND channelId = :channelId LIMIT 1")
    fun getUserChannelState(profileId: String, channelId: String): UserChannelStateEntity?

    @Query("SELECT * FROM media_items WHERE id IN (:ids)")
    fun getMediaItemsByIds(ids: List<String>): List<MediaItemEntity>

    @Query("SELECT * FROM channels WHERE id IN (:ids)")
    fun getChannelsByIds(ids: List<String>): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSuggestions(suggestions: List<SuggestionEntity>)

    @Query("SELECT * FROM suggestions WHERE query LIKE :prefix || '%' ORDER BY score DESC, updatedAt DESC LIMIT :limit")
    fun getSuggestionsByPrefix(prefix: String, limit: Int): List<SuggestionEntity>

    @Query("SELECT * FROM media_items WHERE normalizedTitle LIKE :prefix || '%' ORDER BY rating DESC, updatedAt DESC LIMIT :limit")
    fun getMediaItemsByPrefix(prefix: String, limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM channels WHERE name LIKE :prefix || '%' ORDER BY updatedAt DESC LIMIT :limit")
    fun getChannelsByPrefix(prefix: String, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM media_items WHERE normalizedTitle LIKE '%' || :normalizedQuery || '%' ORDER BY rating DESC, updatedAt DESC LIMIT :limit")
    fun searchMediaItemsByNormalizedTitle(normalizedQuery: String, limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM channels WHERE LOWER(name) LIKE '%' || LOWER(:query) || '%' ORDER BY updatedAt DESC LIMIT :limit")
    fun searchChannelsByName(query: String, limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM media_items ORDER BY rating DESC, updatedAt DESC LIMIT :limit")
    fun getSearchCandidates(limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM channels ORDER BY updatedAt DESC LIMIT :limit")
    fun getChannelCandidates(limit: Int): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSetting(setting: EngineSettingEntity)

    @Query("SELECT value FROM engine_settings WHERE key = :key LIMIT 1")
    fun getSetting(key: String): String?

    @Query("DELETE FROM profiles WHERE id = :profileId")
    fun deleteProfile(profileId: String)

    @Query("SELECT * FROM profiles WHERE id = :profileId LIMIT 1")
    fun getProfile(profileId: String): ProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertUserItemState(state: UserItemStateEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertUserChannelState(state: UserChannelStateEntity)

    @Query("SELECT * FROM user_item_state WHERE profileId = :profileId AND durationMs > 0 AND (progressMs * 100 / durationMs) BETWEEN 5 AND 90 AND isCompleted = 0 AND isHidden = 0 ORDER BY lastWatchedAt DESC LIMIT :limit")
    fun getContinueWatchingStates(profileId: String, limit: Int): List<UserItemStateEntity>

    @Query("SELECT * FROM media_items WHERE (type = 'movie' OR type = 'series') ORDER BY updatedAt DESC LIMIT :limit")
    fun getRecentlyAdded(limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM media_items WHERE (type = 'movie' OR type = 'series') AND rating IS NOT NULL ORDER BY rating DESC LIMIT :limit")
    fun getTopRated(limit: Int): List<MediaItemEntity>

    @Query("SELECT * FROM epg_programs WHERE startTime <= :time AND endTime >= :time")
    fun getCurrentEpgPrograms(time: Long): List<EpgProgramEntity>

    @Query("SELECT * FROM channels LIMIT :limit")
    fun getAllChannels(limit: Int): List<ChannelEntity>

    @Query("SELECT * FROM watch_events WHERE profileId = :profileId AND itemType = 'channel'")
    fun getChannelWatchEvents(profileId: String): List<WatchEventEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertDiscoveryPack(pack: DiscoveryPackEntity)

    @Query("SELECT * FROM discovery_packs")
    fun getAllDiscoveryPacks(): List<DiscoveryPackEntity>

    @Query("SELECT * FROM discovery_packs WHERE id = :packId LIMIT 1")
    fun getDiscoveryPack(packId: String): DiscoveryPackEntity?

    @Query("DELETE FROM media_items WHERE source = :packId")
    fun deleteMediaItemsBySource(packId: String)

    @Query("DELETE FROM media_streams WHERE source = :packId")
    fun deleteMediaStreamsBySource(packId: String)

    @Query("DELETE FROM discovery_packs WHERE id = :packId")
    fun deleteDiscoveryPack(packId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRecommendationCache(cache: RecommendationCacheEntity)

    @Query("SELECT * FROM recommendation_cache WHERE profileId = :profileId AND cacheKey = :cacheKey LIMIT 1")
    fun getRecommendationCache(profileId: String, cacheKey: String): RecommendationCacheEntity?

    @Query("DELETE FROM recommendation_cache WHERE profileId = :profileId AND cacheKey = :cacheKey")
    fun deleteRecommendationCache(profileId: String, cacheKey: String)

    @Query("DELETE FROM recommendation_cache WHERE profileId = :profileId")
    fun clearRecommendationCacheForProfile(profileId: String)

    @Query("DELETE FROM recommendation_cache")
    fun clearAllRecommendationCache()

    @Query("DELETE FROM recommendation_cache WHERE updatedAt < :cutoffTime")
    fun clearExpiredRecommendationCache(cutoffTime: Long): Int

    @Query("DELETE FROM watch_events WHERE timestamp < :cutoffTime")
    fun pruneWatchEvents(cutoffTime: Long): Int

    @Query("DELETE FROM search_events WHERE timestamp < :cutoffTime")
    fun pruneSearchEvents(cutoffTime: Long): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertEmbeddings(embeddings: List<MediaEmbeddingEntity>)

    @Query("SELECT * FROM media_embeddings LIMIT 10000")
    fun getAllEmbeddings(): List<MediaEmbeddingEntity>

    @Query("SELECT * FROM media_embeddings WHERE itemId = :itemId LIMIT 1")
    fun getEmbedding(itemId: String): MediaEmbeddingEntity?

    @Query("SELECT * FROM media_embeddings WHERE itemId IN (:itemIds)")
    fun getEmbeddingsByIds(itemIds: List<String>): List<MediaEmbeddingEntity>

    @Transaction
    @Query("DELETE FROM media_embeddings WHERE itemId IN (SELECT id FROM media_items WHERE source = :source)")
    fun deleteEmbeddingsBySource(source: String)

    @Transaction
    @Query("DELETE FROM media_external_ids WHERE mediaId IN (SELECT id FROM media_items WHERE source = :source)")
    fun deleteExternalIdsBySource(source: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertPlaybackHistoryEntry(entity: StreamPlaybackHistoryEntity)

    @Query("SELECT COUNT(*) FROM stream_playback_history WHERE streamId = :streamId AND status = 'success'")
    fun getPlaybackSuccessCountForStream(streamId: String): Int

    @Query("SELECT COUNT(*) FROM stream_playback_history WHERE streamId = :streamId AND status = 'failure'")
    fun getPlaybackFailureCountForStream(streamId: String): Int

    @Query("SELECT COUNT(*) FROM stream_playback_history WHERE source = :source AND status = 'success'")
    fun getPlaybackSuccessCountForSource(source: String): Int

    @Query("SELECT COUNT(*) FROM stream_playback_history WHERE source = :source AND status = 'failure'")
    fun getPlaybackFailureCountForSource(source: String): Int

    @Query("DELETE FROM stream_playback_history WHERE mediaId = :mediaId")
    fun deletePlaybackHistoryForMedia(mediaId: String)

    @Query("DELETE FROM stream_playback_history WHERE source = :source")
    fun deletePlaybackHistoryForSource(source: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertUserFeedback(entity: UserFeedbackEntity)

    @Query("SELECT * FROM user_feedbacks WHERE profileId = :profileId AND itemId = :itemId LIMIT 1")
    fun getUserFeedback(profileId: String, itemId: String): UserFeedbackEntity?

    @Query("SELECT * FROM user_feedbacks WHERE profileId = :profileId")
    fun getUserFeedbacksForProfile(profileId: String): List<UserFeedbackEntity>

    @Query("DELETE FROM user_feedbacks WHERE profileId = :profileId AND itemId = :itemId")
    fun deleteUserFeedback(profileId: String, itemId: String)

    @Query("SELECT * FROM media_items WHERE externalId = :externalId LIMIT 1")
    fun getMediaItemByExternalId(externalId: String): MediaItemEntity?

    @Query("SELECT mediaId FROM media_external_ids WHERE idValue = :idValue LIMIT 1")
    fun getMediaIdByExternalId(idValue: String): String?

    @Query("SELECT * FROM media_items WHERE normalizedTitle = :normalizedTitle AND releaseYear = :year LIMIT 1")
    fun getMediaItemByNormalizedTitleAndYear(normalizedTitle: String, year: Int): MediaItemEntity?

    @Query("SELECT * FROM media_streams WHERE mediaId = :mediaId")
    fun getStreamsForMediaItem(mediaId: String): List<MediaStreamEntity>

    @Query("SELECT * FROM media_streams WHERE mediaId IN (:mediaIds)")
    fun getStreamsForMediaItems(mediaIds: List<String>): List<MediaStreamEntity>

    @Query("SELECT streamId, COUNT(*) as count FROM stream_playback_history WHERE streamId IN (:streamIds) AND status = 'success' GROUP BY streamId")
    fun getPlaybackSuccessCounts(streamIds: List<String>): List<PlaybackCount>

    @Query("SELECT streamId, COUNT(*) as count FROM stream_playback_history WHERE streamId IN (:streamIds) AND status = 'failure' GROUP BY streamId")
    fun getPlaybackFailureCounts(streamIds: List<String>): List<PlaybackCount>
}
