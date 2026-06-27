package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.MediaStreamEntity
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamParserUtil
import com.example.calmsource.core.sourceintelligence.SourceIntelligence
import com.example.calmsource.core.sourceintelligence.models.RawSourceInput
import com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat
import com.example.calmsource.core.sourceintelligence.parsers.LanguageAndAudioParser
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object StreamRanker {

    /**
     * Ranks media streams based on resolution, codec, file size, language matches,
     * and previous playback history success/failure stats.
     */
    suspend fun rank(
        dao: DiscoveryEngineDao,
        profileId: String,
        streams: List<MediaStreamEntity>,
        strategy: SortingPreference = SortingPreference.BEST_MATCH,
        availabilityScoreProvider: (String) -> Double = { 0.0 }
    ): List<MediaStreamEntity> = withContext(Dispatchers.Default) {
        if (streams.isEmpty()) return@withContext emptyList()

        // Fetch profile preferred languages
        val profile = dao.getProfile(profileId)
        val preferredAudio = profile?.preferredAudioLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val preferredSub = profile?.preferredSubtitleLanguages?.lowercase()?.split(",")?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()

        // Batch-fetch playback counts to avoid N+1 per-stream DB queries
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

        rankWithSignals(
            streams = streams,
            preferredAudio = preferredAudio,
            preferredSub = preferredSub,
            streamSuccessCount = { streamId -> successCounts[streamId] ?: 0 },
            streamFailureCount = { streamId -> failureCounts[streamId] ?: 0 },
            sourceSuccessCount = { source -> sourceSuccessCounts[source] ?: 0 },
            sourceFailureCount = { source -> sourceFailureCounts[source] ?: 0 },
            strategy = strategy,
            availabilityScoreProvider = availabilityScoreProvider
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
        availabilityScoreProvider: (String) -> Double = { 0.0 }
    ): List<MediaStreamEntity> {
        if (streams.isEmpty()) return emptyList()

        return streams.map { stream ->
            var score = 0.0
            val intelligence = SourceIntelligence.process(
                input = RawSourceInput(
                    rawFilename = stream.title,
                    rawTitle = listOfNotNull(
                        stream.resolution,
                        stream.codec,
                        stream.quality,
                        stream.sizeInBytes?.takeIf { it > 0L }?.let { "${it / (1024 * 1024)}MB" }
                    ).joinToString(" ").ifBlank { null },
                    rawUrl = null
                ),
                preferredLanguages = preferredAudio
            )
            val features = intelligence.rankingFeatures

            val parsedHeight = features.resolutionHeight.takeIf { it > 0 } ?: stream.resolution.resolutionHeightHint()
            val titleLower = stream.title.lowercase()
            val sizeBytes = (stream.sizeInBytes ?: 0L).takeIf { it > 0L } ?: features.sizeBytes
            val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
            val seeds = StreamParserUtil.parseSeeds(stream.title) ?: 0

            if (strategy == SortingPreference.HIGHEST_QUALITY) {
                // HIGHEST_QUALITY scoring:
                // - Prioritize 4K (+120) and 1080p (+60)
                score += when {
                    parsedHeight >= 2160 -> 120.0
                    parsedHeight >= 1080 -> 60.0
                    else -> 0.0
                }

                // - Dolby Vision gets +50, HDR10+ gets +30, HDR10 gets +10
                val hdrFormat = StreamParserUtil.parseHdrFormat(stream.title)
                score += when (hdrFormat) {
                    "DV" -> 50.0
                    "HDR10+" -> 30.0
                    "HDR10" -> 10.0
                    else -> 0.0
                }

                // - Atmos/TrueHD gets +30, DTS-HD/DTS:X gets +20
                val audioFormat = StreamParserUtil.parseAudioCodec(stream.title)
                score += when (audioFormat) {
                    "Atmos" -> 30.0
                    "DTS-HD" -> 20.0
                    else -> 0.0
                }

                // - AV1 gets +30, HEVC gets +20
                val videoCodecStr = StreamParserUtil.parseVideoCodec(stream.title)
                score += when (videoCodecStr) {
                    "AV1" -> 30.0
                    "HEVC" -> 20.0
                    else -> 0.0
                }

                // - reward heavy file sizes (>=40GB gets +100, >=20GB gets +50, >=10GB gets +20)
                score += when {
                    sizeGb >= 40.0 -> 100.0
                    sizeGb >= 20.0 -> 50.0
                    sizeGb >= 10.0 -> 20.0
                    else -> 0.0
                }
            } else {
                // BEST_MATCH scoring:
                // - Exclude files >20GB (heavy penalty like -150)
                if (sizeGb > 20.0) {
                    score -= 150.0
                }

                // - sweet spot size (2-8GB gets +30, 8-15GB gets +15)
                score += when {
                    sizeGb >= 2.0 && sizeGb <= 8.0 -> 30.0
                    sizeGb > 8.0 && sizeGb <= 15.0 -> 15.0
                    else -> 0.0
                }

                // - reward high seeders (>50 gets +20, >10 gets +10)
                score += when {
                    seeds > 50 -> 20.0
                    seeds > 10 -> 10.0
                    else -> 0.0
                }

                // - reward 1080p (+80) and 4K (+60 if under 15GB)
                score += when {
                    parsedHeight == 1080 -> 80.0
                    parsedHeight >= 2160 && sizeGb < 15.0 -> 60.0
                    else -> 0.0
                }

                // - HEVC/AV1/Atmos gets +10
                val videoCodecStr = StreamParserUtil.parseVideoCodec(stream.title)
                val audioFormat = StreamParserUtil.parseAudioCodec(stream.title)
                if (videoCodecStr == "HEVC") score += 10.0
                if (videoCodecStr == "AV1") score += 10.0
                if (audioFormat == "Atmos") score += 10.0

                // - remuxes get -40
                val isRemux = titleLower.contains("remux") || features.isRemux
                if (isRemux) {
                    score -= 40.0
                }
            }

            // 5. Audio Language Preference Match
            val streamLang = stream.language?.lowercase() ?: ""
            if (preferredAudio.isNotEmpty()) {
                if (preferredAudio.contains(streamLang)) {
                    score += 30.0
                } else {
                    // Fuzzy match title
                    for (lang in preferredAudio) {
                        val fullName = LanguageAndAudioParser.getLanguageFullName(lang)
                        if (fullName != null) {
                            val match = if (lang.length <= 2) {
                                titleLower.contains(fullName) || LanguageAndAudioParser.parseLanguage(stream.title).languages.any { it.equals(fullName, ignoreCase = true) }
                            } else {
                                titleLower.contains(lang) || titleLower.contains(fullName)
                            }
                            if (match) {
                                score += 20.0
                                break
                            }
                        }
                    }
                }
            }

            // 6. Subtitles Preference Match
            if (stream.isSubbed) {
                for (sub in preferredSub) {
                    val fullName = LanguageAndAudioParser.getLanguageFullName(sub)
                    if (fullName != null) {
                        val match = if (sub.length <= 2) {
                            titleLower.contains(fullName) || LanguageAndAudioParser.parseLanguage(stream.title).languages.any { it.equals(fullName, ignoreCase = true) }
                        } else {
                            titleLower.contains(sub) || titleLower.contains(fullName)
                        }
                        if (match) {
                            score += 15.0
                            break
                        }
                    }
                }
            }

            // 7. Previous Playback Success / Failures (from stream_playback_history)
            val successCount = streamSuccessCount(stream.id)
            val failureCount = streamFailureCount(stream.id)
            
            // Success reward (+20 per success, max +40)
            score += minOf(successCount * 20.0, 40.0)

            // Failure penalty (-50 per failure, max -150)
            score -= minOf(failureCount * 50.0, 150.0)

            // 8. Source-level Playback History Boost/Penalty (if specific stream has no history)
            val src = stream.source
            if (src != null && successCount == 0 && failureCount == 0) {
                val srcSuccess = sourceSuccessCount(src)
                val srcFailure = sourceFailureCount(src)
                if (srcSuccess > srcFailure) {
                    score += 10.0
                } else if (srcFailure > srcSuccess) {
                    score -= 20.0
                }
            }

            score += availabilityScoreProvider(stream.mediaId).let { if (it.isNaN()) 0.0 else it }.coerceIn(0.0, 1.0) * 5.0

            StreamWithScore(stream, score)
        }
        .sortedByDescending { it.score }
        .map { it.stream }
    }

    private data class StreamWithScore(
        val stream: MediaStreamEntity,
        val score: Double
    )

    private fun String?.resolutionHeightHint(): Int {
        val value = this?.lowercase() ?: return 0
        return when {
            value.contains("4320p") || value.contains("8k") -> 4320
            value.contains("2160p") || value.contains("4k") || value.contains("uhd") -> 2160
            value.contains("1440p") || value.contains("qhd") -> 1440
            value.contains("1080p") || value.contains("fhd") -> 1080
            value.contains("720p") || value == "hd" || value.contains(" hd") -> 720
            value.contains("480p") || value.contains("sd") -> 480
            else -> 0
        }
    }
}
