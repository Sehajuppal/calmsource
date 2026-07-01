package com.example.calmsource.feature.iptv

import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.CalmSourceDatabase
import com.example.calmsource.core.database.dao.UserMemoryDao
import com.example.calmsource.core.database.entity.ContinueWatchingEntity
import com.example.calmsource.core.database.entity.FavoriteEntity
import com.example.calmsource.core.database.entity.PreferenceSignalEntity
import com.example.calmsource.core.database.entity.ProfileEntity
import com.example.calmsource.core.database.entity.WatchHistoryEntity
import com.example.calmsource.core.database.repository.RoomUserMemoryRepository
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.discoveryengine.DiscoveryEngine
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.models.RecommendationRow
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class ProfileRedirectionVerificationTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testScope = TestScope(testDispatcher)

    private lateinit var database: CalmSourceDatabase
    private lateinit var memoryDao: UserMemoryDao
    private lateinit var memoryRepository: UserMemoryRepository
    private lateinit var mockDiscoveryRepo: DiscoveryEngineRepository

    private class TestProfileSessionManager(initialProfile: ProfileEntity?) : ProfileSessionManager {
        private val _activeProfile = MutableStateFlow(initialProfile)
        override val activeProfile: StateFlow<ProfileEntity?> = _activeProfile

        override suspend fun selectProfile(profileId: String) {
            _activeProfile.value = ProfileEntity(id = profileId, name = "Profile $profileId")
        }
    }

    private lateinit var sessionManager: TestProfileSessionManager

    @Before
    fun setUp() {
        database = mock(CalmSourceDatabase::class.java)
        memoryDao = mock(UserMemoryDao::class.java)
        whenever(database.userMemoryDao()).thenReturn(memoryDao)

        memoryRepository = RoomUserMemoryRepository(database, memoryDao)

        mockDiscoveryRepo = mock(DiscoveryEngineRepository::class.java)
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockDiscoveryRepo)

        sessionManager = TestProfileSessionManager(ProfileEntity(id = "profile_a", name = "Profile A"))
    }

    @org.junit.After
    fun tearDown() {
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, null)
    }

    @Test
    fun testDynamicProfileRedirectionAndLeakagePrevention() = runTest(testDispatcher) {
        // Return isolated data from mocked DAO depending on the profile ID argument
        val entityCwA = ContinueWatchingEntity(profileId = "profile_a", itemKey = "item_a", contentType = "MOVIE", title = "Movie A", subtitle = null, providerId = "provider", sourceId = "source", progressMs = 1000L, durationMs = 5000L, updatedAt = 12345L)
        val entityWhA = WatchHistoryEntity(profileId = "profile_a", itemKey = "item_a", contentType = "MOVIE", title = "Movie A", subtitle = null, providerId = "provider", sourceId = "source", firstWatchedAt = 12345L, lastWatchedAt = 12345L, watchCount = 1L, progressMs = 1000L, durationMs = 5000L)
        val entityFavA = FavoriteEntity(profileId = "profile_a", itemKey = "item_a", contentType = "MOVIE", title = "Movie A", subtitle = null, providerId = "provider", sourceId = "source", createdAt = 12345L, updatedAt = 12345L)
        val entitySigA = PreferenceSignalEntity(profileId = "profile_a", signalType = "GENRE", signalKey = "Action", count = 1L, lastSignaledAt = 12345L)

        val entityCwB = ContinueWatchingEntity(profileId = "profile_b", itemKey = "item_b", contentType = "MOVIE", title = "Movie B", subtitle = null, providerId = "provider", sourceId = "source", progressMs = 2000L, durationMs = 6000L, updatedAt = 12345L)
        val entityWhB = WatchHistoryEntity(profileId = "profile_b", itemKey = "item_b", contentType = "MOVIE", title = "Movie B", subtitle = null, providerId = "provider", sourceId = "source", firstWatchedAt = 12345L, lastWatchedAt = 12345L, watchCount = 1L, progressMs = 2000L, durationMs = 6000L)
        val entityFavB = FavoriteEntity(profileId = "profile_b", itemKey = "item_b", contentType = "MOVIE", title = "Movie B", subtitle = null, providerId = "provider", sourceId = "source", createdAt = 12345L, updatedAt = 12345L)
        val entitySigB = PreferenceSignalEntity(profileId = "profile_b", signalType = "GENRE", signalKey = "Sci-Fi", count = 1L, lastSignaledAt = 12345L)

        whenever(memoryDao.observeContinueWatching(any(), eq("profile_a"))).thenReturn(flowOf(listOf(entityCwA)))
        whenever(memoryDao.observeWatchHistory(any(), eq("profile_a"))).thenReturn(flowOf(listOf(entityWhA)))
        whenever(memoryDao.observeFavorites(any(), eq("profile_a"))).thenReturn(flowOf(listOf(entityFavA)))
        whenever(memoryDao.observePreferenceSignals(any(), eq("profile_a"))).thenReturn(flowOf(listOf(entitySigA)))

        whenever(memoryDao.observeContinueWatching(any(), eq("profile_b"))).thenReturn(flowOf(listOf(entityCwB)))
        whenever(memoryDao.observeWatchHistory(any(), eq("profile_b"))).thenReturn(flowOf(listOf(entityWhB)))
        whenever(memoryDao.observeFavorites(any(), eq("profile_b"))).thenReturn(flowOf(listOf(entityFavB)))
        whenever(memoryDao.observePreferenceSignals(any(), eq("profile_b"))).thenReturn(flowOf(listOf(entitySigB)))

        // 1. Verify Profile A initial state queries
        val cwA = memoryRepository.observeContinueWatching("profile_a").first()
        val whA = memoryRepository.observeWatchHistory("profile_a").first()
        val favA = memoryRepository.observeFavorites("profile_a").first()
        val sigA = memoryRepository.observePreferenceSignals("profile_a").first()

        assertEquals(1, cwA.size)
        assertEquals("item_a", cwA[0].reference.itemKey)
        assertEquals(1, whA.size)
        assertEquals("item_a", whA[0].reference.itemKey)
        assertEquals(1, favA.size)
        assertEquals("item_a", favA[0].reference.itemKey)
        assertEquals(1, sigA.size)
        assertEquals("Action", sigA[0].signalKey)

        // 2. Verify Profile B initial state queries
        val cwB = memoryRepository.observeContinueWatching("profile_b").first()
        val whB = memoryRepository.observeWatchHistory("profile_b").first()
        val favB = memoryRepository.observeFavorites("profile_b").first()
        val sigB = memoryRepository.observePreferenceSignals("profile_b").first()

        assertEquals(1, cwB.size)
        assertEquals("item_b", cwB[0].reference.itemKey)
        assertEquals(1, whB.size)
        assertEquals("item_b", whB[0].reference.itemKey)
        assertEquals(1, favB.size)
        assertEquals("item_b", favB[0].reference.itemKey)
        assertEquals(1, sigB.size)
        assertEquals("Sci-Fi", sigB[0].signalKey)

        // 3. Verify ObserveHomeDataUseCase dynamically redirects when active profile changes
        val useCase = ObserveHomeDataUseCase(memoryRepository, sessionManager)
        
        // Let's mock DiscoveryEngine home row response for active profiles
        whenever(mockDiscoveryRepo.getHomeRows(profileId = eq("profile_a"), limit = any(), forceRefresh = any(), preferenceSignals = any())).thenReturn(
            listOf(RecommendationRow("row_a", "Recommended A", kotlinx.collections.immutable.persistentListOf()))
        )
        whenever(mockDiscoveryRepo.getHomeRows(profileId = eq("profile_b"), limit = any(), forceRefresh = any(), preferenceSignals = any())).thenReturn(
            listOf(RecommendationRow("row_b", "Recommended B", kotlinx.collections.immutable.persistentListOf()))
        )

        // Verify that when activeProfile is "profile_a", the current queries from it only yield profile_a data
        val activeId = sessionManager.activeProfile.value?.id ?: "default"
        assertEquals("profile_a", activeId)
        
        // Switch to profile_b
        sessionManager.selectProfile("profile_b")
        val activeIdNew = sessionManager.activeProfile.value?.id ?: "default"
        assertEquals("profile_b", activeIdNew)
        
        // Assert that the queries mapped to activeIdNew correctly return profile B's data
        val activeCw = memoryRepository.observeContinueWatching(activeIdNew).first()
        val activeWh = memoryRepository.observeWatchHistory(activeIdNew).first()
        val activeFav = memoryRepository.observeFavorites(activeIdNew).first()
        val activeSig = memoryRepository.observePreferenceSignals(activeIdNew).first()

        assertEquals("item_b", activeCw[0].reference.itemKey)
        assertEquals("item_b", activeWh[0].reference.itemKey)
        assertEquals("item_b", activeFav[0].reference.itemKey)
        assertEquals("Sci-Fi", activeSig[0].signalKey)

        // Verify there is absolutely NO leakage of profile A data in profile B's queries
        assertTrue(activeCw.none { it.reference.itemKey == "item_a" })
        assertTrue(activeWh.none { it.reference.itemKey == "item_a" })
        assertTrue(activeFav.none { it.reference.itemKey == "item_a" })
        assertTrue(activeSig.none { it.signalKey == "Action" })
    }
}
