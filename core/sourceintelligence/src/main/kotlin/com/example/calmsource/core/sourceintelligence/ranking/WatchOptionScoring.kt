package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import com.example.calmsource.core.model.WatchOption

data class ScoredWatchOption(
    val option: WatchOption,
    val score: Int,
    val breakdown: StreamScoreBreakdown,
)

/**
 * Scores [WatchOption]s for details screens using the unified [StreamScoringEngine].
 */
object WatchOptionScoring {

    fun calculateScore(
        source: StreamSource,
        strategy: SortingPreference,
        prefs: UserPreferences = UserPreferences(),
        preferredAudio: List<String> = emptyList(),
        preferredSub: List<String> = emptyList(),
        signals: StreamScoringSignals = StreamScoringSignals(),
    ): Double {
        return StreamScoringEngine.score(
            StreamScoringInput(
                source = source,
                strategy = strategy,
                prefs = prefs,
                preferredAudio = preferredAudio,
                preferredSub = preferredSub,
                signals = signals,
            )
        )
    }

    fun scoreWatchOptionsDetailed(
        options: List<WatchOption>,
        strategy: SortingPreference,
        prefs: UserPreferences,
        preferredAudio: List<String> = emptyList(),
        preferredSub: List<String> = emptyList(),
        signalsFor: (WatchOption) -> StreamScoringSignals = { StreamScoringSignals() },
        deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
    ): List<ScoredWatchOption> {
        return options.map { option ->
            val result = StreamScoringEngine.scoreDetailed(
                StreamScoringInput(
                    source = option.source,
                    strategy = strategy,
                    prefs = prefs,
                    preferredAudio = preferredAudio,
                    preferredSub = preferredSub,
                    signals = signalsFor(option),
                    deviceProfile = deviceProfile,
                )
            )
            ScoredWatchOption(
                option = option,
                score = result.score.toInt(),
                breakdown = result.breakdown,
            )
        }.sortedByDescending { it.score }
    }

    fun scoreWatchOptions(
        options: List<WatchOption>,
        strategy: SortingPreference,
        prefs: UserPreferences,
        preferredAudio: List<String> = emptyList(),
        preferredSub: List<String> = emptyList(),
        signalsFor: (WatchOption) -> StreamScoringSignals = { StreamScoringSignals() },
        deviceProfile: DeviceStreamProfile = DeviceStreamProfile.UNRESTRICTED,
    ): List<Pair<WatchOption, Int>> {
        return scoreWatchOptionsDetailed(
            options = options,
            strategy = strategy,
            prefs = prefs,
            preferredAudio = preferredAudio,
            preferredSub = preferredSub,
            signalsFor = signalsFor,
            deviceProfile = deviceProfile,
        ).map { it.option to it.score }
    }
}
