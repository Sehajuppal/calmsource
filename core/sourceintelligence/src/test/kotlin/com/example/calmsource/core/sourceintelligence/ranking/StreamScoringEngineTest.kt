package com.example.calmsource.core.sourceintelligence.ranking

import com.example.calmsource.core.model.SortingPreference
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamScoringEngineTest {

    private fun source(
        id: String,
        title: String,
        resolution: String = "1080p",
        sizeBytes: Long? = null,
        language: String = "English",
        seeds: Int? = null,
    ) = StreamSource(
        id = id,
        name = title,
        url = "https://example.com/$id",
        extensionId = "ext-torrentio",
        resolution = resolution,
        videoCodec = "HEVC",
        audioCodec = null,
        sizeBytes = sizeBytes,
        seeds = seeds,
        language = language,
        isSubbed = false,
        isDubbed = false,
        isDualAudio = false,
        headers = emptyMap(),
        rawTitle = title,
    )

    @Test
    fun bestMatchPrefersReasonable4KOver1080p() {
        val light4K = source(
            id = "light-4k",
            title = "Movie 4K AV1 HDR10 👥 100",
            resolution = "4K",
            sizeBytes = 12L * 1024 * 1024 * 1024,
            seeds = 100,
        )
        val sweet1080p = source(
            id = "sweet-1080p",
            title = "Movie 1080p HEVC 👥 100",
            resolution = "1080p",
            sizeBytes = 5L * 1024 * 1024 * 1024,
            seeds = 100,
        )

        val ranked = StreamScoringEngine.scoreBatch(
            sources = listOf(sweet1080p, light4K),
            strategy = SortingPreference.BEST_MATCH,
            prefs = UserPreferences(primaryLanguage = "English", secondaryLanguage = ""),
        )

        assertEquals("light-4k", ranked.first().first.id)
    }

    @Test
    fun bestMatchPrefersBalanced1080pOverHeavy4K() {
        val heavy4K = source(
            id = "heavy-4k",
            title = "Movie 4K Remux 👥 100",
            resolution = "4K",
            sizeBytes = 25L * 1024 * 1024 * 1024,
        )
        val sweet1080p = source(
            id = "sweet-1080p",
            title = "Movie 1080p HEVC 👥 100",
            resolution = "1080p",
            sizeBytes = 5L * 1024 * 1024 * 1024,
        )

        val ranked = StreamScoringEngine.scoreBatch(
            sources = listOf(heavy4K, sweet1080p),
            strategy = SortingPreference.BEST_MATCH,
            prefs = UserPreferences(primaryLanguage = "English", secondaryLanguage = ""),
        )

        assertEquals("sweet-1080p", ranked.first().first.id)
    }

    @Test
    fun highestQualityPrefersHeavy4K() {
        val heavy4K = source(
            id = "heavy-4k",
            title = "Movie 4K Remux Dolby Vision 👥 100",
            resolution = "4K",
            sizeBytes = 45L * 1024 * 1024 * 1024,
        )
        val sweet1080p = source(
            id = "sweet-1080p",
            title = "Movie 1080p HEVC 👥 100",
            resolution = "1080p",
            sizeBytes = 5L * 1024 * 1024 * 1024,
        )

        val ranked = StreamScoringEngine.scoreBatch(
            sources = listOf(sweet1080p, heavy4K),
            strategy = SortingPreference.HIGHEST_QUALITY,
            prefs = UserPreferences(primaryLanguage = "English", secondaryLanguage = ""),
        )

        assertEquals("heavy-4k", ranked.first().first.id)
    }

    @Test
    fun primaryLanguageOutranksHigherResolution() {
        val english1080 = source(
            id = "english-1080",
            title = "Movie 1080p English",
            resolution = "1080p",
            language = "English",
        )
        val hindi4k = source(
            id = "hindi-4k",
            title = "Movie 4K Hindi",
            resolution = "4K",
            language = "Hindi",
        )
        val prefs = UserPreferences(primaryLanguage = "English", secondaryLanguage = "Hindi")

        val englishScore = StreamScoringEngine.score(
            StreamScoringInput(source = english1080, strategy = SortingPreference.BEST_MATCH, prefs = prefs)
        )
        val hindiScore = StreamScoringEngine.score(
            StreamScoringInput(source = hindi4k, strategy = SortingPreference.BEST_MATCH, prefs = prefs)
        )

        assertTrue(englishScore > hindiScore)
    }

    @Test
    fun recentSourceHealthBoostsScore() {
        val source = source(id = "src-1", title = "Movie 1080p")
        val base = StreamScoringEngine.score(
            StreamScoringInput(source = source, strategy = SortingPreference.BEST_MATCH)
        )
        val boosted = StreamScoringEngine.score(
            StreamScoringInput(
                source = source,
                strategy = SortingPreference.BEST_MATCH,
                signals = StreamScoringSignals(lastSuccessWithin24h = true),
            )
        )
        assertTrue(boosted > base)
    }

    @Test
    fun playbackFailurePenaltyDecaysOverTime() {
        val now = System.currentTimeMillis()
        val recent = StreamScoringEngine.playbackDecayWeight(now - 1L)
        val twoWeeksAgo = StreamScoringEngine.playbackDecayWeight(now - (14L * 24 * 60 * 60 * 1000))
        val noHistory = StreamScoringEngine.playbackDecayWeight(0L)

        assertEquals(1.0, noHistory, 0.001)
        assertTrue(recent > twoWeeksAgo)
        assertEquals(0.5, twoWeeksAgo, 0.05)
    }

    @Test
    fun scoreDetailedIncludesBreakdownReasons() {
        val source = source(id = "src-1", title = "Movie 1080p HEVC", sizeBytes = 5L * 1024 * 1024 * 1024)
        val result = StreamScoringEngine.scoreDetailed(
            StreamScoringInput(source = source, strategy = SortingPreference.BEST_MATCH)
        )
        assertTrue(result.breakdown.reasons.isNotEmpty())
        assertEquals(result.score, result.breakdown.totalScore, 0.001)
    }

    @Test
    fun phoneProfilePenalizes4KOver1080pCap() {
        val fourK = source(id = "4k", title = "Movie 2160p HEVC", resolution = "4K")
        val hd = source(id = "1080", title = "Movie 1080p HEVC", resolution = "1080p")
        val phoneProfile = DeviceStreamProfile(maxRecommendedHeight = 1080)

        val fourKScore = StreamScoringEngine.score(
            StreamScoringInput(
                source = fourK,
                strategy = SortingPreference.BEST_MATCH,
                deviceProfile = phoneProfile,
            )
        )
        val hdScore = StreamScoringEngine.score(
            StreamScoringInput(
                source = hd,
                strategy = SortingPreference.BEST_MATCH,
                deviceProfile = phoneProfile,
            )
        )
        assertTrue(hdScore > fourKScore)
    }
}
