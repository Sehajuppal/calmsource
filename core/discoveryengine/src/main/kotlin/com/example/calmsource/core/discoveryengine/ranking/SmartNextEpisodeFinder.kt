package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.models.NextEpisodeResult
import com.example.calmsource.core.discoveryengine.models.RecommendationType
import com.example.calmsource.core.discoveryengine.normalization.EpisodeParser
import com.example.calmsource.core.discoveryengine.normalization.ShowExtractor

object SmartNextEpisodeFinder {

    private data class EpisodeWrapper(
        val entity: MediaItemEntity,
        val season: Int,
        val episode: Int
    )

    /**
     * Resolves the next episode recommendation for a given series explicitly by seriesId.
     */
    fun findNextEpisode(
        dao: DiscoveryEngineDao,
        profileId: String,
        seriesId: String,
        threshold: Double = 0.85
    ): NextEpisodeResult {
        // 1. Resolve episodes by series ID
        var episodes = dao.getEpisodesForSeries(seriesId)
        var confidence = 1.0
        var reasonPrefix = "Explicit series lookup"

        // Fallback: title-based matching if no episodes are explicitly linked
        if (episodes.isEmpty()) {
            val seriesItem = dao.getMediaItem(seriesId)
            if (seriesItem != null) {
                val showName = ShowExtractor.extractShowName(seriesItem.title)
                if (showName.isNotEmpty()) {
                    episodes = dao.getEpisodesByTitlePrefix(showName)
                    confidence = 0.7
                    reasonPrefix = "Fuzzy title fallback matching"
                }
            }
        }

        return evaluateEpisodes(dao, profileId, episodes, threshold, confidence, reasonPrefix)
    }

    /**
     * Resolves the next episode recommendation for a series by its title.
     */
    fun findNextEpisodeByTitle(
        dao: DiscoveryEngineDao,
        profileId: String,
        seriesTitle: String,
        threshold: Double = 0.85
    ): NextEpisodeResult {
        val showName = ShowExtractor.extractShowName(seriesTitle)
        if (showName.isEmpty()) {
            return NextEpisodeResult(
                recommendationType = RecommendationType.CAUGHT_UP,
                targetEpisodeId = null,
                seasonNumber = null,
                episodeNumber = null,
                progress = null,
                reason = "Invalid or empty series title.",
                confidenceScore = 0.0
            )
        }

        val episodes = dao.getEpisodesByTitlePrefix(showName)
        return evaluateEpisodes(dao, profileId, episodes, threshold, 0.7, "Fuzzy title matching")
    }

