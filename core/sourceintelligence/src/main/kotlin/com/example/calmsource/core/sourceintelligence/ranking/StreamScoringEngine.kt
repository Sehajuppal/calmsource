package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.ProviderHealth
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.SourceReliabilityTier
import com.example.calmsource.core.model.StreamParserUtil
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import com.example.calmsource.core.sourceintelligence.SourceIntelligence
import com.example.calmsource.core.sourceintelligence.models.ParsedSourceMetadata
import com.example.calmsource.core.sourceintelligence.models.SourceAudioFormat
import com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat
import com.example.calmsource.core.sourceintelligence.models.SourceIntelligenceResult
import com.example.calmsource.core.sourceintelligence.models.SourceRankingFeatures
import com.example.calmsource.core.sourceintelligence.models.SourceVideoCodec
import com.example.calmsource.core.sourceintelligence.models.toRawSourceInput
import com.example.calmsource.core.sourceintelligence.parsers.LanguageAndAudioParser
import kotlin.math.pow

/**
 * Optional per-stream signals layered on top of parsed metadata.
 */
data class StreamScoringSignals(
    val streamSuccessCount: Int = 0,
    val streamFailureCount: Int = 0,
    val sourceSuccessCount: Int = 0,
    val sourceFailureCount: Int = 0,
    val isDebridCached: Boolean = false,
    val sourceReliabilityTier: SourceReliabilityTier? = null,
    val lastSuccessWithin24h: Boolean = false,
    val providerHealth: ProviderHealth? = null,
    val providerPriority: Int? = null,
    val providerHealthScore: Int? = null,
    /** Epoch ms of the most recent playback failure for this stream/source. */
    val lastFailureAtMs: Long = 0L,
)

data class StreamScoringInput(
    val source: StreamSource,
    val strategy: SortingPreference = SortingPreference.BEST_MATCH,
    val prefs: UserPreferences = UserPreferences(),
    val preferredAudio: List<String> = emptyList(),
    val preferredSub: List<String> = emptyList(),
    val intelligence: SourceIntelligenceResult? = null,
    val signals: StreamScoringSignals = StreamScoringSignals(),
    val deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
)

/**
 * Unified stream ranking used by details, discovery, search, and playback racing.
 */
object StreamScoringEngine {

    private fun Int.points(): Double = toDouble()

    fun score(input: StreamScoringInput): Double = scoreDetailed(input).score

