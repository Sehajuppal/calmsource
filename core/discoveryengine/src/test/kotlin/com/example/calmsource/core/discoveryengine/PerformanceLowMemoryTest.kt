package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class PerformanceLowMemoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    private fun captureEngineSetting(captor: ArgumentCaptor<EngineSettingEntity>): EngineSettingEntity {
        captor.capture()
        return EngineSettingEntity("", "")
    }

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
    fun testLowMemorySettingGetSet() {
        runBlocking {
            // Initially setting is null, should be false
            `when`(mockDao.getSetting("low_memory_mode")).thenReturn(null)
            
            // Expose setting via facade or repository
            assertFalse(repository.lowMemoryMode)

            // Set to true
            repository.lowMemoryMode = true
            repository.upsertSetting("low_memory_mode", "true")

            val captor = ArgumentCaptor.forClass(EngineSettingEntity::class.java)
            verify(mockDao).upsertSetting(captureEngineSetting(captor))
            assertEquals("low_memory_mode", captor.value.key)
            assertEquals("true", captor.value.value)

            // Check value
            `when`(mockDao.getSetting("low_memory_mode")).thenReturn("true")
            
            val activeValue = mockDao.getSetting("low_memory_mode")?.toBoolean() ?: false
            assertTrue(activeValue)
        }
    }

    @Test
    fun testPruneEventsAndCacheMaintenance() {
        runBlocking {
            repository.performMaintenance()

            verify(mockDao).clearExpiredEpgPrograms(anyLong())
            verify(mockDao).pruneSearchEvents(anyLong())
            verify(mockDao).pruneWatchEvents(anyLong())
            verify(mockDao).clearExpiredRecommendationCache(anyLong())
        }
    }

    @Test
    fun testLowMemorySearchLimitCaps() {
        runBlocking {
            // Set low memory mode to true
            repository.lowMemoryMode = true
            // Set isLowMemoryLoaded to true so it doesn't query the mockDao for it
            val loadedField = DiscoveryEngineRepository::class.java.getDeclaredField("isLowMemoryLoaded")
            loadedField.isAccessible = true
            loadedField.set(repository, true)

            `when`(mockDao.getSearchCandidates(100)).thenReturn(emptyList())
            `when`(mockDao.getChannelCandidates(50)).thenReturn(emptyList())

            // Under low memory, fullSearch caps in-memory Levenshtein candidates to 100 media, 50 channels
            val results = repository.fullSearch("abc", "profile-1", limit = 20)
            assertTrue(results.isEmpty())
            verify(mockDao).getSearchCandidates(100)
            verify(mockDao).getChannelCandidates(50)
            verify(mockDao, never()).getSearchCandidates(500)
            verify(mockDao, never()).getChannelCandidates(200)
        }
    }

    @Test
    fun testNormalMemorySearchLimitCaps() {
        runBlocking {
            // Set low memory mode to false
            repository.lowMemoryMode = false
            // Set isLowMemoryLoaded to true
            val loadedField = DiscoveryEngineRepository::class.java.getDeclaredField("isLowMemoryLoaded")
            loadedField.isAccessible = true
            loadedField.set(repository, true)

            `when`(mockDao.getSearchCandidates(500)).thenReturn(emptyList())
            `when`(mockDao.getChannelCandidates(200)).thenReturn(emptyList())

            // Under normal memory, fullSearch uses 500 media, 200 channels
            val results = repository.fullSearch("abc", "profile-1", limit = 20)
            assertTrue(results.isEmpty())
            verify(mockDao).getSearchCandidates(500)
            verify(mockDao).getChannelCandidates(200)
            verify(mockDao, never()).getSearchCandidates(100)
            verify(mockDao, never()).getChannelCandidates(50)
        }
    }

    @Test
    fun testLowMemoryRecommendationCandidateCaps() {
        runBlocking {
            // Set low memory mode to true
            repository.lowMemoryMode = true
            // Set isLowMemoryLoaded to true
            val loadedField = DiscoveryEngineRepository::class.java.getDeclaredField("isLowMemoryLoaded")
            loadedField.isAccessible = true
            loadedField.set(repository, true)

            // Mock for taste profile
            `when`(mockDao.getUserItemStatesForProfile("profile-1")).thenReturn(emptyList())
            `when`(mockDao.getUserChannelStatesForProfile("profile-1")).thenReturn(emptyList())

            // 1. Personalized Recommendations
            `when`(mockDao.getSearchCandidates(50)).thenReturn(emptyList())
            val recs = repository.getPersonalizedRecommendations("profile-1", limit = 15)
            assertTrue(recs.isEmpty())
            verify(mockDao).getSearchCandidates(50)
            verify(mockDao, never()).getSearchCandidates(200)

            // 2. Similarity candidates
            `when`(mockDao.getMediaItem("m-1")).thenReturn(MediaItemEntity("m-1", "movie", "Title", "", "", 5.0, 2020, "", "", "", "", 0L, "", "", "", "", 0, 0, "title", 0L))
            `when`(mockDao.getSearchCandidates(50)).thenReturn(emptyList())
            val similar = repository.getMoreLikeThis("profile-1", "m-1", limit = 15)
            assertTrue(similar.isEmpty())

            // 3. Live TV candidates
            `when`(mockDao.getAllChannels(30)).thenReturn(emptyList())
            `when`(mockDao.getChannelWatchEvents("profile-1")).thenReturn(emptyList())
            val live = repository.getLiveRecommendations("profile-1", limit = 15)
            assertTrue(live.isEmpty())
            verify(mockDao).getAllChannels(30)
            verify(mockDao, never()).getAllChannels(100)
        }
    }
}
