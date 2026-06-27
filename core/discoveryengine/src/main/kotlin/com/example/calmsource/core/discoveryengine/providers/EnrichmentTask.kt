package com.example.calmsource.core.discoveryengine.providers

sealed class EnrichmentTask(
    open val mediaId: String,
    open val profileId: String,
    open val externalIds: ExternalIdSet
) {
    data class FetchMetadata(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class FetchRatings(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class FetchSimilar(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet,
        val limit: Int = 10
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class FetchSubtitles(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet,
        val languageHints: List<String> = emptyList()
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class CheckAvailability(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet,
        val addonIds: List<String> = emptyList()
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class RefreshArtwork(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    data class FullEnrichment(
        override val mediaId: String,
        override val profileId: String,
        override val externalIds: ExternalIdSet
    ) : EnrichmentTask(mediaId, profileId, externalIds)

    val dedupeKey: String
        get() = listOf(
            this::class.qualifiedName.orEmpty(),
            mediaId,
            profileId,
            externalIds.stableKey(),
            taskVariantKey()
        ).joinToString(":")

    private fun taskVariantKey(): String {
        return when (this) {
            is FetchSimilar -> "limit=$limit"
            is FetchSubtitles -> "languages=${languageHints.sorted().joinToString(",")}"
            is CheckAvailability -> "addons=${addonIds.sorted().joinToString(",")}"
            else -> ""
        }
    }
}

internal fun ExternalIdSet.stableKey(): String {
    return listOf(
        imdbId.orEmpty(),
        tmdbId.orEmpty(),
        tvdbId.orEmpty(),
        kitsuId.orEmpty(),
        malId.orEmpty(),
        custom.toSortedMap().entries.joinToString(",") { "${it.key}=${it.value}" }
    ).joinToString("|")
}
