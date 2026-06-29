package com.example.calmsource.core.playback.session

import android.content.Context
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.playback.Media3StreamProbe
import com.example.calmsource.core.playback.StreamRaceManager
import com.example.calmsource.core.playback.StreamRaceRanker
import com.example.calmsource.core.playback.StreamRaceRequest
import com.example.calmsource.core.playback.StreamRaceResult
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringEngine
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringInput
import com.example.calmsource.core.sourceintelligence.ranking.StreamScoringSupport
import com.example.calmsource.core.sourceintelligence.ranking.toStreamSourceForScoring

fun interface StreamRaceFactory {
    suspend fun race(context: Context, request: StreamRaceRequest): StreamRaceResult
}

object DefaultStreamRaceFactory : StreamRaceFactory {
    override suspend fun race(context: Context, request: StreamRaceRequest): StreamRaceResult {
        val prefs = UserPreferencesRepository.preferences.value
        val strategy = if (prefs.preferHighestQuality) {
            SortingPreference.HIGHEST_QUALITY
        } else {
            SortingPreference.BEST_MATCH
        }
        val healthBySourceId = request.candidates.associate { candidate ->
            candidate.safeSourceId to runCatching {
                SourceHealthRepository.getSourceHealth(candidate.safeSourceId, readonly = true)
            }.getOrNull()
        }
        val ranker = StreamRaceRanker { source ->
            StreamScoringEngine.score(
                StreamScoringInput(
                    source = source.toStreamSourceForScoring(),
                    strategy = strategy,
                    prefs = prefs,
                    signals = StreamScoringSupport.signalsFromHealth(
                        sourceHealth = healthBySourceId[source.safeSourceId]
                    ),
                )
            )
        }
        return StreamRaceManager(
            probe = Media3StreamProbe(context),
            ranker = ranker,
        ).race(request)
    }
}
