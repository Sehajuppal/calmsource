package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.database.ProfileEntity
import com.example.calmsource.core.discoveryengine.database.WatchEventEntity
import com.example.calmsource.core.discoveryengine.models.WatchEvent
import com.example.calmsource.core.discoveryengine.debug.FakeDataGenerator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class DiscoveryEngineFacadeTest {

    private lateinit var mockRepository: DiscoveryEngineRepository

    private fun captureProfileEntity(captor: ArgumentCaptor<ProfileEntity>): ProfileEntity {
        captor.capture()
        return ProfileEntity(
            id = "",
            name = "",
            avatarUrl = null,
            createdAt = 0L
        )
    }

    private fun captureWatchEvent(captor: ArgumentCaptor<WatchEvent>): WatchEvent {
        captor.capture()
        return WatchEvent("", "", "", 0L, 0L, 0L, "")
    }

    @Before
    fun setUp() {
        mockRepository = mock(DiscoveryEngineRepository::class.java)
        
        // Inject mockRepository into DiscoveryEngine singleton using reflection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockRepository)
    }

    @Test
    fun testIngestStremioItems() = runBlocking {
        val items = FakeDataGenerator.generateMediaItems()
        `when`(mockRepository.upsertMediaItems(items)).thenReturn(items.associate { it.id to it.id })
        val stats = DiscoveryEngine.ingestStremioItems(items)

        verify(mockRepository).upsertMediaItems(items)
        assertEquals(items.size, stats.insertedCount)
        assertNotNull(stats.durationMs)
    }

    @Test
    fun testIngestStremioStreams() = runBlocking {
        val streams = FakeDataGenerator.generateMediaStreams()
        val stats = DiscoveryEngine.ingestStremioStreams(streams)

        verify(mockRepository).upsertMediaStreams(streams)
        assertEquals(streams.size, stats.insertedCount)
    }

    @Test
    fun testIngestIptvChannels() = runBlocking {
        val channels = FakeDataGenerator.generateIptvChannels()
        `when`(mockRepository.upsertChannels(channels)).thenReturn(channels.size)
        val stats = DiscoveryEngine.ingestIptvChannels(channels)

        verify(mockRepository).upsertChannels(channels)
        assertEquals(channels.size, stats.insertedCount)
    }

    @Test
    fun testIngestEpgPrograms() = runBlocking {
        val programs = FakeDataGenerator.generateEpgPrograms()
        `when`(mockRepository.upsertEpgPrograms(programs)).thenReturn(programs.size)
        val stats = DiscoveryEngine.ingestEpgPrograms(programs)

        verify(mockRepository).upsertEpgPrograms(programs)
        assertEquals(programs.size, stats.insertedCount)
    }

    @Test
    fun testTrackWatchEvent() = runBlocking {
        val event = FakeDataGenerator.generateWatchEvents().first()
        DiscoveryEngine.trackWatchEvent(event)

        val captor = ArgumentCaptor.forClass(WatchEvent::class.java)
        verify(mockRepository).insertWatchEvent(captureWatchEvent(captor))
        assertEquals(event.itemId, captor.value.itemId)
    }

    @Test
    fun testCreateProfile() = runBlocking {
        val profileId = DiscoveryEngine.createProfile("Test User")
        assertNotNull(profileId)

        val captor = ArgumentCaptor.forClass(ProfileEntity::class.java)
        verify(mockRepository).upsertProfile(captureProfileEntity(captor))
        assertEquals("Test User", captor.value.name)
        assertEquals(profileId, captor.value.id)
    }

    @Test
    fun testGetAllProfiles() = runBlocking {
        val mockEntities = listOf(
            ProfileEntity(id = "p1", name = "User One", avatarUrl = "url1", createdAt = 12345L),
            ProfileEntity(id = "p2", name = "User Two", avatarUrl = null, createdAt = 67890L)
        )
        `when`(mockRepository.getAllProfiles()).thenReturn(mockEntities)

        val profiles = DiscoveryEngine.getAllProfiles()
        assertEquals(2, profiles.size)
        assertEquals("p1", profiles[0].id)
        assertEquals("User One", profiles[0].name)
        assertEquals("url1", profiles[0].avatarUrl)
        assertEquals("p2", profiles[1].id)
        assertEquals("User Two", profiles[1].name)
    }
}
