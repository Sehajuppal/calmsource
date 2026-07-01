package com.example.calmsource.ui

import android.content.Context
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.models.RecommendationType
import com.example.calmsource.core.model.AutoplayNextPayload
import com.example.calmsource.core.model.ExtensionHealth
import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.SeriesPlaybackContext
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.SourceType
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import com.example.calmsource.core.model.WatchOption
import com.example.calmsource.core.model.WatchOptionResolver
import com.example.calmsource.core.model.stableSourceIdForWatchOption
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import com.example.calmsource.core.sourceintelligence.ranking.WatchOptionScoring
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

object SeriesAutoplayCoordinator {
    suspend fun resolveNextEpisode(
        context: Context,
        completedRequest: PlaybackRequest,
        profileId: String,
    ): AutoplayNextPayload? = withContext(Dispatchers.IO) {
        if (completedRequest.source.metadata?.isLive == true) return@withContext null

        val seriesContext = completedRequest.seriesContext
            ?: completedRequest.source.metadata?.seriesName?.let { seriesName ->
                SeriesPlaybackContext(
                    seriesId = completedRequest.userMemoryReference?.sourceId ?: completedRequest.source.id,
                    seriesTitle = seriesName,
                    posterUrl = completedRequest.source.metadata?.posterUrl,
                    backdropUrl = completedRequest.source.metadata?.backdropUrl,
                )
            }
            ?: return@withContext null

        val nextEpisode = DiscoveryEngine.getNextEpisodeByTitle(
            profileId = profileId,
            seriesTitle = seriesContext.seriesTitle,
        )
        if (nextEpisode.recommendationType != RecommendationType.NEXT_EPISODE) {
            return@withContext null
        }
        val targetEpisodeId = nextEpisode.targetEpisodeId ?: return@withContext null

        val mediaItem = MediaItem(
            id = seriesContext.seriesId,
            title = seriesContext.seriesTitle,
            type = MediaType.SHOW,
            posterUrl = seriesContext.posterUrl,
            backdropUrl = seriesContext.backdropUrl,
        )
        val watchOptions = loadWatchOptions(mediaItem, targetEpisodeId)
        if (watchOptions.isEmpty()) return@withContext null

        val prefs = UserPreferencesRepository.preferences.value
        val strategy = if (prefs.preferHighestQuality) {
            SortingPreference.HIGHEST_QUALITY
        } else {
            SortingPreference.BEST_MATCH
        }
        val extensions = ExtensionRepository.getExtensions().associateBy { it.id }
        val healthByOptionId = watchOptions.associate { option ->
            option.id to SourceHealthRepository.getSourceHealth(
                StreamScoringSupport.healthKeyForWatchOption(option),
                readonly = true,
            )
        }
        val providerHealthByExtension = watchOptions
            .map { it.source.extensionId }
            .distinct()
            .associateWith { extensionId ->
                SourceHealthRepository.getProviderHealth(extensionId, readonly = true)
            }
        val scored = WatchOptionScoring.scoreWatchOptionsDetailed(
            options = watchOptions,
            strategy = strategy,
            prefs = prefs,
            deviceProfile = DeviceStreamProfile.forPlayback(isTelevision = false, prefs = prefs),
            signalsFor = { option ->
                val extension = extensions[option.source.extensionId]
                val providerHealth = when (extension?.health) {
                    ExtensionHealth.ACTIVE -> com.example.calmsource.core.model.ProviderHealth.HEALTHY
                    ExtensionHealth.SLOW -> com.example.calmsource.core.model.ProviderHealth.SLOW
                    ExtensionHealth.FAILED,
                    ExtensionHealth.DISABLED,
                    ExtensionHealth.INVALID_MANIFEST -> com.example.calmsource.core.model.ProviderHealth.FAILED
                    else -> com.example.calmsource.core.model.ProviderHealth.HEALTHY
                }
                StreamScoringSupport.signalsFromHealth(
                    sourceHealth = healthByOptionId[option.id],
                    providerHealth = providerHealth,
                    providerPriority = extension?.priority,
                    providerHealthScore = providerHealthByExtension[option.source.extensionId]?.healthScore,
                )
            },
        )
        val best = scored.firstOrNull()?.option ?: return@withContext null
        val fallbacks = scored.drop(1).take(5).map { it.option }

        val season = nextEpisode.seasonNumber
        val episode = nextEpisode.episodeNumber
        val episodeLabel = if (season != null && episode != null) {
            "S${season}E$episode"
        } else {
            WatchOptionResolver.cleanStreamTitle(best.title, null, best.type.name)
        }

        AutoplayNextPayload(
            request = buildPlaybackRequest(
                option = best,
                mediaItem = mediaItem,
                seriesContext = seriesContext,
                targetEpisodeId = targetEpisodeId,
                season = season,
                episode = episode,
                episodeLabel = episodeLabel,
            ),
            fallbackSources = fallbacks.map { option ->
                buildPlaybackSource(option, mediaItem, seriesContext, season, episode, episodeLabel)
            },
            episodeLabel = episodeLabel,
        )
    }

