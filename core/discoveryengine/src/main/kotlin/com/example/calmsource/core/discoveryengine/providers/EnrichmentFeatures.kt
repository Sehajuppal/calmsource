package com.example.calmsource.core.discoveryengine.providers

/**
 * Provider-derived features consumed by ranking. These are pure cache reads
 * — no network calls happen on the ranking hot path.
 *
 * Default values are "unknown" (0.0 / false / empty). Ranking treats them
 * as soft signals, never as a hard filter.
 */
data class EnrichmentFeatures(
    val mediaId: String,
    val averageRating: Double = 0.0,         // 0..10, 0.0 if unknown
    val ratingCount: Int = 0,
    val popularity: Double = 0.0,           // 0..1, 0.0 if unknown
    val providerCount: Int = 0,              // how many providers contributed
    val similarCount: Int = 0,
    val availabilityCount: Int = 0,         // addons with positive availability
    val bestQuality: String? = null,         // "4k" | "1080p" | "720p" | "480p" | null
    val hasSubtitles: Boolean = false,
    val subtitleLanguages: List<String> = emptyList(),
    val freshnessScore: Double = 0.0,        // 0..1, higher = more recently enriched
    val confidenceScore: Double = 0.0,        // 0..1, how confident the cache is
    /** Optional 0..1 signal from unified stream ranking on cached streams. */
    val streamRankAvailability: Double = 0.0,
) {
    /**
     * Composite availability score. The exact formula is intentionally
     * simple: more addons = higher score, with a small bonus when best
     * quality is HD+ and subtitles are available. When [streamRankAvailability]
     * is present, the stronger of provider-cache and stream-rank signals wins.
     */
    val availabilityScore: Double
        get() {
            val providerSignal = providerAvailabilitySignal()
            val streamSignal = streamRankAvailability.coerceIn(0.0, 1.0)
            return maxOf(providerSignal, streamSignal).coerceAtMost(1.0)
        }

    private fun providerAvailabilitySignal(): Double {
            val n = availabilityCount.coerceAtMost(5).toDouble() / 5.0
            val qBonus = when (bestQuality?.lowercase()) {
                "4k", "2160p" -> 0.2
                "1080p" -> 0.1
                "720p" -> 0.05
                else -> 0.0
            }
            val subBonus = if (hasSubtitles) 0.1 else 0.0
            return (n + qBonus + subBonus).coerceAtMost(1.0)
    }
}