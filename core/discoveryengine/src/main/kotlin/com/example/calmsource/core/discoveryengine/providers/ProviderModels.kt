package com.example.calmsource.core.discoveryengine.providers

/**
 * Result of a single provider call.
 *
 * - [Success] means the network round-trip succeeded and the provider
 *   returned usable data (which may itself be empty).
 * - [CacheOnly] means the caller should use cached data only and skip
 *   the network call (e.g. local-only mode, or the provider is disabled
 *   and we want to fall back to cache without a network round-trip).
 * - [Timeout] means the call exceeded its timeout; reliability is decremented
 *   but the call is retriable.
 * - [Failure] means a non-timeout error (parse, HTTP 5xx, missing fields).
 * - [Skipped] means the provider does not support this request type.
 * - [Disabled] means the provider is disabled in user settings.
 * - [LocalOnly] means local-only mode is on and this provider would otherwise
 *   make a network call.
 */
sealed class ProviderResult<out T> {
    data class Success<T>(val value: T, val cacheable: Boolean = true) : ProviderResult<T>()
    data class CacheOnly<T>(val cached: T? = null) : ProviderResult<T>()
    data object Timeout : ProviderResult<Nothing>()
    data class Failure(val errorCode: String, val message: String? = null) : ProviderResult<Nothing>()
    data class Skipped(val reason: String) : ProviderResult<Nothing>()
    data object Disabled : ProviderResult<Nothing>()
    data object LocalOnly : ProviderResult<Nothing>()
}

/** Enriched metadata returned by a [MetadataProvider]. */
data class EnrichedMetadata(
    val title: String?,
    val originalTitle: String?,
    val aliases: List<String> = emptyList(),
    val overview: String?,
    val genres: List<String> = emptyList(),
    val cast: List<String> = emptyList(),
    val director: String?,
    val runtimeMinutes: Int?,
    val language: String?,
    val country: String?,
    val posterUrl: String?,
    val backdropUrl: String?,
    val externalIds: Map<String, String> = emptyMap(),
    val collection: String? = null,
    val seasonEpisode: String? = null
)

/** A single rating entry for a media item. */
data class RatingEntry(
    val value: Double,
    val scale: Double = 10.0,
    val voteCount: Int? = null,
    val popularity: Double? = null
)

/** A single similar-title entry. */
data class SimilarEntry(
    val similarMediaId: String,
    val similarTitle: String?,
    val externalIds: Map<String, String> = emptyMap(),
    val providerScore: Double? = null,
    val reason: String? = null
)

/** A single subtitle entry. */
data class SubtitleEntry(
    val id: String,
    val language: String,
    val url: String,
    val format: String? = null,
    val matchConfidence: Double = 1.0,
    val streamHash: String? = null,
    val filename: String? = null
)

/** Lightweight per-addon availability signal. The `StreamSource` model in
 *  core:model is the source of truth for actual playback — this entry only
 *  records counts / best quality for ranking purposes. */
data class AvailabilityEntry(
    val addonId: String,
    val streamCount: Int,
    val bestQuality: String?,
    val hasSubtitles: Boolean,
    val languages: List<String> = emptyList()
)

/** Lightweight stream descriptor. Provider layer never returns playback
 *  ready-to-play streams — those flow through existing IPTVRepository /
 *  extension plumbing. This descriptor is purely for enrichment / ranking. */
data class StreamDescriptor(
    val title: String?,
    val url: String?,
    val infoHash: String?,
    val quality: String?,
    val language: String?,
    val hasSubtitles: Boolean
)

/** Artwork entry for poster / backdrop / logo / thumbnail. */
data class ArtworkEntry(
    val posterUrl: String?,
    val backdropUrl: String?,
    val logoUrl: String?,
    val thumbnailUrl: String?
)

/** External ID set used to look up items across providers. */
data class ExternalIdSet(
    val imdbId: String? = null,
    val tmdbId: String? = null,
    val tvdbId: String? = null,
    val kitsuId: String? = null,
    val malId: String? = null,
    val custom: Map<String, String> = emptyMap()
) {
    fun isEmpty(): Boolean = imdbId == null && tmdbId == null && tvdbId == null &&
        kitsuId == null && malId == null && custom.isEmpty()
}

/** A summary row surfaced in the Advanced settings provider list. */
data class ProviderStatusRow(
    val providerId: String,
    val name: String,
    val type: ProviderType,
    val kind: ProviderKind,
    val isEnabled: Boolean,
    val isSystemProvider: Boolean,
    val isUserInstalled: Boolean,
    val priority: Int,
    val reliabilityScore: Double,
    val failureCount: Int,
    val lastSuccessAt: Long?,
    val lastFailureAt: Long?,
    val capabilities: Set<ProviderType>
)