    fun scoreDetailed(input: StreamScoringInput): StreamScoredResult {
        val source = input.source
        val preferredLangs = buildPreferredLanguages(input.prefs, input.preferredAudio)
        val intelligence = input.intelligence ?: SourceIntelligence.process(
            input = source.toRawSourceInput(),
            preferredLanguages = preferredLangs,
            lowDataModeEnabled = input.prefs.preferLowerDataUsage
        )
        val features = intelligence.rankingFeatures
        val parsed = intelligence.parsedMetadata

        val acc = ScoreAccumulator()
        val titleLower = buildCombinedTitle(source).lowercase()
        val streamLang = source.language.lowercase()

        acc.add(
            languageScore(source, streamLang, titleLower, input.prefs, input.preferredAudio, input.preferredSub),
            "Language match"
        )

        if (source.isDualAudio && input.prefs.preferDualAudio) {
            acc.add(StreamScoringConstants.DUAL_AUDIO_BONUS.points(), "Dual audio preferred")
        }
        if (source.isDubbed && input.prefs.preferDubbedAudio) {
            acc.add(StreamScoringConstants.DUBBED_AUDIO_BONUS.points(), "Dubbed audio preferred")
        }

        if (source.isIptvSource() && input.prefs.preferIptvExactMatch) {
            acc.add(StreamScoringConstants.IPTV_EXACT_MATCH_BONUS.points(), "IPTV exact match")
        }
        if (input.signals.isDebridCached) {
            acc.add(StreamScoringConstants.DEBRID_CACHED_BONUS.points(), "Debrid cached")
            if (input.prefs.preferCachedDebrid) {
                acc.add(StreamScoringConstants.DEBRID_PREFERRED_CACHED_BONUS.points(), "Cached debrid preferred")
            }
        }

        val parsedHeight = features.resolutionHeight.takeIf { it > 0 }
            ?: source.resolution.resolutionHeightHint()
            ?: 0
        val sizeBytes = (source.sizeBytes ?: 0L).takeIf { it > 0L } ?: features.sizeBytes
        val sizeGb = sizeBytes / (1024.0 * 1024.0 * 1024.0)
        val seeds = source.seeds ?: StreamParserUtil.parseSeeds(titleLower) ?: features.seeds
        val isRemux = features.isRemux || titleLower.contains("remux")

        acc.add(
            strategyQualityScore(
                strategy = input.strategy,
                parsedHeight = parsedHeight,
                sizeGb = sizeGb,
                seeds = seeds,
                features = features,
                parsed = parsed,
                titleLower = titleLower,
                prefs = input.prefs,
                isRemux = isRemux,
            ),
            if (input.strategy == SortingPreference.HIGHEST_QUALITY) "Highest quality tier" else "Best match quality"
        )

        if (features.isHugeSize && !input.prefs.preferHighestQuality && input.strategy == SortingPreference.BEST_MATCH) {
            acc.add(StreamScoringConstants.HUGE_SIZE_SOFT_PENALTY.points(), "Very large file")
        }
        val practicalDelta = if (input.strategy == SortingPreference.BEST_MATCH && sizeGb in 2.0..15.0) {
            0.0
        } else {
            (features.practicalScore - 50).toDouble()
        }
        acc.add(practicalDelta, "Practical size/codec fit")

        if (seeds > 0) {
            val seedBonus = (seeds / StreamScoringConstants.SEED_DIVISOR)
                .coerceAtMost(StreamScoringConstants.MAX_SEEDS_BONUS)
                .toDouble()
            acc.add(seedBonus, "Seeder count")
        }

        if (input.prefs.preferLowerDataUsage) {
            if (parsedHeight >= 2160 && sizeGb > 5.0) {
                acc.add(StreamScoringConstants.LOW_BANDWIDTH_4K_PENALTY.points(), "Data saver: 4K penalty")
            }
            if (features.isLowDataSuitable) {
                acc.add(StreamScoringConstants.LOW_BANDWIDTH_COMPACT_BONUS.points(), "Data saver: compact stream")
            }
        }

        acc.add(deviceProfileScore(parsedHeight, features, parsed, input.deviceProfile), "Device fit")

        when (input.signals.providerHealth) {
            ProviderHealth.HEALTHY -> acc.add(StreamScoringConstants.PROVIDER_HEALTHY_BONUS.points(), "Healthy provider")
            ProviderHealth.SLOW -> acc.add(StreamScoringConstants.PROVIDER_SLOW_PENALTY.points(), "Slow provider")
            ProviderHealth.FAILED -> acc.add(StreamScoringConstants.PROVIDER_FAILED_PENALTY.points(), "Failed provider")
            null -> Unit
        }
        input.signals.providerPriority?.let { priority ->
            acc.add((100 - priority).coerceAtLeast(0).toDouble(), "Provider priority")
        }
        input.signals.providerHealthScore?.takeIf { it < 40 }?.let {
            acc.add(StreamScoringConstants.PROVIDER_UNHEALTHY_PENALTY.points(), "Unhealthy provider score")
        }

        when (input.signals.sourceReliabilityTier) {
            SourceReliabilityTier.BLOCKED -> acc.add(StreamScoringConstants.SOURCE_BLOCKED_PENALTY.points(), "Source blocked")
            SourceReliabilityTier.POOR -> acc.add(StreamScoringConstants.SOURCE_POOR_PENALTY.points(), "Poor source health")
            SourceReliabilityTier.UNSTABLE -> acc.add(StreamScoringConstants.SOURCE_UNSTABLE_PENALTY.points(), "Unstable source")
            SourceReliabilityTier.EXCELLENT -> acc.add(StreamScoringConstants.SOURCE_EXCELLENT_BOOST.points(), "Excellent source")
            else -> Unit
        }
        if (input.signals.lastSuccessWithin24h) {
            acc.add(StreamScoringConstants.SOURCE_RECENT_SUCCESS_BOOST.points(), "Worked recently")
        }

        acc.add(
            minOf(
                input.signals.streamSuccessCount * StreamScoringConstants.STREAM_SUCCESS_BONUS.points(),
                StreamScoringConstants.STREAM_SUCCESS_MAX.points()
            ),
            "Past playback successes"
        )

        val failureDecay = playbackDecayWeight(input.signals.lastFailureAtMs)
        if (input.signals.streamFailureCount > 0) {
            val failurePenalty = minOf(
                input.signals.streamFailureCount * StreamScoringConstants.STREAM_FAILURE_PENALTY.points() * failureDecay,
                StreamScoringConstants.STREAM_FAILURE_MAX.points()
            )
            val reason = if (failureDecay < 0.99) "Past failures (fading)" else "Past playback failures"
            acc.add(-failurePenalty, reason)
        }

        if (input.signals.streamSuccessCount == 0 && input.signals.streamFailureCount == 0) {
            val srcSuccess = input.signals.sourceSuccessCount
            val srcFailure = input.signals.sourceFailureCount
            if (srcSuccess > srcFailure) {
                acc.add(StreamScoringConstants.SOURCE_HISTORY_POSITIVE.points(), "Source history positive")
            } else if (srcFailure > srcSuccess) {
                val sourcePenalty = StreamScoringConstants.SOURCE_HISTORY_NEGATIVE.points() * failureDecay
                val reason = if (failureDecay < 0.99) "Source history negative (fading)" else "Source history negative"
                acc.add(sourcePenalty, reason)
            }
        }

        return StreamScoredResult(
            score = acc.total,
            breakdown = StreamScoreBreakdown(totalScore = acc.total, reasons = acc.reasons),
        )
    }

