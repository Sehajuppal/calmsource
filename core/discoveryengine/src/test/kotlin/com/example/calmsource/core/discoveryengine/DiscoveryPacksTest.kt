package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.models.DiscoveryPack
import com.example.calmsource.core.discoveryengine.models.MediaItem
import com.example.calmsource.core.discoveryengine.models.MediaStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.*

class DiscoveryPacksTest {

    private lateinit var mockRepository: DiscoveryEngineRepository

    private fun captureDiscoveryPack(captor: ArgumentCaptor<DiscoveryPack>): DiscoveryPack {
        captor.capture()
        return DiscoveryPack("", "", null, "", false, null)
    }

    @Before
    fun setUp() {
        mockRepository = mock(DiscoveryEngineRepository::class.java)

        // Inject mockRepository into DiscoveryEngine singleton using reflection
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, mockRepository)
    }

    @After
    fun tearDown() {
        val repoField = DiscoveryEngine::class.java.getDeclaredField("repository")
        repoField.isAccessible = true
        repoField.set(DiscoveryEngine, null)
    }

    @Test
    fun testRegisterDiscoveryPack() {
        runBlocking {
            DiscoveryEngine.registerDiscoveryPack("p-1", "Anime Pack", "Anime metadata", "manifest_url")

            val captor = ArgumentCaptor.forClass(DiscoveryPack::class.java)
            verify(mockRepository).registerPack(captureDiscoveryPack(captor))
            assertEquals("p-1", captor.value.id)
            assertEquals("Anime Pack", captor.value.name)
            assertEquals("manifest_url", captor.value.manifestUrl)
        }
    }

    @Test
    fun testInstallDiscoveryPack() {
        runBlocking {
            val items = listOf(MediaItem("m-1", "movie", "Title"))
            val streams = listOf(MediaStream("s-1", "m-1", "Title", "url"))

            DiscoveryEngine.installDiscoveryPack("p-1", items, streams)

            verify(mockRepository).installPack("p-1", items, streams)
        }
    }

    @Test
    fun testUninstallDiscoveryPack() {
        runBlocking {
            DiscoveryEngine.uninstallDiscoveryPack("p-1")

            verify(mockRepository).uninstallPack("p-1")
        }
    }

    @Test
    fun testGetAvailableDiscoveryPacks() {
        runBlocking {
            val packsList = listOf(DiscoveryPack("p-1", "Pack 1", null, "url", true, 123L))
            `when`(mockRepository.getAvailablePacks()).thenReturn(packsList)

            val results = DiscoveryEngine.getAvailableDiscoveryPacks()
            assertEquals(1, results.size)
            assertEquals("p-1", results[0].id)
            assertEquals("Pack 1", results[0].name)
        }
    }
}
