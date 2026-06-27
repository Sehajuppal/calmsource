package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class QualityEvaluationIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
        DiscoverySearchFeatureFlags.enableFuzzyFallback = true
    }

    @After
    fun tearDown() {
        DiscoverySearchFeatureFlags.enableFuzzyFallback = false
    }

    @Test
    fun testCalculateSearchQualityIntegration() = runBlocking {
        val profileId = "profile-1"
        val query = "Inception"
        val targetId = "m-inception"

        // Mock candidates for fullSearch to rank
        val candidateItems = listOf(
            MediaItemEntity(
                id = "m-other", type = "movie", title = "The Matrix", overview = "Sci-Fi action",
                posterUrl = null, rating = 8.7, releaseYear = 1999, genres = "Sci-Fi", cast = "",
                director = null, language = "en", durationMs = 0L, externalId = null,
                externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "thematrix", updatedAt = 0L
            ),
            MediaItemEntity(
                id = targetId, type = "movie", title = "Inception", overview = "dream sharing heist movie",
                posterUrl = null, rating = 8.8, releaseYear = 2010, genres = "Sci-Fi", cast = "",
                director = null, language = "en", durationMs = 0L, externalId = null,
                externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
                episodeNumber = null, normalizedTitle = "inception", updatedAt = 0L
            )
        )

        // Mock DAO: fullSearch retrieves search candidates when FTS returns empty
        `when`(mockDao.getSearchCandidates(500)).thenReturn(candidateItems)
        `when`(mockDao.getChannelCandidates(200)).thenReturn(emptyList())

        // Mock user states: Inception is user's favorite (boosts ranking)
        val favoriteState = UserItemStateEntity(
            profileId = profileId,
            itemId = targetId,
            isFavorite = true,
            isHidden = false,
            lastWatchedAt = 1000L,
            progressMs = 100L,
            durationMs = 100L,
            watchCount = 1,
            isCompleted = true
        )
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(listOf(favoriteState))

        // Calculate search quality
        val metrics = repository.calculateSearchQuality(profileId, query, targetId)

        println("=== Search Quality Integration Metrics ===")
        println("Target Item: Inception, MRR: ${metrics.mrr}, Precision@3: ${metrics.precisionAt3}")

        // Inception should rank #1 because it matches exactly and is a favorite
        assertEquals(1.0, metrics.mrr, 0.001)
        assertEquals(1.0, metrics.hitAt3, 0.001)
        assertEquals(0.3333333333333333, metrics.precisionAt3, 0.001)
    }

    @Test
    fun testCalculateRecommendationDiversityIntegration() = runBlocking {
        val profileId = "profile-1"

        // Mock Home rows returning items with different genres
        val movie1 = MediaItemEntity(
            id = "m-1", type = "movie", title = "Sci-Fi Item 1", overview = "",
            posterUrl = null, rating = 8.0, releaseYear = 2020, genres = "Sci-Fi,Action", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "scifiitem1", updatedAt = 0L
        )
        val movie2 = MediaItemEntity(
            id = "m-2", type = "movie", title = "Sci-Fi Item 2", overview = "",
            posterUrl = null, rating = 7.5, releaseYear = 2021, genres = "Sci-Fi,Adventure", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "scifiitem2", updatedAt = 0L
        )
        val movie3 = MediaItemEntity(
            id = "m-3", type = "movie", title = "Romance Item 1", overview = "",
            posterUrl = null, rating = 9.0, releaseYear = 2019, genres = "Romance,Drama", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "romanceitem1", updatedAt = 0L
        )

        // Mock Home Row Generation:
        // Recently Added row
        `when`(mockDao.getRecentlyAdded(45)).thenReturn(listOf(movie1, movie2, movie3))
        // Other rows empty or minimal
        `when`(mockDao.getContinueWatchingStates(profileId, 15)).thenReturn(emptyList())
        `when`(mockDao.getTopRated(45)).thenReturn(emptyList())
        `when`(mockDao.getAllChannels(45)).thenReturn(emptyList())
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(emptyList())

        // Mock getMediaItemsByIds call in calculateRecommendationDiversity
        `when`(mockDao.getMediaItemsByIds(listOf("m-3", "m-1", "m-2"))).thenReturn(listOf(movie3, movie1, movie2))

        // Calculate diversity
        val diversity = repository.calculateRecommendationDiversity(profileId)

        println("=== Recommendation Diversity Integration Metrics ===")
        println("Simpson's Diversity Index: $diversity")

        // Simpson Index of genres [sci-fi, action, sci-fi, adventure, romance, drama]
        // Counts: sci-fi: 2, action: 1, adventure: 1, romance: 1, drama: 1. Total = 6.
        // sum((count/total)^2) = (2/6)^2 + (1/6)^2 * 4 = 4/36 + 4/36 = 8/36 = 0.222
        // Simpson Index = 1.0 - 0.222 = 0.777
        assertTrue("Simpson Index should represent healthy genre diversity (> 0.5)", diversity > 0.5)
        assertEquals(0.777, diversity, 0.01)
    }
}
