package com.example.calmsource.core.discoveryengine.providers

/**
 * Reads provider cache tables and produces an [EnrichmentFeatures] for a
 * given media id. Pure cache reads — no network calls.
 */
class ProviderFeatureExtractor(private val cache: ProviderCacheStore) {

    fun extract(mediaId: String): EnrichmentFeatures {
        val ratings = cache.getRatings(mediaId)
        val similar = cache.getSimilar(mediaId)
        val availability = cache.getAvailability(mediaId)
        val subtitles = cache.getSubtitles(mediaId)

        val averageRating = if (ratings.isEmpty()) 0.0
        else ratings.map { rating ->
            if (rating.scale == 0.0) 0.0 else rating.value / rating.scale * 10.0
        }.average()
        val ratingCount = ratings.size
        val popularity = ratings.mapNotNull { it.popularity }.maxOrNull() ?: 0.0
        val providerCount = ratings.size + similar.size + availability.size

        return EnrichmentFeatures(
            mediaId = mediaId,
            averageRating = averageRating,
            ratingCount = ratingCount,
            popularity = popularity,
            providerCount = providerCount,
            similarCount = similar.size,
            availabilityCount = availability.size,
            bestQuality = availability.mapNotNull { it.bestQuality }.maxByOrNull { qualityRank(it) },
            hasSubtitles = availability.any { it.hasSubtitles } || subtitles.isNotEmpty(),
            subtitleLanguages = subtitles.map { it.language }.distinct(),
            freshnessScore = 0.0, // populated by callers when they know fetchedAt
            confidenceScore = if (providerCount == 0) 0.0 else minOf(1.0, providerCount / 3.0)
        )
    }

    private fun qualityRank(q: String): Int = when (q.lowercase()) {
        "4k", "2160p" -> 4
        "1080p" -> 3
        "720p" -> 2
        "480p" -> 1
        else -> 0
    }
}