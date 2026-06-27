package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.models.RecommendationItem
import com.example.calmsource.core.discoveryengine.models.ScoreBreakdown
import com.example.calmsource.core.discoveryengine.models.TasteProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class DiscoveryEngineRecommendationsIntegrationTest {

    private lateinit var mockRepository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockRepository = mock(DiscoveryEngineRepository::class.java)

        // Inject mockRepository into DiscoveryEngine singleton using reflection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockRepository)
    }

    @Test
    fun testGetTasteProfile() {
        runBlocking {
            val mockProfile = TasteProfile("p-1", mapOf("action" to 1.0), emptyMap(), emptyMap())
            `when`(mockRepository.getTasteProfile("p-1")).thenReturn(mockProfile)

            val profile = DiscoveryEngine.getTasteProfile("p-1")
            assertEquals("p-1", profile.profileId)
            assertEquals(1.0, profile.genreAffinities["action"]!!, 0.01)
        }
    }

    @Test
    fun testGetMoreLikeThis() {
        runBlocking {
            val mockRecommendation = RecommendationItem("m-2", "movie", "Similar Movie", 95.0, "Reason", ScoreBreakdown())
            `when`(mockRepository.getMoreLikeThis("p-1", "m-1", 15)).thenReturn(listOf(mockRecommendation))

            val results = DiscoveryEngine.getMoreLikeThis("p-1", "m-1")
            assertEquals(1, results.size)
            assertEquals("Similar Movie", results[0].title)
        }
    }

    @Test
    fun testGetLiveNowRecommendations() {
        runBlocking {
            val mockRecommendation = RecommendationItem("c-1", "channel", "Live Channel", 50.0, "Live now", ScoreBreakdown())
            `when`(mockRepository.getLiveRecommendations("p-1", 15)).thenReturn(listOf(mockRecommendation))

            val results = DiscoveryEngine.getLiveNowRecommendations("p-1")
            assertEquals(1, results.size)
            assertEquals("Live Channel", results[0].title)
        }
    }
}
