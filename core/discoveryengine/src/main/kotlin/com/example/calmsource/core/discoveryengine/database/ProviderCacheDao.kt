package com.example.calmsource.core.discoveryengine.database

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
interface ProviderCacheDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertMetadata(entity: MetadataCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertRating(entity: RatingsCacheEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSimilar(entities: List<SimilarCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertSubtitles(entities: List<SubtitlesCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAvailability(entities: List<AvailabilityCacheEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun upsertAddonAvailability(entities: List<AddonAvailabilityEntity>)

    @Query("SELECT * FROM metadata_cache WHERE mediaId = :mediaId AND expiresAt > :now")
    fun getMetadata(mediaId: String, now: Long): List<MetadataCacheEntity>

    @Query("SELECT * FROM ratings_cache WHERE mediaId = :mediaId AND expiresAt > :now")
    fun getRatings(mediaId: String, now: Long): List<RatingsCacheEntity>

    @Query("SELECT * FROM similar_cache WHERE mediaId = :mediaId AND expiresAt > :now ORDER BY providerScore DESC")
    fun getSimilar(mediaId: String, now: Long): List<SimilarCacheEntity>

    @Query("SELECT * FROM subtitles_cache WHERE mediaId = :mediaId AND expiresAt > :now ORDER BY matchConfidence DESC")
    fun getSubtitles(mediaId: String, now: Long): List<SubtitlesCacheEntity>

    @Query("SELECT * FROM availability_cache WHERE mediaId = :mediaId AND expiresAt > :now")
    fun getAvailability(mediaId: String, now: Long): List<AvailabilityCacheEntity>

    @Query("DELETE FROM similar_cache WHERE mediaId = :mediaId AND providerId = :providerId")
    fun deleteSimilarForProvider(mediaId: String, providerId: String)

    @Query("DELETE FROM subtitles_cache WHERE mediaId = :mediaId AND providerId = :providerId")
    fun deleteSubtitlesForProvider(mediaId: String, providerId: String)

    @Query("DELETE FROM availability_cache WHERE mediaId = :mediaId AND providerId = :providerId")
    fun deleteAvailabilityForProvider(mediaId: String, providerId: String)

    @Query("DELETE FROM metadata_cache WHERE expiresAt < :now")
    fun pruneMetadata(now: Long): Int

    @Query("DELETE FROM ratings_cache WHERE expiresAt < :now")
    fun pruneRatings(now: Long): Int

    @Query("DELETE FROM similar_cache WHERE expiresAt < :now")
    fun pruneSimilar(now: Long): Int

    @Query("DELETE FROM subtitles_cache WHERE expiresAt < :now")
    fun pruneSubtitles(now: Long): Int

    @Query("DELETE FROM availability_cache WHERE expiresAt < :now")
    fun pruneAvailability(now: Long): Int

    @Query("DELETE FROM metadata_cache")
    fun clearMetadata()

    @Query("DELETE FROM ratings_cache")
    fun clearRatings()

    @Query("DELETE FROM similar_cache")
    fun clearSimilar()

    @Query("DELETE FROM subtitles_cache")
    fun clearSubtitles()

    @Query("DELETE FROM availability_cache")
    fun clearAvailability()

    @Query("DELETE FROM addon_availability")
    fun clearAddonAvailability()

    @Transaction
    fun clearAll() {
        clearMetadata()
        clearRatings()
        clearSimilar()
        clearSubtitles()
        clearAvailability()
        clearAddonAvailability()
    }
}
