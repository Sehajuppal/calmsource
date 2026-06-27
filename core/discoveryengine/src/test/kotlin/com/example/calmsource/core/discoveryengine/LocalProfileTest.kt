package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.database.ProfileEntity
import com.example.calmsource.core.discoveryengine.models.LocalProfile
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class LocalProfileTest {

    private lateinit var mockRepository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockRepository = mock(DiscoveryEngineRepository::class.java)

        // Inject mockRepository into DiscoveryEngine singleton using reflection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockRepository)
        
        // Reset activeProfileId to null before each test
        val activeField = DiscoveryEngine::class.java.getDeclaredField("activeProfileId")
        activeField.isAccessible = true
        activeField.set(DiscoveryEngine, null)
    }

    @Test
    fun testSetActiveProfile() {
        runBlocking {
            DiscoveryEngine.setActiveProfile("p-123")
            verify(mockRepository).upsertSetting("active_profile_id", "p-123")
        }
    }

    @Test
    fun testGetActiveProfileCached() {
        runBlocking {
            // Cache active profile
            val activeField = DiscoveryEngine::class.java.getDeclaredField("activeProfileId")
            activeField.isAccessible = true
            activeField.set(DiscoveryEngine, "p-cached")

            val entity = ProfileEntity(
                id = "p-cached",
                name = "Cached User",
                avatarUrl = "avatar",
                createdAt = 123456789L
            )
            `when`(mockRepository.getProfile("p-cached")).thenReturn(entity)

            val activeProfile = DiscoveryEngine.getActiveProfile()
            assertEquals("p-cached", activeProfile?.id)
            assertEquals("Cached User", activeProfile?.name)
            verify(mockRepository, never()).getSetting(anyString())
        }
    }

    @Test
    fun testGetActiveProfileFromDatabase() {
        runBlocking {
            `when`(mockRepository.getSetting("active_profile_id")).thenReturn("p-db")
            val entity = ProfileEntity(
                id = "p-db",
                name = "Database User",
                avatarUrl = null,
                createdAt = 123456789L
            )
            `when`(mockRepository.getProfile("p-db")).thenReturn(entity)

            val activeProfile = DiscoveryEngine.getActiveProfile()
            assertEquals("p-db", activeProfile?.id)
            assertEquals("Database User", activeProfile?.name)
            verify(mockRepository).getSetting("active_profile_id")
        }
    }

    @Test
    fun testUpdateProfile() {
        runBlocking {
            val existing = ProfileEntity(
                id = "p-1",
                name = "Old Name",
                avatarUrl = "old-avatar",
                createdAt = 100L
            )
            `when`(mockRepository.getProfile("p-1")).thenReturn(existing)

            DiscoveryEngine.updateProfile("p-1", "New Name", "new-avatar")

            val expectedUpdated = ProfileEntity(
                id = "p-1",
                name = "New Name",
                avatarUrl = "new-avatar",
                createdAt = 100L
            )
            verify(mockRepository).upsertProfile(expectedUpdated)
        }
    }

    @Test
    fun testDeleteProfile() {
        runBlocking {
            // Cache active profile
            val activeField = DiscoveryEngine::class.java.getDeclaredField("activeProfileId")
            activeField.isAccessible = true
            activeField.set(DiscoveryEngine, "p-delete")

            DiscoveryEngine.deleteProfile("p-delete")

            verify(mockRepository).deleteProfile("p-delete")
            verify(mockRepository).upsertSetting("active_profile_id", "")
            assertNull(DiscoveryEngine.getActiveProfile())
        }
    }
}