    private suspend fun loadWatchOptions(mediaItem: MediaItem, episodeId: String): List<WatchOption> {
        val localSources = buildList {
            IPTVRepository.findIptvStreamSource(episodeId)?.let(::add)
            addAll(
                IPTVRepository.findIptvStreamSources(mediaItem.id, mediaItem.title)
                    .filter { it.id == episodeId },
            )
        }.distinctBy { it.id }
        val options = WatchOptionResolver.buildWatchOptions(localSources).toMutableList()

        val activeExtensions = ExtensionRepository.getExtensions()
        val extensionResolution = withTimeoutOrNull(12_000L) {
            ExtensionRepository.lookupMediaStreams(mediaItem, activeExtensions, episodeId = episodeId)
                .first { it.streamSources.isNotEmpty() || it.errors.isNotEmpty() }
        } ?: return options.distinctBy { it.id }

        val extensionOptions = WatchOptionResolver.buildWatchOptions(extensionResolution.streamSources)
        extensionOptions.forEach { option ->
            if (options.none { it.id == option.id || it.source.url == option.source.url }) {
                options.add(option)
            }
        }
        return options.distinctBy { it.id }
    }

    private fun buildPlaybackRequest(
        option: WatchOption,
        mediaItem: MediaItem,
        seriesContext: SeriesPlaybackContext,
        targetEpisodeId: String,
        season: Int?,
        episode: Int?,
        episodeLabel: String,
    ): PlaybackRequest = PlaybackRequest(
        source = buildPlaybackSource(option, mediaItem, seriesContext, season, episode, episodeLabel),
        startPositionMs = 0L,
        playWhenReady = true,
        userMemoryReference = UserMemoryReference(
            itemKey = targetEpisodeId,
            contentType = UserMemoryContentType.SHOW,
            title = "${seriesContext.seriesTitle} - $episodeLabel",
        ),
        seriesContext = seriesContext,
    )

    private fun buildPlaybackSource(
        option: WatchOption,
        mediaItem: MediaItem,
        seriesContext: SeriesPlaybackContext,
        season: Int?,
        episode: Int?,
        episodeLabel: String,
    ): PlaybackSource = PlaybackSource(
        id = option.id,
        type = when (option.type) {
            SourceType.IPTV -> PlaybackSourceType.IPTV
            SourceType.EXTENSION -> PlaybackSourceType.EXTENSION
            SourceType.DEBRID -> PlaybackSourceType.DEBRID_RESOLVED
        },
        title = WatchOptionResolver.cleanStreamTitle(option.title, null, option.type.name),
        rawUrl = option.source.url,
        metadata = PlaybackItemMetadata(
            title = "${seriesContext.seriesTitle} - $episodeLabel",
            posterUrl = mediaItem.posterUrl,
            backdropUrl = mediaItem.backdropUrl,
            isLive = option.type == SourceType.IPTV &&
                option.source.resolution.equals("Live", ignoreCase = true),
            seriesName = seriesContext.seriesTitle,
            seasonNumber = season,
            episodeNumber = episode,
            containerFormat = option.source.name.substringAfterLast('.', "").takeIf { it.length in 2..5 },
            videoCodec = option.source.videoCodec,
            audioCodec = option.source.audioCodec,
        ),
        headers = option.source.headers,
        allowInsecureHttp = (option.type == SourceType.IPTV &&
            option.source.url.startsWith("xtream://")) ||
            ((option.type == SourceType.EXTENSION || option.type == SourceType.DEBRID) &&
                option.source.url.startsWith("http://", ignoreCase = true)),
        stableSourceId = stableSourceIdForWatchOption(option.type, option.source.url, option.id),
    )
}
