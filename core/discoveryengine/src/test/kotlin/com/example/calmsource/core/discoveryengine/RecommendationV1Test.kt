package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.RecommendationRow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class RecommendationV1Test {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)

        // Inject mockRepository into DiscoveryEngine singleton using reflection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, repository)
    }

    @Test
    fun testGetHomeRowsSuccess() = runBlocking {
        val profileId = "prof-1"

        // 1. Mock Continue Watching
        val continueState = UserItemStateEntity(
            profileId = profileId,
            itemId = "m-continue",
            isFavorite = false,
            isHidden = false,
            lastWatchedAt = 1000L,
            progressMs = 50L,
            durationMs = 100L,
            watchCount = 1,
            isCompleted = false
        )
        `when`(mockDao.getContinueWatchingStates(profileId, 15)).thenReturn(listOf(continueState))
        val continueMedia = createMockMedia("m-continue", "Unfinished Movie", 8.0)
        `when`(mockDao.getMediaItemsByIds(listOf("m-continue"))).thenReturn(listOf(continueMedia))

        // 2. Mock Recently Added
        val recentMedia = listOf(
            createMockMedia("m-recent-1", "New Movie 1", 7.5),
            createMockMedia("m-recent-2", "New Movie 2", 6.0)
        )
        `when`(mockDao.getRecentlyAdded(45)).thenReturn(recentMedia)

        // 3. Mock Top Rated
        val topMedia = listOf(
            createMockMedia("m-top-1", "Top Movie 1", 9.5),
            createMockMedia("m-top-2", "Top Movie 2", 9.0)
        )
        `when`(mockDao.getTopRated(45)).thenReturn(topMedia)

        // 4. Mock Live TV
        val channels = listOf(
            ChannelEntity("c-1", "Sport Channel", null, "url", "Sports", "provider", null, 0L)
        )
        `when`(mockDao.getAllChannels(45)).thenReturn(channels)
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())
        
        val epgProgram = EpgProgramEntity(
            id = "epg-1",
            channelId = "c-1",
            title = "Live Football Match",
            description = "Live game",
            category = "Sports",
            startTime = 0L,
            endTime = System.currentTimeMillis() + 100000L,
            language = "en",
            episodeNum = null,
            updatedAt = 0L
        )
        `when`(mockDao.getCurrentEpgPrograms(anyLong())).thenReturn(listOf(epgProgram))

        // Execute
        val rows = DiscoveryEngine.getHomeRows(profileId)

        assertEquals(4, rows.size)

        // Verify Continue Watching
        val continueRow = rows.first { it.rowType == "continue_watching" }
        assertEquals("Continue Watching", continueRow.title)
        assertEquals(1, continueRow.items.size)
        assertEquals("Unfinished Movie", continueRow.items[0].title)
        assertEquals("Resume watching (50% completed)", continueRow.items[0].reason)

        // Verify Recently Added
        val recentRow = rows.first { it.rowType == "recently_added" }
        assertEquals("Recently Added", recentRow.title)
        assertEquals(2, recentRow.items.size)
        assertEquals("New Movie 1", recentRow.items[0].title)

        // Verify Top Rated
        val topRow = rows.first { it.rowType == "top_rated" }
        assertEquals("Top Rated", topRow.title)
        assertEquals(2, topRow.items.size)
        assertEquals("Top Movie 1", topRow.items[0].title)

        // Verify Live TV
        val liveRow = rows.first { it.rowType == "live_tv" }
        assertEquals("Live TV Recommendations", liveRow.title)
        assertEquals(1, liveRow.items.size)
        assertEquals("Sport Channel", liveRow.items[0].title)
        assertEquals("Live Now: Live Football Match", liveRow.items[0].reason)
    }

    private fun createMockMedia(id: String, title: String, rating: Double): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = "movie",
            title = title,
            overview = "Overview",
            posterUrl = null,
            rating = rating,
            releaseYear = 2024,
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
            normalizedTitle = title.lowercase(),
            updatedAt = System.currentTimeMillis()
        )
    }
}
