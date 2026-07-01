package com.example.calmsource.feature.iptv

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayInputStream
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.model.ProviderSyncState
import com.example.calmsource.core.model.IPTVChannel

class IPTVRepositoryChaosTest {

    @Before
    fun setup() {
        resetRepositoryState()
    }

    private suspend fun waitUntil(timeoutMs: Long = 5000L, condition: suspend () -> Boolean) {
        val start = System.currentTimeMillis()
        while (!condition()) {
            if (System.currentTimeMillis() - start > timeoutMs) {
                throw AssertionError("Timeout waiting for condition")
            }
            delay(10)
        }
    }

    private fun waitForSettled() {
        try {
            val tickField = IPTVRepository::class.java.getDeclaredField("dataUpdateTick")
            tickField.isAccessible = true
            val tickFlow = tickField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Long>
            
            kotlinx.coroutines.runBlocking {
                var lastTick = tickFlow.value
                var stableCount = 0
                while (stableCount < 5) {
                    kotlinx.coroutines.delay(10)
                    val currentTick = tickFlow.value
                    if (currentTick == lastTick) {
                        stableCount++
                    } else {
                        lastTick = currentTick
                        stableCount = 0
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun resetRepositoryState() {
        waitForSettled()
        try {
            // First, resolve the fallback DAO and clear its in-memory collections BEFORE database reset
            // This prevents asynchronous databaseReady triggers from querying dirty cached data.
            val method = IPTVRepository::class.java.getDeclaredMethod("resolveDao")
            method.isAccessible = true
            val dao = method.invoke(IPTVRepository) as com.example.calmsource.core.database.dao.IPTVDao

            try {
                val chanMemField = dao.javaClass.getDeclaredField("chanMem")
                chanMemField.isAccessible = true
                chanMemField.set(dao, emptyList<com.example.calmsource.core.database.entity.IPTVChannelEntity>())
            } catch (e: NoSuchFieldException) {
                // Room DAO might not have chanMem, ignore
            }

            try {
                val provMemField = dao.javaClass.getDeclaredField("provMem")
                provMemField.isAccessible = true
                provMemField.set(dao, emptyList<com.example.calmsource.core.database.entity.IPTVProviderEntity>())
            } catch (e: NoSuchFieldException) {
                // Room DAO might not have provMem, ignore
            }

            try {
                val provFlowField = dao.javaClass.getDeclaredField("provFlow")
                provFlowField.isAccessible = true
                val provFlow = provFlowField.get(dao) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.calmsource.core.database.entity.IPTVProviderEntity>>
                provFlow.value = emptyList<com.example.calmsource.core.database.entity.IPTVProviderEntity>()
            } catch (e: NoSuchFieldException) {
                // Room DAO might not have provFlow, ignore
            }

            try {
                val chanFlowField = dao.javaClass.getDeclaredField("chanFlow")
                chanFlowField.isAccessible = true
                val chanFlow = chanFlowField.get(dao) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.calmsource.core.database.entity.IPTVChannelEntity>>
                chanFlow.value = emptyList<com.example.calmsource.core.database.entity.IPTVChannelEntity>()
            } catch (e: NoSuchFieldException) {
                // Room DAO might not have chanFlow, ignore
            }

            // Now perform database reset
            com.example.calmsource.core.database.DatabaseProvider.resetForTesting()

            // Reset IPTVRepository internal parsed and sorted caches
            val parsedField = IPTVRepository::class.java.getDeclaredField("parsedChannels")
            parsedField.isAccessible = true
            parsedField.set(IPTVRepository, emptyList<com.example.calmsource.core.model.IPTVChannel>())

            val sortedField = IPTVRepository::class.java.getDeclaredField("sortedChannelsCache")
            sortedField.isAccessible = true
            sortedField.set(IPTVRepository, emptyList<com.example.calmsource.core.model.IPTVChannel>())

            val channelsFlowField = IPTVRepository::class.java.getDeclaredField("_channels")
            channelsFlowField.isAccessible = true
            val channelsFlow = channelsFlowField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<List<com.example.calmsource.core.model.IPTVChannel>>
            channelsFlow.value = emptyList<com.example.calmsource.core.model.IPTVChannel>()

            val channelsReadyField = IPTVRepository::class.java.getDeclaredField("_channelsReady")
            channelsReadyField.isAccessible = true
            val channelsReadyFlow = channelsReadyField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Boolean>
            channelsReadyFlow.value = false

            val tickField = IPTVRepository::class.java.getDeclaredField("dataUpdateTick")
            tickField.isAccessible = true
            val tickFlow = tickField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Long>
            tickFlow.value = 0L

            val syncStatesField = IPTVRepository::class.java.getDeclaredField("_syncStates")
            syncStatesField.isAccessible = true
            val syncStatesFlow = syncStatesField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, ProviderSyncState>>
            syncStatesFlow.value = emptyMap()

            val epgCacheField = IPTVRepository::class.java.getDeclaredField("epgCache")
            epgCacheField.isAccessible = true
            val epgCache = epgCacheField.get(IPTVRepository) as java.util.concurrent.ConcurrentHashMap<*, *>
            epgCache.clear()

            // Clear internal cached fields of IPTVRepository to prevent test leakage
            val cachedDaoField = IPTVRepository::class.java.getDeclaredField("_cachedDao")
            cachedDaoField.isAccessible = true
            cachedDaoField.set(IPTVRepository, null)

            val healthMapField = IPTVRepository::class.java.getDeclaredField("sourceHealthMap")
            healthMapField.isAccessible = true
            healthMapField.set(IPTVRepository, emptyMap<Any, Any>())

            val matchesField = IPTVRepository::class.java.getDeclaredField("matches")
            matchesField.isAccessible = true
            matchesField.set(IPTVRepository, emptyMap<Any, Any>())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Test
    fun `test repository massive mock data for syncPlaylist`() {
        runBlocking {
            // Create an M3U playlist with overlapping channels, unicode strings, huge groups
            val massiveGroup = "A".repeat(50_000)
            val m3u = """
                #EXTM3U
                #EXTINF:-1 tvg-id="id1" tvg-name="Channel 1" group-title="$massiveGroup",Channel 1
                http://example.com/1
                #EXTINF:-1 tvg-id="id1" tvg-name="Channel 1 Dup" group-title="Dup",Channel 1 Dup
                http://example.com/1
            """.trimIndent()
            
            IPTVRepository.addProvider("Chaos Provider", "http://fake.com")
            // Just sync playlist directly
            IPTVRepository.syncPlaylist("iptv-chaos", m3u.byteInputStream())
            
            // Assert sync state doesn't crash
            // Since we are not fully initializing the app, some flows might not emit correctly,
            // but we just want to ensure it completes without throwing unhandled exceptions.
        }
    }

    @Test
    fun `test repository massive xmltv tag`() {
        runBlocking {
            // Create an XMLTV feed with an extremely large missing </programme> 
            // to see if the reader gets an OOM or properly handles it
            val massiveDesc = "X".repeat(3 * 1024 * 1024)
            val xmltv = """
                <tv>
                    <channel id="ch1"><display-name>CH 1</display-name></channel>
                    <programme start="20260605100000" stop="20260605110000" channel="ch1">
                        <title>Huge Desc</title>
                        <desc>$massiveDesc</desc>
                    </programme>
                </tv>
            """.trimIndent()
            
            val provider = IPTVRepository.addProvider("Chaos Provider", "http://fake.com")
            val source = IPTVRepository.addEpgSource(provider.id, "epg-chaos-name", "http://fake.com/xmltv")
            IPTVRepository.syncEPG(source.id, xmltv.byteInputStream())
            // Again, verify no crash occurs
        }
    }

    @Test
    fun `BUG-21-003 getChannels does not block main thread and uses cache`() = runBlocking {
        // First getChannels call will use the initial FakeData fallback cache
        val initialChannels = IPTVRepository.getChannels()
        assertNotNull(initialChannels)

        // Sync a new playlist, which should trigger updateSortedChannelsCache in the background
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="id1" tvg-name="Channel 1",Channel 1
            http://example.com/1
        """.trimIndent()
        
        val p = IPTVRepository.addProvider("Test Cache", "http://fake.com")
        IPTVRepository.syncPlaylist(p.id, m3u.byteInputStream())
        
        // Wait for Dispatchers.IO to finish caching using waitUntil
        waitUntil { IPTVRepository.getChannels().any { it.name == "Channel 1" } }
        
        val updatedChannels = IPTVRepository.getChannels()
        // If sorting was updated properly, it should reflect the newly parsed channels without blocking
        val hasNewChannel = updatedChannels.any { it.name == "Channel 1" }
        assertTrue("getChannels cache was not updated!", hasNewChannel)
    }

    @Test
    fun `BUG-21-004 Provider health update does not query all providers`() = runBlocking {
        val p = IPTVRepository.addProvider("Health Test Provider", "http://fake.com")
        // Create a channel explicitly associated with this provider
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="h_id1" tvg-name="Health Channel 1",Health Channel 1
            http://example.com/h1
        """.trimIndent()
        IPTVRepository.syncPlaylist(p.id, m3u.byteInputStream())
        
        // Wait for parsedChannels to update using waitUntil
        waitUntil { IPTVRepository.getChannels().any { it.name == "Health Channel 1" } }
        
        // Find the inserted channel to get its ID
        val ch = IPTVRepository.getChannels().find { it.name == "Health Channel 1" }
        assertNotNull(ch)
        
        // Trigger health failure which hits updateProviderHealthInDb
        IPTVRepository.recordPlaybackFailure(ch!!.id, "TIMEOUT")
        
        // Ensure that provider health score actually updated using waitUntil
        waitUntil { com.example.calmsource.core.database.SourceHealthRepository.getProviderHealth(p.id) != null }
        
        val providerHealth = com.example.calmsource.core.database.SourceHealthRepository.getProviderHealth(p.id)
        assertNotNull(providerHealth)
        assertEquals(1, providerHealth?.failureCount)
    }
}
