package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.ProviderHealth
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.model.SourceHealth
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.WatchOption

/**
 * Helpers for building [StreamScoringSignals] from persisted health and playback data.
 */
object StreamScoringSupport {

    private const val ONE_DAY_MS = 24 * 60 * 60 * 1000L

    fun signalsFromHealth(
        sourceHealth: SourceHealth?,
        providerHealthScore: Int? = null,
        providerHealth: ProviderHealth? = null,
        providerPriority: Int? = null,
        streamSuccessCount: Int = 0,
        streamFailureCount: Int = 0,
        sourceSuccessCount: Int = 0,
        sourceFailureCount: Int = 0,
        isDebridCached: Boolean = false,
    ): StreamScoringSignals {
        val now = System.currentTimeMillis()
        return StreamScoringSignals(
            streamSuccessCount = streamSuccessCount,
            streamFailureCount = streamFailureCount,
            sourceSuccessCount = sourceSuccessCount,
            sourceFailureCount = sourceFailureCount,
            isDebridCached = isDebridCached,
            sourceReliabilityTier = sourceHealth?.reliabilityTier,
            lastSuccessWithin24h = sourceHealth?.lastSuccessTime?.let { it > now - ONE_DAY_MS } == true,
            providerHealth = providerHealth,
            providerPriority = providerPriority,
            providerHealthScore = providerHealthScore,
            lastFailureAtMs = sourceHealth?.lastFailureTime ?: 0L,
        )
    }

    /** Batch-load persisted health for ranking hot paths (search, availability, fallback). */
    suspend fun prefetchSourceHealth(sourceIds: List<String>): Map<String, SourceHealth?> {
        if (sourceIds.isEmpty()) return emptyMap()
        return SourceHealthRepository.getSourceHealths(sourceIds.distinct(), readonly = true)
    }

    fun healthKeyForWatchOption(option: WatchOption): String {
        return if (option.type == com.example.calmsource.core.model.SourceType.IPTV) {
            com.example.calmsource.core.model.generateSafeSourceId(option.source.url)
        } else {
            option.id
        }
    }
}

fun PlaybackSource.toStreamSourceForScoring(): StreamSource {
    return StreamSource(
        id = id,
        name = title,
        url = rawUrl,
        extensionId = resolveProviderIdForHealth(),
        resolution = "",
        videoCodec = metadata?.videoCodec,
        audioCodec = metadata?.audioCodec,
        sizeBytes = null,
        seeds = null,
        language = "",
        isSubbed = false,
        isDubbed = false,
        isDualAudio = false,
        headers = headers,
        rawTitle = title
    )
}
