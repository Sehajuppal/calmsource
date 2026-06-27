package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.WatchEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class EventTrackingIntegrationTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    private fun captureUserItemState(captor: ArgumentCaptor<UserItemStateEntity>): UserItemStateEntity {
        captor.capture()
        return UserItemStateEntity("", "", false, false, 0L, 0L, 0L, 0, false)
    }

    private fun captureUserChannelState(captor: ArgumentCaptor<UserChannelStateEntity>): UserChannelStateEntity {
        captor.capture()
        return UserChannelStateEntity("", "", false, false, 0L, 0)
    }

    private fun anyWatchEventEntity(): WatchEventEntity {
        any(WatchEventEntity::class.java)
        return WatchEventEntity("", "", "", 0L, 0L, 0L, "")
    }

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
    fun testTrackWatchEventUpdatesUserItemState() = runBlocking {
        val event = WatchEvent(
            profileId = "prof-1",
            itemId = "item-1",
            itemType = "movie",
            timestamp = 1000L,
            progressMs = 960L, // 96% progress (completed with 95% threshold)
            durationMs = 1000L,
            eventType = "progress"
        )
        
        `when`(mockDao.getUserItemState("prof-1", "item-1")).thenReturn(null)

        DiscoveryEngine.trackWatchEvent(event)

        // Verify watch event is inserted using non-null helper
        verify(mockDao).insertWatchEvent(anyWatchEventEntity())

        // Verify UserItemStateEntity is upserted
        val captor = ArgumentCaptor.forClass(UserItemStateEntity::class.java)
        verify(mockDao).upsertUserItemState(captureUserItemState(captor))

        val state = captor.value
        assertEquals("prof-1", state.profileId)
        assertEquals("item-1", state.itemId)
        assertEquals(960L, state.progressMs)
        assertEquals(1000L, state.durationMs)
        assertEquals(1, state.watchCount)
        assertTrue(state.isCompleted)
    }

    @Test
    fun testTrackWatchEventUpdatesUserChannelState() = runBlocking {
        val event = WatchEvent(
            profileId = "prof-1",
            itemId = "chan-1",
            itemType = "channel",
            timestamp = 2000L,
            progressMs = 0L,
            durationMs = 0L,
            eventType = "start"
        )

        `when`(mockDao.getUserChannelState("prof-1", "chan-1")).thenReturn(null)

        DiscoveryEngine.trackWatchEvent(event)

        val captor = ArgumentCaptor.forClass(UserChannelStateEntity::class.java)
        verify(mockDao).upsertUserChannelState(captureUserChannelState(captor))

        val state = captor.value
        assertEquals("prof-1", state.profileId)
        assertEquals("chan-1", state.channelId)
        assertEquals(2000L, state.lastWatchedAt)
        assertEquals(1, state.watchCount)
    }

    @Test
    fun testTogglePreferences() = runBlocking {
        `when`(mockDao.getUserItemState("p-1", "m-1")).thenReturn(null)

        DiscoveryEngine.toggleFavorite("p-1", "m-1", "movie", true)

        val captor = ArgumentCaptor.forClass(UserItemStateEntity::class.java)
        verify(mockDao).upsertUserItemState(captureUserItemState(captor))
        
        assertTrue(captor.value.isFavorite)
        assertEquals("m-1", captor.value.itemId)
    }
}
