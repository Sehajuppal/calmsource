package com.example.calmsource.feature.iptv

import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.entity.ProfileEntity
import com.example.calmsource.core.database.repository.UserMemoryRepository
import com.example.calmsource.core.model.ContinueWatchingItem
import com.example.calmsource.core.model.FavoriteItem
import com.example.calmsource.core.model.RecentChannelItem
import com.example.calmsource.core.model.SearchHistoryItem
import com.example.calmsource.core.model.UserMemoryContentType
import com.example.calmsource.core.model.UserMemoryReference
import com.example.calmsource.core.model.UserPreferenceSignal
import com.example.calmsource.core.model.UserPreferenceSignalType
import com.example.calmsource.core.model.WatchHistoryItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProfileRedirectionTest {

    class TestProfileSessionManager : ProfileSessionManager {
        private val _activeProfile = MutableStateFlow<ProfileEntity?>(null)
        override val activeProfile: StateFlow<ProfileEntity?> = _activeProfile.asStateFlow()

        override suspend fun selectProfile(profileId: String) {
            _activeProfile.value = ProfileEntity(id = profileId, name = "Profile $profileId")
        }

        fun setActiveProfile(profile: ProfileEntity?) {
            _activeProfile.value = profile
        }
    }

    class TestUserMemoryRepository : UserMemoryRepository {
        val observedContinueWatchingProfiles = mutableListOf<String>()
        val observedFavoritesProfiles = mutableListOf<String>()
        val observedWatchHistoryProfiles = mutableListOf<String>()
        val observedPreferenceSignalsProfiles = mutableListOf<String>()

        override fun observeContinueWatching(profileId: String): Flow<List<ContinueWatchingItem>> {
            observedContinueWatchingProfiles.add(profileId)
            return flow {
                emit(listOf(ContinueWatchingItem(
                    reference = UserMemoryReference(itemKey = "cw_$profileId", contentType = UserMemoryContentType.MOVIE, title = "CW $profileId", subtitle = null, providerId = null, sourceId = null),
                    progressMs = 100L,
                    durationMs = 1000L,
                    updatedAt = 12345L
                )))
            }
        }

        override fun observeFavorites(profileId: String): Flow<List<FavoriteItem>> {
            observedFavoritesProfiles.add(profileId)
            return flow {
                emit(listOf(FavoriteItem(
                    reference = UserMemoryReference(itemKey = "fav_$profileId", contentType = UserMemoryContentType.MOVIE, title = "FAV $profileId", subtitle = null, providerId = null, sourceId = null),
                    createdAt = 12345L,
                    updatedAt = 12345L
                )))
            }
        }

        override fun observeIsFavorite(itemKey: String, profileId: String): Flow<Boolean> = flowOf(false)

        override fun observeWatchHistory(profileId: String): Flow<List<WatchHistoryItem>> {
            observedWatchHistoryProfiles.add(profileId)
            return flow {
                emit(listOf(WatchHistoryItem(
                    reference = UserMemoryReference(itemKey = "wh_$profileId", contentType = UserMemoryContentType.MOVIE, title = "WH $profileId", subtitle = null, providerId = null, sourceId = null),
                    firstWatchedAt = 12345L,
                    lastWatchedAt = 12345L,
                    watchCount = 1,
                    progressMs = 100L,
                    durationMs = 1000L
                )))
            }
        }

        override fun observeRecentChannels(profileId: String): Flow<List<RecentChannelItem>> = flowOf(emptyList())
        override fun observeLastWatchedChannel(profileId: String): Flow<RecentChannelItem?> = flowOf(null)
        override fun observeSearchHistory(profileId: String): Flow<List<SearchHistoryItem>> = flowOf(emptyList())

        override fun observePreferenceSignals(profileId: String): Flow<List<UserPreferenceSignal>> {
            observedPreferenceSignalsProfiles.add(profileId)
            return flow {
                emit(listOf(UserPreferenceSignal(
                    signalType = UserPreferenceSignalType.GENRE,
                    signalKey = "genre_$profileId",
                    count = 1,
                    lastSignaledAt = 12345L
                )))
            }
        }

        override suspend fun upsertContinueWatching(reference: UserMemoryReference, progressMs: Long, durationMs: Long, updatedAt: Long, profileId: String) {}
        override suspend fun removeContinueWatching(itemKey: String, profileId: String) {}
        override suspend fun clearContinueWatching(profileId: String) {}
        override suspend fun toggleFavorite(reference: UserMemoryReference, timestamp: Long, profileId: String): Boolean = false
        override suspend fun setFavorite(reference: UserMemoryReference, favorite: Boolean, timestamp: Long, profileId: String) {}
        override suspend fun removeFavorite(itemKey: String, profileId: String) {}
        override suspend fun clearFavorites(profileId: String) {}
        override suspend fun recordWatchHistory(reference: UserMemoryReference, progressMs: Long, durationMs: Long, watchedAt: Long, profileId: String) {}
        override suspend fun removeWatchHistory(itemKey: String, profileId: String) {}
        override suspend fun clearWatchHistory(profileId: String) {}
        override suspend fun recordRecentChannel(reference: UserMemoryReference, watchedAt: Long, profileId: String) {}
        override suspend fun removeRecentChannel(itemKey: String, profileId: String) {}
        override suspend fun clearRecentChannels(profileId: String) {}
        override suspend fun recordSearch(query: String, searchedAt: Long, profileId: String): Boolean = false
        override suspend fun removeSearch(query: String, profileId: String): Boolean = false
        override suspend fun clearSearchHistory(profileId: String) {}
        override suspend fun incrementPreferenceSignal(signalType: UserPreferenceSignalType, signalKey: String, incrementBy: Long, signaledAt: Long, profileId: String) {}
        override suspend fun clearPreferenceSignals(profileId: String) {}
    }

    @Test
    fun testUseCaseRedirectionOnProfileChange() = runTest {
        val sessionManager = TestProfileSessionManager()
        val memoryRepository = TestUserMemoryRepository()
        val useCase = ObserveHomeDataUseCase(memoryRepository, sessionManager)

        // Set initial profile
        sessionManager.setActiveProfile(ProfileEntity("profile_A", "Profile A"))

        // Start collecting from the use case in a separate coroutine
        val collectedUnits = mutableListOf<Unit>()
        val job = launch {
            useCase.execute().collect {
                collectedUnits.add(it)
            }
        }

        // Allow collection to start on Dispatchers.Default using robust polling
        val startTimeA = System.currentTimeMillis()
        while (!(memoryRepository.observedContinueWatchingProfiles.contains("profile_A") &&
                 memoryRepository.observedFavoritesProfiles.contains("profile_A") &&
                 memoryRepository.observedWatchHistoryProfiles.contains("profile_A") &&
                 memoryRepository.observedPreferenceSignalsProfiles.contains("profile_A")) &&
                 System.currentTimeMillis() - startTimeA < 2000) {
            kotlinx.coroutines.delay(10)
        }

        // Assert that repository was queried for profile_A and only profile_A
        println("observedContinueWatchingProfiles: ${memoryRepository.observedContinueWatchingProfiles}")
        println("observedFavoritesProfiles: ${memoryRepository.observedFavoritesProfiles}")
        println("observedWatchHistoryProfiles: ${memoryRepository.observedWatchHistoryProfiles}")
        println("observedPreferenceSignalsProfiles: ${memoryRepository.observedPreferenceSignalsProfiles}")

        assertTrue(memoryRepository.observedContinueWatchingProfiles.contains("profile_A"))
        assertTrue(memoryRepository.observedFavoritesProfiles.contains("profile_A"))
        assertTrue(memoryRepository.observedWatchHistoryProfiles.contains("profile_A"))
        assertTrue(memoryRepository.observedPreferenceSignalsProfiles.contains("profile_A"))

        assertEquals(1, memoryRepository.observedContinueWatchingProfiles.count { it == "profile_A" })
        assertEquals(1, memoryRepository.observedFavoritesProfiles.count { it == "profile_A" })
        assertEquals(1, memoryRepository.observedWatchHistoryProfiles.count { it == "profile_A" })
        assertEquals(1, memoryRepository.observedPreferenceSignalsProfiles.count { it == "profile_A" })

        // Change profile to profile_B
        sessionManager.setActiveProfile(ProfileEntity("profile_B", "Profile B"))
        
        val startTimeB = System.currentTimeMillis()
        while (!(memoryRepository.observedContinueWatchingProfiles.contains("profile_B") &&
                 memoryRepository.observedFavoritesProfiles.contains("profile_B") &&
                 memoryRepository.observedWatchHistoryProfiles.contains("profile_B") &&
                 memoryRepository.observedPreferenceSignalsProfiles.contains("profile_B")) &&
                 System.currentTimeMillis() - startTimeB < 2000) {
            kotlinx.coroutines.delay(10)
        }

        // Assert that repository was queried for profile_B
        assertTrue(memoryRepository.observedContinueWatchingProfiles.contains("profile_B"))
        assertTrue(memoryRepository.observedFavoritesProfiles.contains("profile_B"))
        assertTrue(memoryRepository.observedWatchHistoryProfiles.contains("profile_B"))
        assertTrue(memoryRepository.observedPreferenceSignalsProfiles.contains("profile_B"))

        assertEquals(1, memoryRepository.observedContinueWatchingProfiles.count { it == "profile_B" })
        assertEquals(1, memoryRepository.observedFavoritesProfiles.count { it == "profile_B" })
        assertEquals(1, memoryRepository.observedWatchHistoryProfiles.count { it == "profile_B" })
        assertEquals(1, memoryRepository.observedPreferenceSignalsProfiles.count { it == "profile_B" })

        // Cleanup
        job.cancel()
    }
}
