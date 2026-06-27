package com.example.calmsource.core.discoveryengine.providers

/**
 * Provider contract. All `fetch*` functions are `suspend` and must NEVER
 * throw — every code path returns a [ProviderResult].
 */
interface MetadataProvider {
    val providerId: String
    suspend fun fetchMetadata(mediaId: String, ids: ExternalIdSet): ProviderResult<EnrichedMetadata>
}

interface RatingProvider {
    val providerId: String
    suspend fun fetchRatings(mediaId: String, ids: ExternalIdSet): ProviderResult<List<RatingEntry>>
}

interface SimilarProvider {
    val providerId: String
    suspend fun fetchSimilar(mediaId: String, ids: ExternalIdSet, limit: Int = 10): ProviderResult<List<SimilarEntry>>
}

interface SubtitleProvider {
    val providerId: String
    suspend fun fetchSubtitles(mediaId: String, ids: ExternalIdSet, languageHints: List<String> = emptyList()): ProviderResult<List<SubtitleEntry>>
}

interface AvailabilityProvider {
    val providerId: String
    suspend fun checkAvailability(mediaId: String, ids: ExternalIdSet, addonIds: List<String>): ProviderResult<List<AvailabilityEntry>>
}

/**
 * Provider layer never *selects* a stream for playback. It only describes
 * streams for ranking / Play Best purposes. Existing IPTVRepository and
 * Stremio playback paths remain authoritative for actual playback.
 */
interface StreamProvider {
    val providerId: String
    suspend fun describeStreams(mediaId: String, ids: ExternalIdSet, addonId: String? = null): ProviderResult<List<StreamDescriptor>>
}

interface ArtworkProvider {
    val providerId: String
    suspend fun fetchArtwork(mediaId: String, ids: ExternalIdSet): ProviderResult<ArtworkEntry>
}

interface CatalogProvider {
    val providerId: String
    suspend fun fetchCatalog(catalogId: String, type: String, extra: Map<String, String> = emptyMap()): ProviderResult<List<CatalogItemPreview>>
}

/** A lightweight catalog preview that doesn't pull in MediaItem. */
data class CatalogItemPreview(
    val id: String,
    val type: String,
    val name: String,
    val posterUrl: String?,
    val backgroundUrl: String? = null,
    val imdbId: String? = null
)