    fun scoreBatch(
        sources: List<StreamSource>,
        strategy: SortingPreference,
        prefs: UserPreferences,
        preferredAudio: List<String> = emptyList(),
        preferredSub: List<String> = emptyList(),
        signalsFor: (StreamSource) -> StreamScoringSignals = { StreamScoringSignals() },
        deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
    ): List<Pair<StreamSource, Double>> {
        if (sources.isEmpty()) return emptyList()
        val preferredLangs = buildPreferredLanguages(prefs, preferredAudio)
        val inputs = sources.map { it.toRawSourceInput() }
        val intelligenceResults = SourceIntelligence.processAll(
            inputs = inputs,
            preferredLanguages = preferredLangs,
            lowDataModeEnabled = prefs.preferLowerDataUsage
        )
        return sources.zip(intelligenceResults) { source, intelligence ->
            val streamScore = score(
                StreamScoringInput(
                    source = source,
                    strategy = strategy,
                    prefs = prefs,
                    preferredAudio = preferredAudio,
                    preferredSub = preferredSub,
                    intelligence = intelligence,
                    signals = signalsFor(source),
                    deviceProfile = deviceProfile,
                )
            )
            source to streamScore
        }.sortedWith(
            compareByDescending<Pair<StreamSource, Double>> { it.second }
                .thenByDescending { it.first.seeds ?: 0 }
                .thenBy { it.first.sizeBytes ?: Long.MAX_VALUE }
                .thenBy { it.first.id }
        )
    }

    /** Exponential decay: recent failures weigh more; older failures fade over ~14 days. */
    internal fun playbackDecayWeight(lastFailureAtMs: Long, nowMs: Long = System.currentTimeMillis()): Double {
        if (lastFailureAtMs <= 0L) return 1.0
        val ageDays = (nowMs - lastFailureAtMs).coerceAtLeast(0L) / (24.0 * 60 * 60 * 1000)
        return 0.5.pow(ageDays / StreamScoringConstants.PLAYBACK_DECAY_HALF_LIFE_DAYS)
    }

    private class ScoreAccumulator {
        var total = 0.0
            private set
        val reasons = mutableListOf<String>()

        fun add(points: Double, reason: String) {
            if (points == 0.0) return
            total += points
            val sign = if (points >= 0) "+" else ""
            reasons.add("$reason ($sign${points.toInt()})")
        }
    }

    private fun buildPreferredLanguages(prefs: UserPreferences, profileAudio: List<String>): List<String> {
        return (listOf(prefs.primaryLanguage, prefs.secondaryLanguage) + profileAudio)
            .map { it.trim().lowercase() }
            .filter { it.isNotEmpty() }
            .distinct()
    }