    private fun evaluateEpisodes(
        dao: DiscoveryEngineDao,
        profileId: String,
        episodes: List<MediaItemEntity>,
        threshold: Double,
        confidence: Double,
        reasonPrefix: String
    ): NextEpisodeResult {
        if (episodes.isEmpty()) {
            return NextEpisodeResult(
                recommendationType = RecommendationType.CAUGHT_UP,
                targetEpisodeId = null,
                seasonNumber = null,
                episodeNumber = null,
                progress = null,
                reason = "$reasonPrefix: No episodes available in database.",
                confidenceScore = confidence
            )
        }

        // 2. Parse season/episode details and sort chronologically
        val wrappers = episodes.mapNotNull { entity ->
            val parsed = if (entity.seasonNumber == null || entity.episodeNumber == null) EpisodeParser.parse(entity.title) else null
            val s = entity.seasonNumber ?: parsed?.season
            val e = entity.episodeNumber ?: parsed?.episode
            if (s != null && e != null) {
                EpisodeWrapper(entity, s, e)
            } else {
                null
            }
        }.sortedWith(compareBy({ it.season }, { it.episode }))

        if (wrappers.isEmpty()) {
            return NextEpisodeResult(
                recommendationType = RecommendationType.CAUGHT_UP,
                targetEpisodeId = null,
                seasonNumber = null,
                episodeNumber = null,
                progress = null,
                reason = "$reasonPrefix: Available episodes lack valid season or episode numbers.",
                confidenceScore = confidence
            )
        }

        // 3. Query profile-isolated watch events
        val watchEvents = dao.getLatestWatchEventsForProfile(profileId)
        val watchMap = watchEvents.associateBy { it.itemId }

        // Find watched episodes in the series
        val watchedWrappers = wrappers.mapNotNull { wrapper ->
            val event = watchMap[wrapper.entity.id]
            if (event != null) wrapper to event else null
        }

        // Get the most recently watched episode (highest timestamp)
        val latestWatched = watchedWrappers.maxByOrNull { it.second.timestamp }

        // 4. Default: Series not started yet -> Recommend S01E01 or first available
        if (latestWatched == null) {
            val first = wrappers.first()
            val streams = dao.getStreamsForMediaItem(first.entity.id)
            if (streams.isNotEmpty()) {
                return NextEpisodeResult(
                    recommendationType = RecommendationType.NEXT_EPISODE,
                    targetEpisodeId = first.entity.id,
                    seasonNumber = first.season,
                    episodeNumber = first.episode,
                    progress = 0.0,
                    reason = "$reasonPrefix: Series not started. Recommending first episode.",
                    confidenceScore = confidence,
                    isAvailable = true
                )
            } else {
                val fallback = wrappers.drop(1).firstOrNull { dao.getStreamsForMediaItem(it.entity.id).isNotEmpty() }
                if (fallback != null) {
                    return NextEpisodeResult(
                        recommendationType = RecommendationType.NEXT_EPISODE,
                        targetEpisodeId = fallback.entity.id,
                        seasonNumber = fallback.season,
                        episodeNumber = fallback.episode,
                        progress = 0.0,
                        reason = "$reasonPrefix: Series not started. S01E01 not available. Recommending first available episode Season ${fallback.season} Episode ${fallback.episode}.",
                        confidenceScore = confidence * 0.9,
                        isAvailable = true
                    )
                } else {
                    return NextEpisodeResult(
                        recommendationType = RecommendationType.NEXT_EPISODE,
                        targetEpisodeId = first.entity.id,
                        seasonNumber = first.season,
                        episodeNumber = first.episode,
                        progress = 0.0,
                        reason = "$reasonPrefix: Series not started. Recommending first episode (No streams found).",
                        confidenceScore = confidence,
                        isAvailable = false
                    )
                }
            }
        }

        val (currentWrapper, latestEvent) = latestWatched
        val progress = if (latestEvent.durationMs > 0) {
            latestEvent.progressMs.toDouble() / latestEvent.durationMs.toDouble()
        } else {
            0.0
        }

        // 5. Unfinished episode -> CONTINUE_EPISODE
        if (progress < threshold) {
            val streams = dao.getStreamsForMediaItem(currentWrapper.entity.id)
            return NextEpisodeResult(
                recommendationType = RecommendationType.CONTINUE_EPISODE,
                targetEpisodeId = currentWrapper.entity.id,
                seasonNumber = currentWrapper.season,
                episodeNumber = currentWrapper.episode,
                progress = progress.coerceIn(0.0, 1.0),
                reason = "$reasonPrefix: Unfinished episode Season ${currentWrapper.season} Episode ${currentWrapper.episode} at ${(progress * 100).toInt()}%.",
                confidenceScore = confidence,
                isAvailable = streams.isNotEmpty()
            )
        }

        // 6. Completed episode -> Find chronologically next episode
        val currentIndex = wrappers.indexOfFirst { it.entity.id == currentWrapper.entity.id }
        val nextWrapperCandidate = if (currentIndex != -1 && currentIndex < wrappers.size - 1) {
            wrappers[currentIndex + 1]
        } else {
            null
        }

        // No next episode available -> CAUGHT_UP
        if (nextWrapperCandidate == null) {
            return NextEpisodeResult(
                recommendationType = RecommendationType.CAUGHT_UP,
                targetEpisodeId = null,
                seasonNumber = currentWrapper.season,
                episodeNumber = currentWrapper.episode,
                progress = 1.0,
                reason = "$reasonPrefix: Completed final available episode Season ${currentWrapper.season} Episode ${currentWrapper.episode}.",
                confidenceScore = confidence,
                isAvailable = false
            )
        }

        // Check streams for nextWrapperCandidate
        val nextStreams = dao.getStreamsForMediaItem(nextWrapperCandidate.entity.id)
        if (nextStreams.isNotEmpty()) {
            val recType = if (nextWrapperCandidate.season == currentWrapper.season) {
                RecommendationType.NEXT_EPISODE
            } else {
                RecommendationType.NEXT_SEASON
            }
            val reasonStr = if (recType == RecommendationType.NEXT_EPISODE) {
                "$reasonPrefix: Recommending next episode Season ${nextWrapperCandidate.season} Episode ${nextWrapperCandidate.episode}."
            } else {
                "$reasonPrefix: Recommending next season premiere Season ${nextWrapperCandidate.season} Episode ${nextWrapperCandidate.episode}."
            }
            return NextEpisodeResult(
                recommendationType = recType,
                targetEpisodeId = nextWrapperCandidate.entity.id,
                seasonNumber = nextWrapperCandidate.season,
                episodeNumber = nextWrapperCandidate.episode,
                progress = 0.0,
                reason = reasonStr,
                confidenceScore = confidence,
                isAvailable = true
            )
        } else {
            val fallback = wrappers.drop(currentIndex + 2).firstOrNull { dao.getStreamsForMediaItem(it.entity.id).isNotEmpty() }
            if (fallback != null) {
                val recType = if (fallback.season == currentWrapper.season) {
                    RecommendationType.NEXT_EPISODE
                } else {
                    RecommendationType.NEXT_SEASON
                }
                val reasonStr = "$reasonPrefix: Next episode not available. Recommending fallback Season ${fallback.season} Episode ${fallback.episode}."
                return NextEpisodeResult(
                    recommendationType = recType,
                    targetEpisodeId = fallback.entity.id,
                    seasonNumber = fallback.season,
                    episodeNumber = fallback.episode,
                    progress = 0.0,
                    reason = reasonStr,
                    confidenceScore = confidence * 0.9,
                    isAvailable = true
                )
            } else {
                val recType = if (nextWrapperCandidate.season == currentWrapper.season) {
                    RecommendationType.NEXT_EPISODE
                } else {
                    RecommendationType.NEXT_SEASON
                }
                val reasonStr = if (recType == RecommendationType.NEXT_EPISODE) {
                    "$reasonPrefix: Recommending next episode Season ${nextWrapperCandidate.season} Episode ${nextWrapperCandidate.episode} (No streams found)."
                } else {
                    "$reasonPrefix: Recommending next season premiere Season ${nextWrapperCandidate.season} Episode ${nextWrapperCandidate.episode} (No streams found)."
                }
                return NextEpisodeResult(
                    recommendationType = recType,
                    targetEpisodeId = nextWrapperCandidate.entity.id,
                    seasonNumber = nextWrapperCandidate.season,
                    episodeNumber = nextWrapperCandidate.episode,
                    progress = 0.0,
                    reason = reasonStr,
                    confidenceScore = confidence,
                    isAvailable = false
                )
            }
        }
    }
}
