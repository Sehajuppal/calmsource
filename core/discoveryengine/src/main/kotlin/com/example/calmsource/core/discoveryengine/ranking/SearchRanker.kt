package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import com.example.calmsource.core.discoveryengine.models.SearchResult
import com.example.calmsource.core.discoveryengine.normalization.MetadataNormalizer
import com.example.calmsource.core.discoveryengine.normalization.Vectorizer
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
import com.example.calmsource.core.sourceintelligence.ranking.MediaAvailabilityScorer
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

object SearchRanker {

    /**
     * Ranks raw database search results by calculating text match similarity,
     * quality boosts, profile history affinity, and live TV signals.
     */
    suspend fun rankSearchResults(
        dao: DiscoveryEngineDao,
        profileId: String,
        query: String,
        rawResults: List<Map<String, String>>,
        limit: Int = 20,
        isTelevision: Boolean = false,
    ): List<SearchResult> = withContext(Dispatchers.Default) {
        val normQuery = MetadataNormalizer.normalizeSearchQuery(query)
        val compactQuery = normQuery.replace(" ", "")
        if (rawResults.isEmpty()) return@withContext emptyList()

        val ids = rawResults.mapNotNull { it["id"] }
        
        // Batch query media items, channels, and embeddings to enrich scores
        // Chunk to respect SQLITE_MAX_VARIABLE_NUMBER (default 999)
        val mediaItemsMap = ids.chunkedAssociate(MAX_VARS) { chunk ->
            dao.getMediaItemsByIds(chunk).associateBy { it.id }
        }
        val channelsMap = ids.chunkedAssociate(MAX_VARS) { chunk ->
            dao.getChannelsByIds(chunk).associateBy { it.id }
        }
        val embeddingsMap = ids.chunkedAssociate(MAX_VARS) { chunk ->
            dao.getEmbeddingsByIds(chunk).associateBy { it.itemId }
        }

        // Vectorize search query
        val queryVector = Vectorizer.vectorizeQuery(query)

        // Query profile states to personalize boosts and penalties
        val userItemStatesMap = dao.getUserItemStatesForProfile(profileId).associateBy { it.itemId }
        val userChannelStatesMap = dao.getUserChannelStatesForProfile(profileId).associateBy { it.channelId }
        val userFeedbacksMap = dao.getUserFeedbacksForProfile(profileId).associateBy { it.itemId }
        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        // Batch-fetch streams for all non-channel items to avoid N+1 queries
        val nonChannelIds = rawResults.filter { (it["type"] ?: "movie") != "channel" }.mapNotNull { it["id"] }
        val allStreams = if (nonChannelIds.isNotEmpty()) {
            nonChannelIds.chunkedFlatMap(MAX_VARS) { dao.getStreamsForMediaItems(it) }
        } else emptyList()
        val streamsMap = allStreams.groupBy { it.mediaId }
        val allStreamIds = allStreams.map { it.id }
        val successCounts = if (allStreamIds.isNotEmpty()) {
            allStreamIds.chunkedFlatMap(MAX_VARS) { dao.getPlaybackSuccessCounts(it) }.associate { it.streamId to it.count }
        } else emptyMap()
        val failureCounts = if (allStreamIds.isNotEmpty()) {
            allStreamIds.chunkedFlatMap(MAX_VARS) { dao.getPlaybackFailureCounts(it) }.associate { it.streamId to it.count }
        } else emptyMap()

        val prefs = UserPreferencesRepository.preferences.value
        val deviceProfile = DeviceStreamProfile.forPlayback(isTelevision, prefs)
        val healthById = StreamScoringSupport.prefetchSourceHealth(allStreamIds)

        val rankedResults = rawResults.mapNotNull { raw ->
            val id = raw["id"] ?: return@mapNotNull null
            val type = raw["type"] ?: "movie"
            val title = raw["title"] ?: ""
            val normTitle = raw["normalized_title"] ?: ""
            val compactTitle = normTitle.replace(" ", "")
            val overview = raw["overview"]
            val genres = raw["genres"]
            val castDirector = raw["cast_director"]

            val reasons = mutableListOf<String>()

            // 1. Text Similarity Score (FTS)
            var ftsScore = 10.0 // Base match score
            var exactPrefixBoost = 0.0

            if (normQuery.isNotEmpty()) {
                if (normTitle == normQuery || compactTitle == compactQuery) {
                    exactPrefixBoost += 100.0
                    reasons.add("Exact title match")
                } else if (normTitle.startsWith(normQuery)) {
                    exactPrefixBoost += 50.0
                    reasons.add("Title prefix match")
                } else {
                    val words = normTitle.split(" ")
                    val wordMatch = words.any { it.startsWith(normQuery) }
                    if (wordMatch) {
                        exactPrefixBoost += 20.0
                        reasons.add("Word prefix match")
                    } else if (normTitle.contains(normQuery)) {
                        ftsScore += 10.0
                        reasons.add("Title contains query")
                    }
                }
            }

            // 2. Vector Similarity Score (Weight = 0.35)
            var vectorSimilarity = 0.0
            if (normQuery.isNotEmpty()) {
                val emb = embeddingsMap[id]
                if (emb != null) {
                    val itemVec = Vectorizer.bytesToVector(emb.embedding)
                    vectorSimilarity = Vectorizer.cosineSimilarity(queryVector, itemVec).coerceAtLeast(0.0)
                }
            }
            val vectorScore = vectorSimilarity * 100.0 * 0.35
            if (vectorSimilarity > 0.1) {
                reasons.add("Feature similarity match: " + String.format("%.2f", vectorSimilarity))
            }

            // 3. Alias Match Boost
            var aliasBoost = 0.0
            if (normQuery.isNotEmpty()) {
                val aliases = if (type == "channel") {
                    MetadataNormalizer.generateChannelAliases(title)
                } else {
                    MetadataNormalizer.generateTitleAliases(title)
                }
                if (aliases.contains(normQuery)) {
                    aliasBoost += 30.0
                    reasons.add("Exact alias match")
                } else if (aliases.any { it.startsWith(normQuery) }) {
                    aliasBoost += 15.0
                    reasons.add("Alias prefix match")
                }
            }

            // 4. Quality Metrics Boost
            var qualityBoost = 0.0
            var posterUrl: String? = null
            var subtitle: String? = null
            var source = "local"
            var externalIds = emptyMap<String, String>()

            if (type == "channel") {
                val chan = channelsMap[id]
                if (chan != null) {
                    posterUrl = chan.logoUrl
                    subtitle = chan.category
                    source = chan.providerId
                    val nameLower = chan.name.lowercase()
                    if (nameLower.contains("fhd") || nameLower.contains("1080p") || nameLower.contains("4k")) {
                        qualityBoost += 5.0
                        reasons.add("High resolution stream")
                    }
                }
            } else {
                val media = mediaItemsMap[id]
                if (media != null) {
                    posterUrl = media.posterUrl
                    subtitle = "${media.type.replaceFirstChar { it.uppercase() }} • ${media.releaseYear ?: ""}"
                    source = media.source ?: "stremio"
                    externalIds = runCatching {
                        Json.decodeFromString<Map<String, String>>(media.externalIdsJson)
                    }.getOrDefault(emptyMap())
                    val rating = media.rating ?: 0.0
                    if (rating > 0.0) {
                        qualityBoost += rating * 2.0
                        reasons.add("High rating: $rating")
                    }
                    val year = media.releaseYear ?: 0
                    if (year >= 2022) {
                        qualityBoost += 5.0
                        reasons.add("Recent release")
                    }
                }
            }

            // 5. Profile watch history / favorite / hidden state
            var profileBoost = 0.0
            var penalty = 0.0

            if (type == "channel") {
                val state = userChannelStatesMap[id]
                if (state != null) {
                    if (state.isHidden) {
                        penalty += 1000.0
                    }
                    if (state.isFavorite) {
                        profileBoost += 30.0
                        reasons.add("Favorite channel")
                    }
                    if (state.watchCount > 0) {
                        profileBoost += 10.0 + (state.watchCount * 2.0).coerceAtMost(15.0)
                        reasons.add("Frequently watched channel")
                    }
                }
            } else {
                val state = userItemStatesMap[id]
                if (state != null) {
                    if (state.isHidden) {
                        penalty += 1000.0
                    }
                    if (state.isFavorite) {
                        profileBoost += 30.0
                        reasons.add("Favorite content")
                    }
                    if (state.progressMs > 0 && !state.isCompleted) {
                        profileBoost += 25.0
                        reasons.add("In progress (continue watching)")
                    }
                    if (state.watchCount > 0) {
                        profileBoost += 10.0 + (state.watchCount * 2.0).coerceAtMost(15.0)
                        reasons.add("Previously watched")
                    }
                }
            }

            // 5b. User feedbacks (not interested or hidden)
            val feedback = userFeedbacksMap[id]
            if (feedback != null) {
                if (feedback.feedbackType == "not_interested" || feedback.feedbackType == "hidden") {
                    penalty += 1000.0
                }
            }

            // 6. Live Television Boost
            var liveNowBoost = 0.0
            if (type == "channel") {
                liveNowBoost += 25.0
                reasons.add("Live TV channel")
            }

            // Filter out hidden/not interested items completely
            if (penalty >= 500.0) {
                return@mapNotNull null
            }

            // 7. Stream Availability Scoring (unified top-stream signal)
            val availabilityScore = if (type == "channel") {
                MediaAvailabilityScorer.channelAvailability().additiveScore
            } else {
                val streams = streamsMap[id] ?: emptyList()
                MediaAvailabilityScorer.scoreFromStreams(
                    streams = streams.map { it.toStreamSource() },
                    prefs = prefs,
                    preferredAudio = preferredAudio,
                    preferredSub = preferredSub,
                    streamSuccessCount = { streamId -> successCounts[streamId] ?: 0 },
                    streamFailureCount = { streamId -> failureCounts[streamId] ?: 0 },
                    sourceHealthById = healthById,
                    deviceProfile = deviceProfile,
                ).additiveScore
            }
            if (availabilityScore > 0.0) {
                reasons.add("Playable streams available: +${availabilityScore.toInt()}")
            } else if (availabilityScore < 0.0) {
                reasons.add("Playback issues detected: ${availabilityScore.toInt()}")
            }

            val totalScore = ftsScore + vectorScore + exactPrefixBoost + aliasBoost + qualityBoost + profileBoost + liveNowBoost + availabilityScore - penalty

            val breakdown = ScoreBreakdown(
                ftsScore = ftsScore,
                exactPrefixBoost = exactPrefixBoost,
                aliasBoost = aliasBoost,
                qualityBoost = qualityBoost,
                profileBoost = profileBoost,
                liveNowBoost = liveNowBoost,
                vectorSimilarity = vectorSimilarity,
                penalty = penalty,
                availabilityScore = availabilityScore,
                reasons = reasons
            )

            SearchResult(
                id = id,
                type = type,
                title = title,
                subtitle = subtitle,
                posterUrl = posterUrl,
                score = totalScore,
                source = source,
                scoreBreakdown = breakdown,
                externalIds = externalIds
            )
        }.sortedByDescending { it.score }

        rankedResults.take(limit)
    }

    private const val MAX_VARS = 900

    private inline fun <T, R> List<T>.chunkedFlatMap(chunkSize: Int, transform: (List<T>) -> List<R>): List<R> {
        if (size <= chunkSize) return transform(this)
        return chunked(chunkSize).flatMap(transform)
    }

    private inline fun <T, K, V> List<T>.chunkedAssociate(chunkSize: Int, transform: (List<T>) -> Map<K, V>): Map<K, V> {
        if (size <= chunkSize) return transform(this)
        val result = mutableMapOf<K, V>()
        chunked(chunkSize).forEach { result.putAll(transform(it)) }
        return result
    }
}
