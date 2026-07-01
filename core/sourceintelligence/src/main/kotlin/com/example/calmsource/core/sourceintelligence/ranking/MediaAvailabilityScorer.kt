package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.SourceHealth
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences

/**
 * Maps cached per-media streams to a single availability signal using the unified
 * [StreamScoringEngine]. Used by discovery search and recommendation ranking.
 */
data class MediaAvailabilityResult(
    /** Additive boost for search / recommendation totals (roughly 0..100). */
    val additiveScore: Double,
    /** Normalized 0..1 signal for enrichment multipliers. */
    val normalizedSignal: Double,
)

object MediaAvailabilityScorer {

    fun scoreFromStreams(
        streams: List<StreamSource>,
        prefs: UserPreferences = UserPreferences(),
        preferredAudio: List<String> = emptyList(),
        preferredSub: List<String> = emptyList(),
        streamSuccessCount: (String) -> Int = { 0 },
        streamFailureCount: (String) -> Int = { 0 },
        sourceSuccessCount: (String) -> Int = { 0 },
        sourceFailureCount: (String) -> Int = { 0 },
        sourceHealthById: Map<String, SourceHealth?> = emptyMap(),
        providerCacheAvailability: Double = 0.0,
        deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
    ): MediaAvailabilityResult {
        val providerBoost = providerCacheAvailability.coerceIn(0.0, 1.0) *
            StreamScoringConstants.MEDIA_AVAILABILITY_PROVIDER_CACHE_MAX

        if (streams.isEmpty()) {
            val additive = providerBoost
            return MediaAvailabilityResult(
                additiveScore = additive,
                normalizedSignal = normalizeAdditive(additive),
            )
        }

        val ranked = StreamScoringEngine.scoreBatch(
            sources = streams,
            strategy = SortingPreference.BEST_MATCH,
            prefs = prefs,
            preferredAudio = preferredAudio,
            preferredSub = preferredSub,
            signalsFor = { source ->
                StreamScoringSupport.signalsFromHealth(
                    sourceHealth = sourceHealthById[source.id],
                    streamSuccessCount = streamSuccessCount(source.id),
                    streamFailureCount = streamFailureCount(source.id),
                    sourceSuccessCount = sourceSuccessCount(source.extensionId),
                    sourceFailureCount = sourceFailureCount(source.extensionId),
                )
            },
            deviceProfile = deviceProfile,
        )

        val topScore = ranked.firstOrNull()?.second ?: 0.0
        val topBoost = (topScore * StreamScoringConstants.MEDIA_AVAILABILITY_TOP_STREAM_SCALE)
            .coerceIn(0.0, StreamScoringConstants.MEDIA_AVAILABILITY_TOP_STREAM_CAP)
        val countBonus = (streams.size * StreamScoringConstants.MEDIA_AVAILABILITY_STREAM_COUNT_PER)
            .coerceAtMost(StreamScoringConstants.MEDIA_AVAILABILITY_STREAM_COUNT_CAP)

        val additive = topBoost + countBonus + providerBoost
        return MediaAvailabilityResult(
            additiveScore = additive,
            normalizedSignal = normalizeAdditive(additive),
        )
    }

    fun channelAvailability(): MediaAvailabilityResult {
        val additive = StreamScoringConstants.MEDIA_AVAILABILITY_CHANNEL_DEFAULT
        return MediaAvailabilityResult(
            additiveScore = additive,
            normalizedSignal = normalizeAdditive(additive),
        )
    }

    fun normalizeAdditive(additiveScore: Double): Double {
        return (additiveScore / StreamScoringConstants.MEDIA_AVAILABILITY_NORMALIZE_DIVISOR)
            .coerceIn(0.0, 1.0)
    }
}
