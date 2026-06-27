package com.example.calmsource.core.discoveryengine.models

import kotlinx.serialization.Serializable
import androidx.compose.runtime.Immutable
import kotlinx.collections.immutable.ImmutableList
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.collections.immutable.toImmutableList

@Serializable
data class MediaItem(
    val id: String,
    val type: String, // movie, series, episode, live_program
    val title: String,
    val overview: String? = null,
    val posterUrl: String? = null,
    val rating: Double? = null,
    val releaseYear: Int? = null,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: String? = null,
    val language: String? = null,
    val durationMs: Long? = null,
    val externalIds: Map<String, String> = emptyMap(),
    val source: String? = null,
    val seriesId: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null
)

@Serializable
data class MediaStream(
    val id: String,
    val mediaItemId: String,
    val title: String,
    val url: String,
    val resolution: String? = null,
    val codec: String? = null,
    val quality: String? = null,
    val sizeInBytes: Long? = null,
    val language: String? = null,
    val isSubbed: Boolean = false,
    val isDubbed: Boolean = false,
    val source: String? = null
)

@Serializable
data class IptvChannel(
    val id: String,
    val name: String,
    val logoUrl: String? = null,
    val streamUrl: String,
    val category: String? = null,
    val providerId: String,
    val tvgId: String? = null
)

@Serializable
data class EpgProgram(
    val id: String,
    val channelId: String,
    val title: String,
    val description: String? = null,
    val category: String? = null,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val language: String? = null,
    val episodeNum: String? = null
)

@Serializable
data class LocalProfile(
    val id: String,
    val name: String,
    val avatarUrl: String? = null,
    val createdAt: Long
)

@Serializable
data class WatchEvent(
    val profileId: String,
    val itemId: String,
    val itemType: String,
    val timestamp: Long,
    val progressMs: Long,
    val durationMs: Long,
    val eventType: String // start, stop, progress, completed, quick_skip
)

@Serializable
data class SearchEvent(
    val profileId: String,
    val query: String,
    val timestamp: Long,
    val selectedItemId: String? = null
)

@Serializable
data class UserItemState(
    val profileId: String,
    val itemId: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val lastWatchedAt: Long? = null,
    val progressMs: Long = 0L,
    val durationMs: Long = 0L,
    val watchCount: Int = 0,
    val isCompleted: Boolean = false
)

@Serializable
data class UserChannelState(
    val profileId: String,
    val channelId: String,
    val isFavorite: Boolean = false,
    val isHidden: Boolean = false,
    val lastWatchedAt: Long? = null,
    val watchCount: Int = 0
)

@Serializable
data class ScoreBreakdown(
    val ftsScore: Double = 0.0,
    val exactPrefixBoost: Double = 0.0,
    val aliasBoost: Double = 0.0,
    val qualityBoost: Double = 0.0,
    val profileBoost: Double = 0.0,
    val liveNowBoost: Double = 0.0,
    val vectorSimilarity: Double = 0.0,
    val penalty: Double = 0.0,
    val availabilityScore: Double = 0.0,
    val reasons: List<String> = emptyList()
)

@Serializable
data class SearchResult(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String?,
    val posterUrl: String?, // poster/logo
    val score: Double,
    val source: String,
    val scoreBreakdown: ScoreBreakdown,
    val externalIds: Map<String, String> = emptyMap()
)

@Serializable
data class SuggestionResult(
    val query: String,
    val score: Double,
    val matchedTerm: String?
)

object RecommendationItemImmutableListSerializer : KSerializer<ImmutableList<RecommendationItem>> {
    private val delegate = ListSerializer(RecommendationItem.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun serialize(encoder: Encoder, value: ImmutableList<RecommendationItem>) {
        delegate.serialize(encoder, value)
    }

    override fun deserialize(decoder: Decoder): ImmutableList<RecommendationItem> {
        return delegate.deserialize(decoder).toImmutableList()
    }
}

@Immutable
@Serializable
data class RecommendationItem(
    val id: String,
    val type: String,
    val title: String,
    val score: Double,
    val reason: String,
    val scoreBreakdown: ScoreBreakdown,
    val subtitle: String? = null,
    val posterUrl: String? = null,
    val source: String? = null,
    val externalIds: Map<String, String> = emptyMap()
)

@Immutable
@Serializable
data class RecommendationRow(
    val title: String,
    val rowType: String,
    val items: @Serializable(with = RecommendationItemImmutableListSerializer::class) ImmutableList<RecommendationItem>
)

@Serializable
data class IngestionStats(
    val insertedCount: Int = 0,
    val updatedCount: Int = 0,
    val skippedCount: Int = 0,
    val durationMs: Long = 0
)

@Serializable
data class TasteProfile(
    val profileId: String,
    val genreAffinities: Map<String, Double>,
    val languageAffinities: Map<String, Double>,
    val sourceAffinities: Map<String, Double>
)

@Serializable
data class DiscoveryPack(
    val id: String,
    val name: String,
    val description: String?,
    val manifestUrl: String,
    val isInstalled: Boolean,
    val installedAt: Long?
)

@Serializable
data class SearchQualityMetrics(
    val mrr: Double,
    val hitAt3: Double,
    val precisionAt3: Double
)
