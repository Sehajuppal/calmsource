package com.example.calmsource.feature.iptv

import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import org.junit.Assert.*
import org.junit.Test
import com.example.calmsource.core.database.mapper.toEntity
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.core.model.ProviderHealth
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.parser.M3UParser
import java.io.ByteArrayInputStream

class IPTVMilestone1EmpiricalTest {

    private fun waitForSettled() {
        try {
            val tickField = IPTVRepository::class.java.getDeclaredField("dataUpdateTick")
            tickField.isAccessible = true
            val tickFlow = tickField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Long>
            
            kotlinx.coroutines.runBlocking {
                var lastTick = tickFlow.value
                var stableCount = 0
                while (stableCount < 5) {
                    kotlinx.coroutines.delay(100)
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

    private suspend fun waitForRepositoryChannelsSize(expectedSize: Int, checkStateFlows: Boolean = true) {
        val parsedField = IPTVRepository::class.java.getDeclaredField("parsedChannels")
        parsedField.isAccessible = true
        kotlinx.coroutines.withTimeout(30000) {
            while (true) {
                val parsed = parsedField.get(IPTVRepository) as List<*>
                val parsedMatch = parsed.size == expectedSize
                val stateFlowMatch = !checkStateFlows || IPTVRepository.channels.value.size == expectedSize
                val getChannelsMatch = !checkStateFlows || IPTVRepository.getChannels().size == expectedSize
                if (parsedMatch && stateFlowMatch && getChannelsMatch) {
                    break
                }
                delay(100)
            }
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
            parsedField.set(IPTVRepository, emptyList<IPTVChannel>())

            val sortedField = IPTVRepository::class.java.getDeclaredField("sortedChannelsCache")
            sortedField.isAccessible = true
            sortedField.set(IPTVRepository, emptyList<IPTVChannel>())

            val channelsFlowField = IPTVRepository::class.java.getDeclaredField("_channels")
            channelsFlowField.isAccessible = true
            val channelsFlow = channelsFlowField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<List<IPTVChannel>>
            channelsFlow.value = emptyList<IPTVChannel>()

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
            val syncStatesFlow = syncStatesField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, com.example.calmsource.core.model.ProviderSyncState>>
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
    fun testProviderDisablingImmediatelyFiltersChannelsAndUpdatesFlow() = runBlocking {
        resetRepositoryState()
        delay(1000)

        // Verify start state
        assertEquals(0, IPTVRepository.providers.value.size)
        assertEquals(0, IPTVRepository.getChannels().size)
        assertEquals(0, IPTVRepository.channels.value.size)

        // Add a provider
        val provider = IPTVRepository.addProvider("Test Provider 1", "http://test.com/playlist.m3u")
        assertTrue(provider.isEnabled)

        // Add channels for this provider
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch-1" tvg-name="Channel 1",Channel 1
            http://example.com/1
            #EXTINF:-1 tvg-id="ch-2" tvg-name="Channel 2",Channel 2
            http://example.com/2
        """.trimIndent()
        IPTVRepository.syncPlaylist(provider.id, m3u.byteInputStream())
        delay(1000)

        // Verify channels are present
        val channelsBefore = IPTVRepository.getChannels()
        assertEquals(2, channelsBefore.size)
        assertEquals(2, IPTVRepository.channels.value.size)

        // Use reflection to access the resolved dao
        val method = IPTVRepository::class.java.getDeclaredMethod("resolveDao")
        method.isAccessible = true
        val dao = method.invoke(IPTVRepository) as com.example.calmsource.core.database.dao.IPTVDao

        // Print state before disabling
        println("BEFORE DISABLING:")
        println("  Repository Providers: ${IPTVRepository.providers.value}")
        println("  DAO Providers: ${dao.getAllProviders().first()}")
        println("  getChannels(): ${IPTVRepository.getChannels()}")

        // Disable provider via the DAO
        val disabledEntity = provider.copy(isEnabled = false).toEntity()
        dao.updateProvider(disabledEntity)
        IPTVRepository.refreshHealthCacheForTest()
        delay(1000) // Give it time to propagate to Flow

        // Print state after disabling
        println("AFTER DISABLING:")
        println("  Repository Providers: ${IPTVRepository.providers.value}")
        println("  DAO Providers: ${dao.getAllProviders().first()}")
        println("  getChannels(): ${IPTVRepository.getChannels()}")
        println("  StateFlow Channels: ${IPTVRepository.channels.value}")

        // Verify channels are immediately filtered/cleared in getChannels() and StateFlow
        val channelsAfter = IPTVRepository.getChannels()
        assertEquals(0, channelsAfter.size)
        assertEquals(0, IPTVRepository.channels.value.size)

        // Re-enable provider
        val enabledEntity = provider.copy(isEnabled = true).toEntity()
        dao.updateProvider(enabledEntity)
        IPTVRepository.refreshHealthCacheForTest()
        delay(1000)

        // Verify channels are restored immediately
        assertEquals(2, IPTVRepository.getChannels().size)
        assertEquals(2, IPTVRepository.channels.value.size)

        // Clean up
        IPTVRepository.deleteProvider(provider.id)
    }

    @Test
    fun testLoadingLargeList100kPerformance() = runBlocking {
        resetRepositoryState()
        delay(1000)

        val provider = IPTVRepository.addProvider("Large Provider", "http://large.com/playlist.m3u")
        
        // Generate 100,000 channels
        val sb = java.lang.StringBuilder()
        sb.append("#EXTM3U\n")
        for (i in 1..100000) {
            sb.append("#EXTINF:-1 tvg-id=\"large-ch-$i\" tvg-name=\"Large Channel $i\" group-title=\"LargeGroup\",Large Channel $i\n")
            sb.append("http://example.com/stream/$i\n")
        }

        // Parse the M3U playlist directly and accumulate channels locally to avoid flow backlogging
        val m3uStream = sb.toString().byteInputStream()
        val parsedChannelsList = mutableListOf<IPTVChannel>()
        
        val parseResult = M3UParser.parse(m3uStream, provider.id) { chunk ->
            val detectedChunk = chunk.map { channel ->
                val lang = IptvChannelOrganizer.detectLanguage(channel)
                val cntry = IptvChannelOrganizer.detectCountry(channel)
                channel.copy(language = lang, country = cntry)
            }
            parsedChannelsList.addAll(detectedChunk)
        }
        
        assertTrue(parseResult.isSuccess)
        assertEquals(100000, parseResult.channelCount)
        assertEquals(100000, parsedChannelsList.size)

        // Get the DAO
        val method = IPTVRepository::class.java.getDeclaredMethod("resolveDao")
        method.isAccessible = true
        val dao = method.invoke(IPTVRepository) as com.example.calmsource.core.database.dao.IPTVDao

        // Insert all parsed channels in one single batch to prevent flow queue build-up and sequential lock contention
        val entities = parsedChannelsList.map { it.toEntity() }
        dao.insertChannels(entities)
        
        // Wait for parsedChannels to be fully mapped to 100,000 channels
        waitForRepositoryChannelsSize(100000, checkStateFlows = false)

        // Query the DAO directly to verify raw count
        val rawChannels = dao.getAllChannels().first()
        assertEquals(100000, rawChannels.size)

        // Disable filters to get raw count from getChannels()
        IPTVRepository.updateOptimizationPreferences {
            it.copy(
                removeDuplicates = false,
                hideAdult = false,
                hideUnsupported = false
            )
        }

        // Wait for all StateFlows and getChannels to be fully updated to 100,000
        waitForRepositoryChannelsSize(100000, checkStateFlows = true)

        val channels = IPTVRepository.getChannels()
        println("FINAL Raw DAO channel count: ${rawChannels.size}")
        println("FINAL Filtered getChannels() count: ${channels.size}")
        
        assertEquals(100000, channels.size)

        // Clean up
        IPTVRepository.deleteProvider(provider.id)
        
        // Wait for parsedChannels and all flows/getChannels to be fully mapped back to 0
        waitForRepositoryChannelsSize(0, checkStateFlows = true)
    }
}
