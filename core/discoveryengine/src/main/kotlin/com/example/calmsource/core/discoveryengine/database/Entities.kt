package com.example.calmsource.core.discoveryengine.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "profiles",
    indices = [Index("createdAt")]
)
data class ProfileEntity(
    @PrimaryKey val id: String,
    val name: String,
    val avatarUrl: String?,
    val createdAt: Long,
    val preferredAudioLanguages: String? = null, // Comma-separated, e.g. "pa,hi,en"
    val preferredSubtitleLanguages: String? = null // Comma-separated, e.g. "en"
)

@Entity(
    tableName = "media_items",
    indices = [
        Index("externalId"),
        Index("normalizedTitle"),
        Index("updatedAt"),
        Index("source")
    ]
)
data class MediaItemEntity(
    @PrimaryKey val id: String,
    val type: String, // movie, series, episode, live_program
    val title: String,
    val overview: String?,
    val posterUrl: String?,
    val rating: Double?,
    val releaseYear: Int?,
    val genres: String, // Comma-separated or JSON
    val cast: String, // Comma-separated or JSON
    val director: String?,
    val language: String?,
    val durationMs: Long?,
    val externalId: String?,
    val externalIdsJson: String,
    val source: String?,
    val seriesId: String?,
    val seasonNumber: Int?,
    val episodeNumber: Int?,
    val normalizedTitle: String,
    val updatedAt: Long
)

@Entity(
    tableName = "media_streams",
    indices = [
        Index("mediaId"),
        Index("source"),
        Index("updatedAt")
    ]
)
data class MediaStreamEntity(
    @PrimaryKey val id: String,
    val mediaId: String,
    val title: String,
    val url: String,
    val resolution: String?,
    val codec: String?,
    val quality: String?,
    val sizeInBytes: Long?,
    val language: String?,
    val isSubbed: Boolean,
    val isDubbed: Boolean,
    val source: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "channels",
    indices = [
        Index("providerId"),
        Index("updatedAt")
    ]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val name: String,
    val logoUrl: String?,
    val streamUrl: String,
    val category: String?,
    val providerId: String,
    val tvgId: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "epg_programs",
    indices = [
        Index("channelId"),
        Index("startTime"),
        Index("endTime"),
        Index("updatedAt")
    ]
)
data class EpgProgramEntity(
    @PrimaryKey val id: String,
    val channelId: String,
    val title: String,
    val description: String?,
    val category: String?,
    val startTime: Long,
    val endTime: Long,
    val language: String?,
    val episodeNum: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "watch_events",
    primaryKeys = ["profileId", "itemId", "timestamp"],
    indices = [
        Index("profileId"),
        Index("itemId"),
        Index("timestamp")
    ]
)
data class WatchEventEntity(
    val profileId: String,
    val itemId: String,
    val itemType: String,
    val timestamp: Long,
    val progressMs: Long,
    val durationMs: Long,
    val eventType: String
)

@Entity(
    tableName = "search_events",
    primaryKeys = ["profileId", "timestamp"],
    indices = [
        Index("profileId"),
        Index("timestamp")
    ]
)
data class SearchEventEntity(
    val profileId: String,
    val query: String,
    val timestamp: Long,
    val selectedItemId: String?
)

@Entity(
    tableName = "user_item_state",
    primaryKeys = ["profileId", "itemId"],
    indices = [
        Index("profileId"),
        Index("itemId")
    ]
)
data class UserItemStateEntity(
    val profileId: String,
    val itemId: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val lastWatchedAt: Long?,
    val progressMs: Long,
    val durationMs: Long,
    val watchCount: Int,
    val isCompleted: Boolean
)

@Entity(
    tableName = "user_channel_state",
    primaryKeys = ["profileId", "channelId"],
    indices = [
        Index("profileId"),
        Index("channelId")
    ]
)
data class UserChannelStateEntity(
    val profileId: String,
    val channelId: String,
    val isFavorite: Boolean,
    val isHidden: Boolean,
    val lastWatchedAt: Long?,
    val watchCount: Int
)

@Entity(
    tableName = "suggestions",
    indices = [
        Index("updatedAt")
    ]
)
data class SuggestionEntity(
    @PrimaryKey val query: String,
    val score: Double,
    val matchedTerm: String?,
    val updatedAt: Long
)

@Entity(
    tableName = "recommendation_cache",
    primaryKeys = ["profileId", "cacheKey"],
    indices = [
        Index("profileId"),
        Index("updatedAt")
    ]
)
data class RecommendationCacheEntity(
    val profileId: String,
    val cacheKey: String,
    val data: String,
    val updatedAt: Long
)

@Entity(
    tableName = "discovery_packs",
    indices = [
        Index("isInstalled")
    ]
)
data class DiscoveryPackEntity(
    @PrimaryKey val id: String,
    val name: String,
    val description: String?,
    val manifestUrl: String,
    val checksum: String?,
    val version: Int? = null,
    val lastCheckedAt: Long? = null,
    val isInstalled: Boolean,
    val installedAt: Long?
)

@Entity(
    tableName = "pack_interest_signals",
    primaryKeys = ["profileId", "packId"],
    indices = [
        Index("profileId"),
        Index("packId"),
        Index("lastSignaledAt")
    ]
)
data class PackInterestSignalEntity(
    val profileId: String,
    val packId: String,
    val interestScore: Double,
    val lastSignaledAt: Long
)

@Entity(tableName = "engine_settings")
data class EngineSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

@Entity(
    tableName = "media_embeddings",
    foreignKeys = [
        androidx.room.ForeignKey(
            entity = MediaItemEntity::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = androidx.room.ForeignKey.CASCADE
        )
    ]
)
data class MediaEmbeddingEntity(
    @PrimaryKey val itemId: String,
    val version: Int,
    val dimension: Int,
    val norm: Double,
    val embedding: ByteArray,
    val updatedAt: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as MediaEmbeddingEntity

        if (itemId != other.itemId) return false
        if (version != other.version) return false
        if (dimension != other.dimension) return false
        if (norm != other.norm) return false
        if (!embedding.contentEquals(other.embedding)) return false
        if (updatedAt != other.updatedAt) return false

        return true
    }

    override fun hashCode(): Int {
        var result = itemId.hashCode()
        result = 31 * result + version
        result = 31 * result + dimension
        result = 31 * result + norm.hashCode()
        result = 31 * result + embedding.contentHashCode()
        result = 31 * result + updatedAt.hashCode()
        return result
    }
}

@Entity(
    tableName = "stream_playback_history",
    primaryKeys = ["streamId", "timestamp"],
    indices = [
        Index("mediaId"),
        Index("source"),
        Index("timestamp")
    ]
)
data class StreamPlaybackHistoryEntity(
    val streamId: String,
    val mediaId: String,
    val source: String, // addon / provider ID
    val status: String, // "success", "failure"
    val reason: String?,
    val timestamp: Long
)

@Entity(
    tableName = "user_feedbacks",
    primaryKeys = ["profileId", "itemId"],
    indices = [
        Index("profileId"),
        Index("itemId")
    ]
)
data class UserFeedbackEntity(
    val profileId: String,
    val itemId: String,
    val feedbackType: String, // "not_interested", "prefer_source", etc.
    val timestamp: Long
)

@Entity(
    tableName = "media_external_ids",
    primaryKeys = ["idType", "idValue"],
    indices = [
        Index("mediaId"),
        Index("idValue")
    ]
)
data class MediaExternalIdEntity(
    val mediaId: String,
    val idType: String,  // "imdb", "tmdb", "tvdb", "kitsu", "mal", etc.
    val idValue: String
)
