package com.example.calmsource.core.discoveryengine.ranking

import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import com.example.calmsource.core.discoveryengine.providers.EnrichmentFeatures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HybridRecommendationRankerTest {

    @Test
    fun testRankRecommendations() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = mapOf("sci-fi" to 1.0),
            languageAffinities = mapOf("en" to 1.0),
            sourceAffinities = emptyMap()
        )

        // Candidate 1: Sci-Fi Movie (rating 7.0, en)
        val candidateSciFi = MediaItemEntity("m-1", "movie", "SciFi Hit", "overview", null, 7.0, 2020, "Sci-Fi", "", null, "en", 1000L, null, "{}", "stremio", null, null, null, "scifi hit", 0L)
        // Candidate 2: Drama Movie (rating 8.0, fr)
        val candidateDrama = MediaItemEntity("m-2", "movie", "Drama Hit", "overview", null, 8.0, 2020, "Drama", "", null, "fr", 1000L, null, "{}", "stremio", null, null, null, "drama hit", 0L)

        val recommendations = HybridRecommendationRanker.rank(
            tasteProfile = tasteProfile,
            candidates = listOf(candidateSciFi, candidateDrama),
            userItemStates = emptyMap(),
            limit = 10,
            currentYear = 2020
        )

        assertEquals(2, recommendations.size)
        // SciFi hit should rank first because of the saturated genre boost (+12.0) and language boost (+10.0).
        // With unknown availability, the soft multiplier is 0.7; brand new titles add +6 freshness.
        // SciFi Hit score: ((7.0*2) + 12.0 + 10.0) * 0.7 + 6.0 = 31.2
        // Drama Hit score: (8.0*2) * 0.7 + 6.0 = 17.2
        assertEquals("m-1", recommendations[0].id)
        assertEquals(31.2, recommendations[0].score, 0.01)
        assertEquals("m-2", recommendations[1].id)
        assertEquals(17.2, recommendations[1].score, 0.01)
    }

    @Test
    fun providerEnrichmentAddsSmallBonusWithoutReplacingBaseRanking() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = emptyMap(),
            languageAffinities = emptyMap(),
            sourceAffinities = emptyMap()
        )
        val enriched = MediaItemEntity("m-enriched", "movie", "Enriched", null, null, 7.0, 2020, "", "", null, null, null, null, "{}", null, null, null, null, "enriched", 0L)
        val plain = MediaItemEntity("m-plain", "movie", "Plain", null, null, 7.0, 2020, "", "", null, null, null, null, "{}", null, null, null, null, "plain", 0L)

        val recommendations = HybridRecommendationRanker.rank(
            tasteProfile = tasteProfile,
            candidates = listOf(plain, enriched),
            userItemStates = emptyMap(),
            enrichmentFeatures = { mediaId ->
                if (mediaId == "m-enriched") {
                    EnrichmentFeatures(
                        mediaId = mediaId,
                        averageRating = 9.0,
                        ratingCount = 1,
                        availabilityCount = 2,
                        bestQuality = "1080p"
                    )
                } else {
                    EnrichmentFeatures(mediaId = mediaId)
                }
            }
        )

        assertEquals("m-enriched", recommendations.first().id)
        assertTrue(recommendations.first().score > recommendations[1].score)
    }

    @Test
    fun saturatedGenreBoostDoesNotDilutePrimaryAffinityOnMultiGenreItems() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = mapOf("action" to 1.0, "sci-fi" to 0.0),
            languageAffinities = emptyMap(),
            sourceAffinities = emptyMap()
        )
        val mixedGenre = MediaItemEntity("m-mixed", "movie", "Mixed", null, null, 7.0, 2024, "Action,Sci-Fi", "", null, null, null, null, "{}", null, null, null, null, "mixed", 0L)

        val recommendation = HybridRecommendationRanker.rank(
            tasteProfile = tasteProfile,
            candidates = listOf(mixedGenre),
            userItemStates = emptyMap(),
            currentYear = 2024
        ).single()

        assertEquals(12.0, recommendation.scoreBreakdown.profileBoost, 0.01)
    }

    @Test
    fun availableStreamsUseMultiplierToOutrankOtherwiseEqualUnavailableItems() {
        val tasteProfile = TasteProfile(
            profileId = "p-1",
            genreAffinities = emptyMap(),
            languageAffinities = emptyMap(),
            sourceAffinities = emptyMap()
        )
        val unavailable = MediaItemEntity("m-unavailable", "movie", "Unavailable", null, null, 7.0, 2024, "", "", null, null, null, null, "{}", null, null, null, null, "unavailable", 0L)
        val available = MediaItemEntity("m-available", "movie", "Available", null, null, 7.0, 2024, "", "", null, null, null, null, "{}", null, null, null, null, "available", 0L)

        val recommendations = HybridRecommendationRanker.rank(
            tasteProfile = tasteProfile,
            candidates = listOf(unavailable, available),
            userItemStates = emptyMap(),
            currentYear = 2024,
            enrichmentFeatures = { mediaId ->
                if (mediaId == "m-available") {
                    EnrichmentFeatures(
                        mediaId = mediaId,
                        availabilityCount = 5,
                        bestQuality = "1080p"
                    )
                } else {
                    EnrichmentFeatures(mediaId = mediaId)
                }
            }
        )

        assertEquals("m-available", recommendations.first().id)
        assertEquals(1.0, recommendations.first().scoreBreakdown.availabilityScore, 0.01)
        assertTrue(recommendations.first().score > recommendations[1].score)
    }
}