    private fun languageScore(
        source: StreamSource,
        streamLang: String,
        titleLower: String,
        prefs: UserPreferences,
        preferredAudio: List<String>,
        preferredSub: List<String>,
    ): Double {
        var score = 0.0
        val primary = prefs.primaryLanguage.trim()
        val secondary = prefs.secondaryLanguage.trim()

        when {
            primary.isNotEmpty() && streamLang.equals(primary, ignoreCase = true) ->
                score += StreamScoringConstants.PRIMARY_LANGUAGE_BONUS.points()
            secondary.isNotEmpty() && streamLang.equals(secondary, ignoreCase = true) ->
                score += StreamScoringConstants.SECONDARY_LANGUAGE_BONUS.points()
            streamLang.isNotEmpty() && primary.isNotEmpty() && secondary.isNotEmpty() ->
                score += StreamScoringConstants.FOREIGN_LANGUAGE_PENALTY.points()
        }

        if (preferredAudio.isNotEmpty()) {
            if (preferredAudio.any { streamLang.equals(it, ignoreCase = true) }) {
                score += StreamScoringConstants.PROFILE_LANGUAGE_BONUS.points()
            } else {
                for (lang in preferredAudio) {
                    val fullName = LanguageAndAudioParser.getLanguageFullName(lang) ?: continue
                    val match = if (lang.length <= 2) {
                        titleLower.contains(fullName) ||
                            LanguageAndAudioParser.parseLanguage(titleLower).languages
                                .any { it.equals(fullName, ignoreCase = true) }
                    } else {
                        titleLower.contains(lang) || titleLower.contains(fullName)
                    }
                    if (match) {
                        score += StreamScoringConstants.PROFILE_LANGUAGE_FUZZY_BONUS.points()
                        break
                    }
                }
            }
        }

        if (source.isSubbed && preferredSub.isNotEmpty()) {
            for (sub in preferredSub) {
                val fullName = LanguageAndAudioParser.getLanguageFullName(sub) ?: continue
                val match = if (sub.length <= 2) {
                    titleLower.contains(fullName) ||
                        LanguageAndAudioParser.parseLanguage(titleLower).languages
                            .any { it.equals(fullName, ignoreCase = true) }
                } else {
                    titleLower.contains(sub) || titleLower.contains(fullName)
                }
                if (match) {
                    score += StreamScoringConstants.SUBTITLE_MATCH_BONUS.points()
                    break
                }
            }
        } else if (source.isSubbed && prefs.subtitleLanguage.isNotBlank() &&
            streamLang.equals(prefs.subtitleLanguage, ignoreCase = true)
        ) {
            score += StreamScoringConstants.SUBTITLE_MATCH_BONUS.points()
        }

        return score
    }

    private fun strategyQualityScore(
        strategy: SortingPreference,
        parsedHeight: Int,
        sizeGb: Double,
        seeds: Int,
        features: SourceRankingFeatures,
        parsed: ParsedSourceMetadata,
        titleLower: String,
        prefs: UserPreferences,
        isRemux: Boolean,
    ): Double {
        var score = 0.0
        if (strategy == SortingPreference.HIGHEST_QUALITY) {
            score += when {
                parsedHeight >= 2160 -> StreamScoringConstants.RESOLUTION_4K_HQ.points()
                parsedHeight >= 1080 -> StreamScoringConstants.RESOLUTION_1080P_HQ.points()
                else -> 0.0
            }
            score += when (parsed.hdrFormat) {
                SourceHdrFormat.DOLBY_VISION -> StreamScoringConstants.HDR_DV_BONUS.points()
                SourceHdrFormat.HDR10_PLUS -> StreamScoringConstants.HDR10_PLUS_BONUS.points()
                SourceHdrFormat.HDR10 -> StreamScoringConstants.HDR10_BONUS.points()
                else -> hdrBonusFromTitle(titleLower)
            }
            if (features.isAtmos) score += StreamScoringConstants.ATMOS_HQ_BONUS.points()
            else if (parsed.audioFormat == SourceAudioFormat.DTS_HD ||
                parsed.audioFormat == SourceAudioFormat.DTS_X
            ) {
                score += StreamScoringConstants.DTS_HD_BONUS.points()
            }
            if (parsed.videoCodec == SourceVideoCodec.AV1) {
                score += StreamScoringConstants.AV1_HQ_BONUS.points()
            } else if (features.isHevc) {
                score += StreamScoringConstants.HEVC_HQ_BONUS.points()
            }
            score += when {
                sizeGb >= 40.0 -> StreamScoringConstants.SIZE_40GB_BONUS.points()
                sizeGb >= 20.0 -> StreamScoringConstants.SIZE_20GB_BONUS.points()
                sizeGb >= 10.0 -> StreamScoringConstants.SIZE_10GB_BONUS.points()
                else -> 0.0
            }
        } else {
            if (sizeGb > 20.0) score += StreamScoringConstants.HUGE_FILE_PENALTY.points()
            score += when {
                sizeGb in 2.0..8.0 -> StreamScoringConstants.SWEET_SPOT_SIZE_BONUS.points()
                sizeGb > 8.0 && sizeGb <= 15.0 -> StreamScoringConstants.MID_SIZE_BONUS.points()
                else -> 0.0
            }
            score += when {
                seeds > 50 -> StreamScoringConstants.SEEDS_HIGH_BONUS.points()
                seeds > 10 -> StreamScoringConstants.SEEDS_MID_BONUS.points()
                else -> 0.0
            }
            score += bestMatchResolutionScore(parsedHeight, sizeGb, prefs)
            if (features.isHevc || parsed.videoCodec == SourceVideoCodec.AV1) {
                score += StreamScoringConstants.CODEC_HEVC_AV1_BEST_MATCH_BONUS.points()
            }
            if (features.isAtmos) score += StreamScoringConstants.CODEC_ATMOS_BEST_MATCH_BONUS.points()
            if (isRemux) {
                score += StreamScoringConstants.REMUX_PENALTY.points()
            }
            if (prefs.preferHighestQuality && parsedHeight >= 1080) {
                score += StreamScoringConstants.HIGH_QUALITY_PREFERENCE_BONUS.points()
            }
        }
        return score
    }

