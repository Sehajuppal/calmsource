package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.generateSafeSourceId
import com.example.calmsource.core.model.ProviderSyncStatus
import com.example.calmsource.core.database.SourceHealthRepository
import com.example.calmsource.core.database.repository.UserPreferencesRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import java.io.InputStream
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IPTVPlaybackRequestRegressionTest {
    private val xmlTvFormatter = DateTimeFormatter
        .ofPattern("yyyyMMddHHmmss xx")
        .withZone(ZoneOffset.UTC)

    private fun xmlTvTime(offsetMs: Long): String =
        xmlTvFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis() + offsetMs))

    @Before
    fun setup() {
        resetRepositoryState()
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
    fun `production channel repository never seeds fake provider channels`() {
        assertFalse(IPTVRepository.getChannels().any { it.providerId == "fake-provider" })
    }

    @Test
    fun `live playback request preserves channel url and live metadata`() {
        val channel = IPTVChannel(
            id = "xtream-live-42",
            tvgId = "live42",
            tvgName = "Live 42",
            tvgLogo = "https://example.com/logo.png",
            groupTitle = "News",
            name = "Live Channel 42",
            streamUrl = "xtream://stream_id/42",
            providerId = "provider-42"
        )

        val request = IPTVRepository.buildLivePlaybackRequest(
            channel = channel,
            programTitle = "Morning Live",
            programDescription = "Daily news",
            programDurationMs = 3_600_000L
        )

        assertEquals("xtream-live-42", request.source.id)
        assertEquals(PlaybackSourceType.IPTV, request.source.type)
        assertEquals("xtream://stream_id/42", request.source.rawUrl)
        assertEquals("Live Channel 42", request.source.title)
        assertEquals("Morning Live", request.source.metadata?.title)
        assertEquals("Daily news", request.source.metadata?.description)
        assertEquals(3_600_000L, request.source.metadata?.durationMs)
        assertTrue(request.source.metadata?.isLive == true)
        assertEquals("ts", request.source.metadata?.containerFormat)
        assertTrue(request.source.allowInsecureHttp)
        assertEquals(channel.safeSourceId, request.source.stableSourceId)
        assertEquals(channel.safeSourceId, request.source.safeSourceId)
    }

    @Test
    fun `xtream live playback exposes m3u8 fallback candidate`() {
        val channel = IPTVChannel(
            id = "xtream-live-99",
            tvgId = "live99",
            tvgName = "Live 99",
            tvgLogo = null,
            groupTitle = "Sports",
            name = "Sports Live",
            streamUrl = "xtream://stream_id/provider-99/99",
            providerId = "provider-99"
        )

        val fallbacks = IPTVRepository.buildLivePlaybackFallbackSources(channel)

        assertEquals(1, fallbacks.size)
        assertEquals("xtream-live-99-alt-m3u8", fallbacks[0].id)
        assertEquals("m3u8", fallbacks[0].metadata?.containerFormat)
        assertTrue(fallbacks[0].metadata?.isLive == true)
        assertEquals(channel.safeSourceId, fallbacks[0].stableSourceId)
    }

    @Test
    fun `details origin iptv playback source keeps pseudo url stable source id`() {
        val pseudoUrl = "xtream://stream_id/42"
        val stableId = generateSafeSourceId(pseudoUrl)
        val source = com.example.calmsource.core.model.PlaybackSource(
            id = "details-stream-42",
            type = PlaybackSourceType.IPTV,
            title = "Movie Stream",
            rawUrl = pseudoUrl,
            stableSourceId = stableId
        )
        assertEquals(stableId, source.safeSourceId)
        assertEquals(pseudoUrl, source.rawUrl)
    }

    @Test
    fun `vod channels do not expose live format fallbacks`() {
        val channel = IPTVChannel(
            id = "vod-1",
            tvgId = null,
            tvgName = null,
            tvgLogo = null,
            groupTitle = "Movies",
            name = "Movie",
            streamUrl = "xtream://stream_id/provider-1/1",
            providerId = "provider-1",
            rawAttributes = mapOf("xtream_content_type" to "vod")
        )

        assertTrue(IPTVRepository.buildLivePlaybackFallbackSources(channel).isEmpty())
    }

    @Test
    fun `plain M3U live playback requests do not bypass cleartext preference`() {
        val channel = IPTVChannel(
            id = "m3u-live-1",
            tvgId = "m3u1",
            tvgName = "M3U 1",
            tvgLogo = null,
            groupTitle = "News",
            name = "Plain M3U Channel",
            streamUrl = "http://example.com/live/1.ts",
            providerId = "m3u-provider"
        )

        val request = IPTVRepository.buildLivePlaybackRequest(channel)

        assertTrue(request.source.allowInsecureHttp)
    }

    @Test
    fun `missing playlist provider resolves to sync error`() = runBlocking {
        val missingId = "iptv-missing-regression"

        IPTVRepository.syncPlaylistFromUrl(missingId)

        val state = IPTVRepository.syncStates.value[missingId]
        assertEquals(ProviderSyncStatus.ERROR, state?.status)
        assertEquals(100, state?.progressPercent)
    }

    @Test
    fun `playlist parser exceptions resolve sync state instead of spinning`() = runBlocking {
        val provider = IPTVRepository.addProvider("Broken Playlist", "https://example.com/broken.m3u")
        val brokenStream = object : InputStream() {
            override fun read(): Int {
                throw IOException("broken playlist stream")
            }
        }

        IPTVRepository.syncPlaylist(provider.id, brokenStream)

        val state = IPTVRepository.syncStates.value[provider.id]
        assertEquals(ProviderSyncStatus.ERROR, state?.status)
        assertEquals(100, state?.progressPercent)
    }

    @Test
    fun `live channel category hiding creates health rows and removes matching channels`() = runBlocking {
        SourceHealthRepository.clearSourceHealth()
        val provider = IPTVRepository.addProvider("Hide Regression", "https://example.com/hide.m3u")
        val unrelatedVodSourceId = "unrelated-hidden-vod"
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="hide-india-1" tvg-name="Hide India 1" group-title="Hide India",Hide India 1
            https://example.com/live/hide-india-1.m3u8
            #EXTINF:-1 tvg-id="hide-us-1" tvg-name="Hide US 1" group-title="Hide US",Hide US 1
            https://example.com/live/hide-us-1.m3u8
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3u.byteInputStream())
        delay(1000)

        assertTrue(IPTVRepository.getLiveChannels().any { it.groupTitle == "Hide India" })
        assertTrue(IPTVRepository.getLiveChannels().any { it.groupTitle == "Hide US" })

        IPTVRepository.setLiveChannelGroupHidden("Hide India", hidden = true)
        IPTVRepository.refreshHealthCacheForTest()

        assertFalse(IPTVRepository.getLiveChannels().any { it.groupTitle == "Hide India" })
        assertTrue(IPTVRepository.getLiveChannels().any { it.groupTitle == "Hide US" })

        SourceHealthRepository.setSourceHidden(
            sourceId = unrelatedVodSourceId,
            providerId = provider.id,
            sourceType = PlaybackSourceType.IPTV,
            hidden = true
        )

        IPTVRepository.restoreHiddenIptvChannels()
        IPTVRepository.refreshHealthCacheForTest()

        assertTrue(IPTVRepository.getLiveChannels().any { it.groupTitle == "Hide India" })
        assertTrue(SourceHealthRepository.getSourceHealth(unrelatedVodSourceId)?.userHidden == true)

        SourceHealthRepository.clearSourceHealth()
        IPTVRepository.refreshHealthCacheForTest()
    }

    @Test
    fun `live channel helpers filter vod and handle missing ids safely`() = runBlocking {
        val provider = IPTVRepository.addProvider("Helper Regression", "https://example.com/helper.m3u")
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="helper-live" tvg-name="Helper Live" group-title="News",Helper Live
            https://example.com/live/helper.m3u8
            #EXTINF:-1 tvg-id="helper-movie" tvg-name="Helper Movie" group-title="Movies",Helper Movie
            https://example.com/movie/helper.mp4
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3u.byteInputStream())
        delay(1000)

        val allChannels = IPTVRepository.getChannels()
        val helperLive = allChannels.firstOrNull { it.name == "Helper Live" }
        val helperMovie = allChannels.firstOrNull { it.name == "Helper Movie" }

        assertNotNull(helperLive)
        assertNotNull(helperMovie)
        assertFalse(helperLive!!.isVod)
        assertTrue(helperMovie!!.isVod)
        assertEquals(helperLive, IPTVRepository.findChannel(helperLive.id))
        assertNull(IPTVRepository.findChannel(""))
        assertFalse(IPTVRepository.getLiveChannels().any { it.id == helperMovie.id })
        assertTrue(IPTVRepository.getLiveChannels(limit = 1).size <= 1)
    }

    @Test
    fun `separate iptv categories by provider toggle dynamic prefixing`() = runBlocking {
        val provider = IPTVRepository.addProvider("Provider A", "https://example.com/provA.m3u")
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" tvg-name="Channel 1" group-title="Sports",Channel 1
            https://example.com/ch1.m3u8
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3u.byteInputStream())
        delay(1000)

        // By default, category separation is false, should not prefix
        UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = false) }
        var limit = 0
        while (UserPreferencesRepository.preferences.value.separateIptvCategoriesByProvider && limit < 100) {
            delay(10)
            limit++
        }
        var channels = IPTVRepository.getChannels()
        var ch = channels.firstOrNull { it.name == "Channel 1" }
        assertNotNull(ch)
        assertEquals("Sports", ch!!.groupTitle)

        // Enable category separation, should prefix with provider name
        UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = true) }
        limit = 0
        while (!UserPreferencesRepository.preferences.value.separateIptvCategoriesByProvider && limit < 100) {
            delay(10)
            limit++
        }
        channels = IPTVRepository.getChannels()
        ch = channels.firstOrNull { it.name == "Channel 1" }
        assertNotNull(ch)
        assertEquals("Provider A - Sports", ch!!.groupTitle)

        // Reset preference
        UserPreferencesRepository.updatePreferences { it.copy(separateIptvCategoriesByProvider = false) }
        limit = 0
        while (UserPreferencesRepository.preferences.value.separateIptvCategoriesByProvider && limit < 100) {
            delay(10)
            limit++
        }
    }

    @Test
    fun `delete provider deletes channels and provider details`() = runBlocking {
        val provider = IPTVRepository.addProvider("Provider To Delete", "https://example.com/delete.m3u")
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="del1" tvg-name="Channel Del" group-title="General",Channel Del
            https://example.com/del1.m3u8
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3u.byteInputStream())
        delay(1000)

        val epgSource = IPTVRepository.addEpgSource(
            providerId = provider.id,
            name = "Delete Provider EPG",
            url = "https://example.com/delete.xml"
        )
        val programStart = xmlTvTime(60 * 60 * 1000L)
        val programEnd = xmlTvTime(2 * 60 * 60 * 1000L)
        val xmltv = """
            <tv>
              <programme start="$programStart" stop="$programEnd" channel="del1">
                <title>Program To Delete</title>
              </programme>
            </tv>
        """.trimIndent()
        IPTVRepository.syncEPG(epgSource.id, xmltv.byteInputStream())

        // Verify provider and channels exist
        assertNotNull(IPTVRepository.providers.value.firstOrNull { it.id == provider.id })
        assertTrue(IPTVRepository.getChannels().any { it.providerId == provider.id })
        assertTrue(IPTVRepository.getPrograms().any { it.channelId == "del1" })

        // Delete the provider
        IPTVRepository.deleteProvider(provider.id)
        delay(1000)

        // Verify provider and channels are deleted
        assertNull(IPTVRepository.providers.value.firstOrNull { it.id == provider.id })
        assertFalse(IPTVRepository.getChannels().any { it.providerId == provider.id })
        assertFalse(IPTVRepository.getPrograms().any { it.channelId == "del1" })
    }
}
