package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class HomeSimplificationRefactorTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
    }

    private fun createMockMedia(id: String, title: String, type: String, genres: String): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = type,
            title = title,
            overview = "Overview",
            posterUrl = null,
            rating = 8.0,
            releaseYear = 2020,
            genres = genres,
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
            normalizedTitle = title.lowercase(),
            updatedAt = System.currentTimeMillis()
        )
    }

    private fun createMockChannel(id: String, name: String, category: String): ChannelEntity {
        return ChannelEntity(
            id = id,
            name = name,
            logoUrl = null,
            streamUrl = "url",
            category = category,
            providerId = "provider",
            tvgId = null,
            updatedAt = 0L
        )
    }

    @Test
    fun testEmptyQueryWithNoFiltersReturnsEmpty() = runBlocking {
        val results = repository.fullSearch("", "profile-1", 10, emptyMap())
        assertTrue(results.isEmpty())
    }

    @Test
    fun testEmptyQueryWithTypeMovieFilter() = runBlocking {
        val movies = listOf(
            createMockMedia("m-1", "Inception", "movie", "Sci-Fi"),
            createMockMedia("m-2", "Interstellar", "movie", "Sci-Fi")
        )
        val series = listOf(
            createMockMedia("s-1", "Breaking Bad", "series", "Drama")
        )
        val channels = listOf(
            createMockChannel("c-1", "HBO", "Movies")
        )

        // Mock DAO responses
        `when`(mockDao.getSearchCandidates(500)).thenReturn(movies + series)
        `when`(mockDao.getChannelCandidates(200)).thenReturn(channels)
        `when`(mockDao.getMediaItemsByIds(anyList())).thenAnswer { invocation ->
            val ids = invocation.arguments[0] as List<String>
            (movies + series).filter { it.id in ids }
        }
        `when`(mockDao.getUserItemStatesForProfile("profile-1")).thenReturn(emptyList())

        // Run search with empty query and movie filter
        val results = repository.fullSearch("", "profile-1", 10, mapOf("type" to "movie"))

        assertEquals(2, results.size)
        assertTrue(results.all { it.type == "movie" })
        assertTrue(results.any { it.id == "m-1" })
        assertTrue(results.any { it.id == "m-2" })
    }

    @Test
    fun testEmptyQueryWithTypeSeriesFilter() = runBlocking {
        val movies = listOf(
            createMockMedia("m-1", "Inception", "movie", "Sci-Fi")
        )
        val series = listOf(
            createMockMedia("s-1", "Breaking Bad", "series", "Drama"),
            createMockMedia("s-2", "Better Call Saul", "series", "Drama")
        )
        val channels = listOf(
            createMockChannel("c-1", "HBO", "Movies")
        )

        // Mock DAO responses
        `when`(mockDao.getSearchCandidates(500)).thenReturn(movies + series)
        `when`(mockDao.getChannelCandidates(200)).thenReturn(channels)
        `when`(mockDao.getMediaItemsByIds(anyList())).thenAnswer { invocation ->
            val ids = invocation.arguments[0] as List<String>
            (movies + series).filter { it.id in ids }
        }
        `when`(mockDao.getUserItemStatesForProfile("profile-1")).thenReturn(emptyList())

        // Run search with empty query and series filter
        val results = repository.fullSearch("", "profile-1", 10, mapOf("type" to "series"))

        assertEquals(2, results.size)
        assertTrue(results.all { it.type == "series" })
        assertTrue(results.any { it.id == "s-1" })
        assertTrue(results.any { it.id == "s-2" })
    }

    @Test
    fun testEmptyQueryWithGenreFilter() = runBlocking {
        val items = listOf(
            createMockMedia("m-1", "Inception", "movie", "Sci-Fi,Thriller"),
            createMockMedia("m-2", "The Hangover", "movie", "Comedy"),
            createMockMedia("s-1", "Breaking Bad", "series", "Drama,Thriller")
        )

        // Mock DAO responses
        `when`(mockDao.getSearchCandidates(500)).thenReturn(items)
        `when`(mockDao.getChannelCandidates(200)).thenReturn(emptyList())
        `when`(mockDao.getMediaItemsByIds(anyList())).thenAnswer { invocation ->
            val ids = invocation.arguments[0] as List<String>
            items.filter { it.id in ids }
        }
        `when`(mockDao.getUserItemStatesForProfile("profile-1")).thenReturn(emptyList())

        // Run search with empty query and genre = thriller
        val results = repository.fullSearch("", "profile-1", 10, mapOf("genre" to "thriller"))

        assertEquals(2, results.size)
        assertTrue(results.any { it.id == "m-1" })
        assertTrue(results.any { it.id == "s-1" })
    }
}