    /** Reasonable-size 4K ranks above 1080p in Best Match; heavy/remux 4K does not. */
    internal fun bestMatchResolutionScore(parsedHeight: Int, sizeGb: Double, prefs: UserPreferences): Double {
        return when {
            parsedHeight >= 2160 -> when {
                sizeGb > 20.0 -> 0.0
                sizeGb in 2.0..15.0 -> StreamScoringConstants.RESOLUTION_4K_REASONABLE_BEST_MATCH.points()
                sizeGb > 15.0 && sizeGb <= 20.0 -> StreamScoringConstants.RESOLUTION_4K_MID_BEST_MATCH.points()
                sizeGb in 0.0..2.0 && sizeGb > 0 -> StreamScoringConstants.RESOLUTION_4K_LIGHT_BEST_MATCH.points()
                sizeGb <= 0 -> StreamScoringConstants.RESOLUTION_4K_UNKNOWN_SIZE_BEST_MATCH.points()
                else -> StreamScoringConstants.RESOLUTION_4K_REASONABLE_BEST_MATCH.points()
            }
            parsedHeight == 1080 -> StreamScoringConstants.RESOLUTION_1080P_BEST_MATCH.points()
            parsedHeight >= 720 -> StreamScoringConstants.RESOLUTION_720P.points()
            parsedHeight > 0 -> {
                if (prefs.hideLowQuality) StreamScoringConstants.LOW_QUALITY_PENALTY.points()
                else StreamScoringConstants.RESOLUTION_SD.points()
            }
            else -> StreamScoringConstants.RESOLUTION_UNKNOWN.points()
        }
    }

    internal fun deviceProfileScore(
        parsedHeight: Int,
        features: SourceRankingFeatures,
        parsed: ParsedSourceMetadata,
        profile: DeviceStreamProfile,
    ): Double {
        if (profile.maxRecommendedHeight >= 4320 && !profile.penalizeHdr) return 0.0
        var score = 0.0
        if (parsedHeight > profile.maxRecommendedHeight) {
            score += StreamScoringConstants.DEVICE_OVER_RESOLUTION_PENALTY.points()
        }
        if (profile.penalizeHdr && (
                features.isDolbyVision ||
                    parsed.hdrFormat != com.example.calmsource.core.sourceintelligence.models.SourceHdrFormat.SDR
                )
        ) {
            score += StreamScoringConstants.DEVICE_HDR_PENALTY.points()
        }
        if (profile.maxRecommendedHeight <= 1080 && features.isAtmos) {
            score += StreamScoringConstants.DEVICE_ATMOS_PENALTY.points()
        }
        return score
    }

    private fun hdrBonusFromTitle(titleLower: String): Double {
        return when (StreamParserUtil.parseHdrFormat(titleLower)) {
            "DV" -> StreamScoringConstants.HDR_DV_BONUS.points()
            "HDR10+" -> StreamScoringConstants.HDR10_PLUS_BONUS.points()
            "HDR10" -> StreamScoringConstants.HDR10_BONUS.points()
            else -> 0.0
        }
    }

    private fun buildCombinedTitle(source: StreamSource): String {
        return source.rawTitle ?: listOfNotNull(
            source.name,
            source.resolution.takeIf { it.isNotBlank() },
            source.videoCodec,
            source.audioCodec
        ).joinToString(" ")
    }

    private fun String.resolutionHeightHint(): Int? {
        val value = lowercase()
        return when {
            value.contains("4320p") || value.contains("8k") -> 4320
            value.contains("2160p") || value.contains("4k") || value.contains("uhd") -> 2160
            value.contains("1440p") || value.contains("qhd") -> 1440
            value.contains("1080p") || value.contains("fhd") -> 1080
            value.contains("720p") || value == "hd" || value.contains(" hd") -> 720
            value.contains("480p") || value.contains("sd") -> 480
            else -> null
        }
    }

    private fun StreamSource.isIptvSource(): Boolean {
        return extensionId.startsWith("iptv-") ||
            extensionId.startsWith("xtream-") ||
            url.startsWith("xtream://")
    }
}
