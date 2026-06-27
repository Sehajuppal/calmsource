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

class TypoTolerantSearchTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
        
        // Ensure FTSMode is STANDARD for fallback testing
        val modeField = FtsIndexManager::class.java.getDeclaredField("activeMode")
        modeField.isAccessible = true
        modeField.set(FtsIndexManager, FtsIndexManager.FtsMode.STANDARD)
        DiscoverySearchFeatureFlags.enableFuzzyFallback = true
    }

    @After
    fun tearDown() {
        DiscoverySearchFeatureFlags.enableFuzzyFallback = false
    }

    @Test
    fun testSuggestionsFallbackToSuggestionsTable() = runBlocking {
        val query = "spid"
        
        // Mock suggestion table match
        val mockSuggestions = listOf(
            SuggestionEntity("Spider-Man", 10.0, "spid", System.currentTimeMillis()),
            SuggestionEntity("Spider-Man 2", 9.0, "spid", System.currentTimeMillis())
        )
        `when`(mockDao.getSuggestionsByPrefix(query, 10)).thenReturn(mockSuggestions)

        val results = repository.searchSuggestions(query, "profile-1", 10)
        
        assertEquals(2, results.size)
        assertEquals("Spider-Man", results[0].query)
        assertEquals(10.0, results[0].score, 0.01)
        assertEquals("Spider-Man 2", results[1].query)
    }

    @Test
    fun testSuggestionsFallbackToMediaAndChannels() = runBlocking {
        val query = "spid"
        
        // Suggestions table is empty
        `when`(mockDao.getSuggestionsByPrefix(query, 10)).thenReturn(emptyList())
        
        // Media items match
        val mockMedia = listOf(
            createMockMedia("m-1", "Spider-Man", "spiderman", 9.0)
        )
        `when`(mockDao.getMediaItemsByPrefix("spid", 10)).thenReturn(mockMedia)
        
        // Channels match
        val mockChannels = listOf(
            ChannelEntity("c-1", "Spider Channel", null, "url", "Action", "provider", null, 0L)
        )
        `when`(mockDao.getChannelsByPrefix("spid", 10)).thenReturn(mockChannels)

        val results = repository.searchSuggestions(query, "profile-1", 10)
        
        assertEquals(2, results.size)
        // Order by score descending: Spider-Man (9.0) first, Spider Channel (6.0) second
        assertEquals("Spider-Man", results[0].query)
        assertEquals(9.0, results[0].score, 0.01)
        assertEquals("Spider Channel", results[1].query)
        assertEquals(6.0, results[1].score, 0.01)
    }

    @Test
    fun testFullSearchLevenshteinFallback() = runBlocking {
        val query = "spidremna" // Typo of "spiderman" (distance 4)
        
        // We expect Levenshtein distance matching. Since "spidremna".length > 3, max distance allowed is 2.
        // Let's test with a closer typo: "spidreman" (distance 2)
        val queryClose = "spidreman"
        
        val candidates = listOf(
            createMockMedia("m-1", "Spider-Man", "spiderman", 8.5),
            createMockMedia("m-2", "Batman", "batman", 9.0)
        )
        `when`(mockDao.getSearchCandidates(500)).thenReturn(candidates)
        `when`(mockDao.getChannelCandidates(200)).thenReturn(emptyList())
        `when`(mockDao.getMediaItemsByIds(listOf("m-1"))).thenReturn(listOf(candidates[0]))
        `when`(mockDao.getUserItemStatesForProfile("profile-1")).thenReturn(emptyList())

        val results = repository.fullSearch(queryClose, "profile-1", 10)
        
        assertEquals(1, results.size)
        assertEquals("m-1", results[0].id)
        assertEquals("Spider-Man", results[0].title)
    }

    private fun createMockMedia(id: String, title: String, normTitle: String, rating: Double): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = "movie",
            title = title,
            overview = "Overview",
            posterUrl = null,
            rating = rating,
            releaseYear = 2020,
            genres = "",
            cast = "",
            director = null,
            language = "en",
            durationMs = 120000L,
            externalId = null,
            externalIdsJson = "{}",
            source = "stremio",
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            normalizedTitle = normTitle,
            updatedAt = System.currentTimeMillis()
        )
    }
}
