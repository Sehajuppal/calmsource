package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.MediaStreamEntity
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringEngine
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object StreamRanker {

    /**
     * Ranks media streams based on unified scoring: quality, language, health,
     * and previous playback success/failure stats.
     */
    suspend fun rank(
        dao: DiscoveryEngineDao,
        profileId: String,
        streams: List<MediaStreamEntity>,
        strategy: SortingPreference = SortingPreference.BEST_MATCH,
        prefs: UserPreferences = UserPreferences(),
        isTelevision: Boolean = false,
    ): List<MediaStreamEntity> = withContext(Dispatchers.Default) {
        if (streams.isEmpty()) return@withContext emptyList()

        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")
            ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")
            ?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        val streamIds = streams.map { it.id }
        val successCounts = dao.getPlaybackSuccessCounts(streamIds).associate { it.streamId to it.count }
        val failureCounts = dao.getPlaybackFailureCounts(streamIds).associate { it.streamId to it.count }
        val sources = streams.mapNotNull { it.source }.distinct()
        val sourceSuccessCounts = if (sources.isNotEmpty()) {
            sources.associateWith { dao.getPlaybackSuccessCountForSource(it) }
        } else emptyMap()
        val sourceFailureCounts = if (sources.isNotEmpty()) {
            sources.associateWith { dao.getPlaybackFailureCountForSource(it) }
        } else emptyMap()

        val sourceHealthById = StreamScoringSupport.prefetchSourceHealth(streamIds)
        val deviceProfile = DeviceStreamProfile.forPlayback(isTelevision, prefs)

        rankWithSignals(
            streams = streams,
            preferredAudio = preferredAudio,
            preferredSub = preferredSub,
            streamSuccessCount = { streamId -> successCounts[streamId] ?: 0 },
            streamFailureCount = { streamId -> failureCounts[streamId] ?: 0 },
            sourceSuccessCount = { source -> sourceSuccessCounts[source] ?: 0 },
            sourceFailureCount = { source -> sourceFailureCounts[source] ?: 0 },
            strategy = strategy,
            prefs = prefs,
            deviceProfile = deviceProfile,
            sourceHealthById = sourceHealthById,
        )
    }

    internal fun rankWithSignals(
        streams: List<MediaStreamEntity>,
        preferredAudio: List<String>,
        preferredSub: List<String>,
        streamSuccessCount: (String) -> Int,
        streamFailureCount: (String) -> Int,
        sourceSuccessCount: (String) -> Int = { 0 },
        sourceFailureCount: (String) -> Int = { 0 },
        strategy: SortingPreference = SortingPreference.BEST_MATCH,
        prefs: UserPreferences = UserPreferences(),
        deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
        sourceHealthById: Map<String, com.example.calmsource.core.model.SourceHealth?> = emptyMap(),
    ): List<MediaStreamEntity> {
        if (streams.isEmpty()) return emptyList()

        val streamSources = streams.map { it.toStreamSource() }
        val rankedSources = StreamScoringEngine.scoreBatch(
            sources = streamSources,
            strategy = strategy,
            prefs = prefs,
            preferredAudio = preferredAudio,
            preferredSub = preferredSub,
            signalsFor = { source ->
                val entity = streams.first { it.id == source.id }
                StreamScoringSupport.signalsFromHealth(
                    sourceHealth = sourceHealthById[entity.id],
                    streamSuccessCount = streamSuccessCount(entity.id),
                    streamFailureCount = streamFailureCount(entity.id),
                    sourceSuccessCount = entity.source?.let { sourceSuccessCount(it) } ?: 0,
                    sourceFailureCount = entity.source?.let { sourceFailureCount(it) } ?: 0,
                )
            },
            deviceProfile = deviceProfile,
        )

        val order = rankedSources.map { it.first.id }
        return order.mapNotNull { id -> streams.find { it.id == id } }
    }
}
