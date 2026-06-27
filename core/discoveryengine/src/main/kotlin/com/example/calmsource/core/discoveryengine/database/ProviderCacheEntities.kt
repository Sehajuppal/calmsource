package com.example.calmsource.core.discoveryengine.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "metadata_cache",
    primaryKeys = ["mediaId", "providerId"],
    indices = [
        Index("expiresAt"),
        Index("providerId")
    ]
)
data class MetadataCacheEntity(
    val mediaId: String,
    val providerId: String,
    val title: String?,
    val originalTitle: String?,
    val aliases: String?,
    val overview: String?,
    val genres: String?,
    val cast: String?,
    val director: String?,
    val runtimeMinutes: Int?,
    val language: String?,
    val country: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val externalIdsJson: String?,
    val collectionJson: String?,
    val seasonEpisodeJson: String?,
    val confidenceScore: Double,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Entity(
    tableName = "ratings_cache",
    primaryKeys = ["mediaId", "providerId"],
    indices = [Index("expiresAt")]
)
data class RatingsCacheEntity(
    val mediaId: String,
    val providerId: String,
    val ratingValue: Double,
    val ratingScale: Double,
    val voteCount: Int?,
    val popularityScore: Double?,
    val qualityScore: Double?,
    val confidenceScore: Double,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Entity(
    tableName = "similar_cache",
    primaryKeys = ["mediaId", "providerId", "similarMediaId"],
    indices = [
        Index("expiresAt"),
        Index("similarMediaId")
    ]
)
data class SimilarCacheEntity(
    val mediaId: String,
    val providerId: String,
    val similarMediaId: String,
    val similarExternalIdsJson: String?,
    val similarTitle: String?,
    val providerScore: Double?,
    val reason: String?,
    val confidenceScore: Double,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Entity(
    tableName = "subtitles_cache",
    indices = [
        Index("mediaId"),
        Index("expiresAt")
    ]
)
data class SubtitlesCacheEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val providerId: String,
    val streamHash: String?,
    val filename: String?,
    val language: String,
    val subtitleUrl: String,
    val subtitleFormat: String?,
    val matchConfidence: Double,
    val fetchedAt: Long,
    val expiresAt: Long
)

@Entity(
    tableName = "availability_cache",
    primaryKeys = ["mediaId", "providerId", "addonId"],
    indices = [Index("expiresAt")]
)
data class AvailabilityCacheEntity(
    val mediaId: String,
    val providerId: String,
    val addonId: String,
    val streamCount: Int,
    val bestQuality: String?,
    val hasSubtitles: Boolean,
    val languagesJson: String?,
    val confidenceScore: Double,
    val lastCheckedAt: Long,
    val expiresAt: Long
)

@Entity(
    tableName = "addon_availability",
    primaryKeys = ["mediaId", "addonId"]
)
data class AddonAvailabilityEntity(
    val mediaId: String,
    val addonId: String,
    val streamCount: Int,
    val bestQuality: String?,
    val hasSubtitles: Boolean,
    val lastCheckedAt: Long,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val reliabilityScore: Double
)
