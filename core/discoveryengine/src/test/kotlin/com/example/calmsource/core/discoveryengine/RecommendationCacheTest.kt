package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.collections.immutable.toImmutableList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

@kotlinx.serialization.Serializable
private data class RecommendationCacheWrapper(
    val version: Int,
    val rows: List<RecommendationRow>
)

class RecommendationCacheTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    private fun captureRecommendationCache(captor: ArgumentCaptor<RecommendationCacheEntity>): RecommendationCacheEntity {
        captor.capture()
        return RecommendationCacheEntity("", "", "", 0L)
    }

    private fun captureWatchEvent(captor: ArgumentCaptor<WatchEventEntity>): WatchEventEntity {
        captor.capture()
        return WatchEventEntity("", "", "", 0L, 0L, 0L, "")
    }

    private fun captureEngineSetting(captor: ArgumentCaptor<EngineSettingEntity>): EngineSettingEntity {
        captor.capture()
        return EngineSettingEntity("", "")
    }

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
    }

    @Test
    fun testCacheRecommendations() {
        runBlocking {
            val profileId = "profile-1"
            val key = "home_rows"
            val scoreBreakdown = ScoreBreakdown()
            val items = listOf(RecommendationItem("m-1", "movie", "Title", 10.0, "Reason", scoreBreakdown))
            val rows = listOf(RecommendationRow("Continue", "continue_watching", items.toImmutableList()))

            repository.cacheRecommendations(profileId, key, rows)

            val captor = ArgumentCaptor.forClass(RecommendationCacheEntity::class.java)
            verify(mockDao).upsertRecommendationCache(captureRecommendationCache(captor))

            val captured = captor.value
            assertEquals(profileId, captured.profileId)
            assertEquals(key, captured.cacheKey)
            val wrapper = Json.decodeFromString<RecommendationCacheWrapper>(captured.data)
            assertEquals(1, wrapper.rows.size)
            assertEquals("Continue", wrapper.rows[0].title)
        }
    }

    @Test
    fun testGetCachedRecommendationsHit() {
        runBlocking {
            val profileId = "profile-1"
            val key = "home_rows"
            val scoreBreakdown = ScoreBreakdown()
            val items = listOf(RecommendationItem("m-1", "movie", "Title", 10.0, "Reason", scoreBreakdown))
            val rows = listOf(RecommendationRow("Continue", "continue_watching", items.toImmutableList()))
            val wrapper = RecommendationCacheWrapper(version = 0, rows = rows)
            val serialized = Json.encodeToString(wrapper)

            val entity = RecommendationCacheEntity(profileId, key, serialized, System.currentTimeMillis())
            `when`(mockDao.getRecommendationCache(profileId, key)).thenReturn(entity)

            val cached = repository.getCachedRecommendations(profileId, key, 600000L) // 10 min TTL
            assertNotNull(cached)
            assertEquals(1, cached?.size)
            assertEquals("Continue", cached?.get(0)?.title)
        }
    }

    @Test
    fun testGetCachedRecommendationsExpired() {
        runBlocking {
            val profileId = "profile-1"
            val key = "home_rows"
            val scoreBreakdown = ScoreBreakdown()
            val items = listOf(RecommendationItem("m-1", "movie", "Title", 10.0, "Reason", scoreBreakdown))
            val rows = listOf(RecommendationRow("Continue", "continue_watching", items.toImmutableList()))
            val wrapper = RecommendationCacheWrapper(version = 0, rows = rows)
            val serialized = Json.encodeToString(wrapper)

            // Age: 12 minutes ago
            val entity = RecommendationCacheEntity(profileId, key, serialized, System.currentTimeMillis() - 720000L)
            `when`(mockDao.getRecommendationCache(profileId, key)).thenReturn(entity)

            // TTL: 10 minutes (600000ms)
            val cached = repository.getCachedRecommendations(profileId, key, 600000L)
            assertNull(cached)
            verify(mockDao).deleteRecommendationCache(profileId, key)
        }
    }

    @Test
    fun testInvalidationOnWatchEvent() {
        runBlocking {
            val event = WatchEvent("profile-1", "m-1", "movie", System.currentTimeMillis(), 1000L, 5000L, "progress")

            repository.insertWatchEvent(event)

            val captor = ArgumentCaptor.forClass(WatchEventEntity::class.java)
            verify(mockDao).insertWatchEvent(captureWatchEvent(captor))
            verify(mockDao).clearRecommendationCacheForProfile("profile-1")
        }
    }

    @Test
    fun testInvalidationOnPreferences() {
        runBlocking {
            repository.toggleFavorite("profile-1", "m-1", "movie", true)
            verify(mockDao, times(1)).clearRecommendationCacheForProfile("profile-1")

            repository.toggleHidden("profile-1", "m-2", "movie", true)
            verify(mockDao, times(2)).clearRecommendationCacheForProfile("profile-1")
        }
    }

    @Test
    fun testInvalidationOnIngestsAndPacks() {
        runBlocking {
            // 1. upsertMediaItems
            repository.upsertMediaItems(emptyList())

            // 2. upsertChannels
            repository.upsertChannels(emptyList())

            // 3. upsertEpgPrograms
            repository.upsertEpgPrograms(emptyList())

            // 4. installPack
            repository.installPack("pack-1", emptyList(), emptyList())

            // 5. uninstallPack
            repository.uninstallPack("pack-1")

            val captor = ArgumentCaptor.forClass(EngineSettingEntity::class.java)
            verify(mockDao, atLeast(5)).upsertSetting(captureEngineSetting(captor))
            val values = captor.allValues
            assert(values.isNotEmpty())
            values.forEach {
                assertEquals("recommendation_cache_version", it.key)
            }
        }
    }
}
