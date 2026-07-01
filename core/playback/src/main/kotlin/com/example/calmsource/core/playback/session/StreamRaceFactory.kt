package com.example.calmsource.core.playback.session

import android.content.Context
import android.content.pm.PackageManager
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.playback.Media3StreamProbe
import com.example.calmsource.core.playback.StreamRaceManager
import com.example.calmsource.core.playback.StreamRaceRanker
import com.example.calmsource.core.playback.StreamRaceRequest
import com.example.calmsource.core.playback.StreamRaceResult
import com.example.calmsource.core.sourceintelligence.ranking.DeviceStreamProfile
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
        val isTelevision = runCatching {
            val pm = context.packageManager
            pm != null && (pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) || pm.hasSystemFeature(PackageManager.FEATURE_TELEVISION))
        }.getOrDefault(false)
        val deviceProfile = DeviceStreamProfile.forPlayback(isTelevision, prefs)
        val healthBySourceId = StreamScoringSupport.prefetchSourceHealth(
            request.candidates.map { it.safeSourceId }
        )
        val ranker = StreamRaceRanker { source ->
            StreamScoringEngine.score(
                StreamScoringInput(
                    source = source.toStreamSourceForScoring(),
                    strategy = strategy,
                    prefs = prefs,
                    signals = StreamScoringSupport.signalsFromHealth(
                        sourceHealth = healthBySourceId[source.safeSourceId]
                    ),
                    deviceProfile = deviceProfile,
                )
            )
        }
        return StreamRaceManager(
            probe = Media3StreamProbe(context),
            ranker = ranker,
        ).race(request)
    }
}
