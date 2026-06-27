package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class DiscoveryEngineRepositoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    private fun captureMediaItemList(captor: ArgumentCaptor<List<MediaItemEntity>>): List<MediaItemEntity> {
        captor.capture()
        return emptyList()
    }

    private fun captureWatchEvent(captor: ArgumentCaptor<WatchEventEntity>): WatchEventEntity {
        captor.capture()
        return WatchEventEntity(
            profileId = "",
            itemId = "",
            itemType = "",
            timestamp = 0L,
            progressMs = 0L,
            durationMs = 0L,
            eventType = ""
        )
    }

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
    }


    @Test
    fun testUpsertMediaItemsMapping() = runBlocking {
        val mediaItem = MediaItem(
            id = "m-123",
            type = "movie",
            title = "Inception",
            overview = "A dream within a dream",
            rating = 8.8,
            releaseYear = 2010,
            genres = listOf("Sci-Fi", "Thriller"),
            externalIds = mapOf("imdb" to "tt1375666")
        )

        repository.upsertMediaItems(listOf(mediaItem))

        // Capture arguments sent to DAO
        val captor = ArgumentCaptor.forClass(List::class.java) as ArgumentCaptor<List<MediaItemEntity>>
        verify(mockDao).upsertMediaItems(captureMediaItemList(captor))

        val capturedList = captor.value
        assertEquals(1, capturedList.size)
        val entity = capturedList[0]
        assertEquals("m-123", entity.id)
        assertEquals("Inception", entity.title)
        assertEquals("movie", entity.type)
        assertEquals("A dream within a dream", entity.overview)
        assertEquals("Sci-Fi,Thriller", entity.genres)
        assertEquals("tt1375666", entity.externalId)
    }

    @Test
    fun testInsertWatchEventMapping() = runBlocking {
        val event = WatchEvent(
            profileId = "profile-abc",
            itemId = "item-xyz",
            itemType = "episode",
            timestamp = 123456789L,
            progressMs = 10000L,
            durationMs = 2400000L,
            eventType = "progress"
        )

        repository.insertWatchEvent(event)

        val captor = ArgumentCaptor.forClass(WatchEventEntity::class.java)
        verify(mockDao).insertWatchEvent(captureWatchEvent(captor))

        val entity = captor.value
        assertEquals("profile-abc", entity.profileId)
        assertEquals("item-xyz", entity.itemId)
        assertEquals("episode", entity.itemType)
        assertEquals(123456789L, entity.timestamp)
        assertEquals(10000L, entity.progressMs)
    }

    @Test
    fun testGetDatabaseStats() = runBlocking {
        `when`(mockDao.getProfileCount()).thenReturn(2)
        `when`(mockDao.getMediaItemCount()).thenReturn(50)
        `when`(mockDao.getMediaStreamCount()).thenReturn(120)
        `when`(mockDao.getChannelCount()).thenReturn(300)
        `when`(mockDao.getEpgProgramCount()).thenReturn(5000)

        val stats = repository.getDatabaseStats()

        assertEquals(2, stats["profiles"])
        assertEquals(50, stats["media_items"])
        assertEquals(120, stats["media_streams"])
        assertEquals(300, stats["channels"])
        assertEquals(5000, stats["epg_programs"])
    }
}

