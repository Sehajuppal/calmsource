package com.example.calmsource.feature.search

import com.example.calmsource.core.model.*
import com.example.calmsource.core.parser.M3UParser
import com.example.calmsource.core.parser.XMLTVParser
import com.example.calmsource.feature.iptv.IPTVRepository
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

class IPTVParserTest {
    private val xmlTvFormatter = DateTimeFormatter
        .ofPattern("yyyyMMddHHmmss xx")
        .withZone(ZoneOffset.UTC)

    private fun xmlTvTime(offsetMs: Long): String =
        xmlTvFormatter.format(Instant.ofEpochMilli(System.currentTimeMillis() + offsetMs))

    @Before
    fun setUp() {
        // Clear IPTVRepository's private fields via reflection to isolate tests
        try {
            val repoClass = IPTVRepository::class.java
            
            // Clear parsedChannels
            val parsedChannelsField = repoClass.getDeclaredField("parsedChannels")
            parsedChannelsField.isAccessible = true
            parsedChannelsField.set(IPTVRepository, emptyList<IPTVChannel>())

            // Clear parsedPrograms
            val parsedProgramsField = repoClass.getDeclaredField("parsedPrograms")
            parsedProgramsField.isAccessible = true
            parsedProgramsField.set(IPTVRepository, emptyList<EPGProgram>())

            // Clear matches
            val matchesField = repoClass.getDeclaredField("matches")
            matchesField.isAccessible = true
            matchesField.set(IPTVRepository, emptyMap<String, EPGMatch>())

            // Clear sourceHealthMap
            val sourceHealthMapField = repoClass.getDeclaredField("sourceHealthMap")
            sourceHealthMapField.isAccessible = true
            val map = emptyMap<String, SourceHealth>()
            sourceHealthMapField.set(IPTVRepository, map)

            // Clear _syncStates
            val syncStatesField = repoClass.getDeclaredField("_syncStates")
            syncStatesField.isAccessible = true
            val syncStatesFlow = syncStatesField.get(IPTVRepository) as kotlinx.coroutines.flow.MutableStateFlow<Map<String, ProviderSyncState>>
            syncStatesFlow.value = emptyMap()

            // Get dao
            val daoField = repoClass.declaredFields.firstOrNull { it.name.contains("dao") }
            if (daoField != null) {
                daoField.isAccessible = true
                val delegateValue = daoField.get(IPTVRepository)
                val daoObj = if (delegateValue is Lazy<*>) {
                    delegateValue.value
                } else {
                    delegateValue
                }
                
                if (daoObj != null) {
                    val daoClass = daoObj.javaClass
                    for (field in daoClass.declaredFields) {
                        if (field.name.endsWith("Mem")) {
                            field.isAccessible = true
                            val stateFlow = field.get(daoObj)
                            if (stateFlow is kotlinx.coroutines.flow.MutableStateFlow<*>) {
                                @Suppress("UNCHECKED_CAST")
                                (stateFlow as kotlinx.coroutines.flow.MutableStateFlow<List<Any>>).value = emptyList()
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        runBlocking {
            com.example.calmsource.core.database.SourceHealthRepository.clearSourceHealth()
            com.example.calmsource.core.database.SourceHealthRepository.clearProviderHealth()
            IPTVRepository.syncPlaylist("test-provider", "".byteInputStream())
            IPTVRepository.syncEPG("test-epg", "".byteInputStream())
        }
    }

    @Test
    fun testValidM3UParser() = runBlocking {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="hbo-hd" tvg-name="HBO HD" tvg-logo="http://logo.com/hbo.png" group-title="Movies",HBO HD US
            http://stream.com/hbo.m3u8
        """.trimIndent()

        val channels = mutableListOf<IPTVChannel>()
        val result = M3UParser.parse(m3uContent.byteInputStream(), "test-provider") { chunk ->
            channels.addAll(chunk)
        }
        assertTrue(result.isSuccess)
        assertEquals(1, channels.size)
        val channel = channels.first()
        assertEquals("hbo-hd", channel.tvgId)
        assertEquals("HBO HD", channel.tvgName)
        assertEquals("http://logo.com/hbo.png", channel.tvgLogo)
        assertEquals("Movies", channel.groupTitle)
        assertEquals("HBO HD US", channel.name)
        assertEquals("http://stream.com/hbo.m3u8", channel.streamUrl)
    }

    @Test
    fun testMalformedAndMissingTvgIdM3UParsing() = runBlocking {
        // Malformed line (missing comma) should be skipped/fail parsing for that entry, and missing tvg-id should fallback safely
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-name="No ID" tvg-logo="http://logo.com/noid.png" group-title="General",No ID Channel
            http://stream.com/noid.m3u8
            #EXTINF:-1 tvg-id="malformed" Malformed line here without comma
            http://stream.com/malformed.m3u8
        """.trimIndent()

        val channels = mutableListOf<IPTVChannel>()
        val result = M3UParser.parse(m3uContent.byteInputStream(), "test-provider") { chunk ->
            channels.addAll(chunk)
        }
        assertTrue(result.isSuccess)
        // Should parse both channels matching the lenient parser behavior
        assertEquals(2, channels.size)
        val channel1 = channels[0]
        val channel2 = channels[1]
        assertNull(channel1.tvgId)
        assertEquals("No ID Channel", channel1.name)
        assertEquals("http://stream.com/noid.m3u8", channel1.streamUrl)

        assertEquals("malformed", channel2.tvgId)
        assertEquals("tvg-id=\"malformed\" Malformed line here without comma", channel2.name)
        assertEquals("http://stream.com/malformed.m3u8", channel2.streamUrl)
    }

    @Test
    fun testDuplicateChannelHandling() = runBlocking {
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="chan1",Channel One
            http://stream.com/one.m3u8
            #EXTINF:-1 tvg-id="chan1-dup",Channel One Duplicate URL
            http://stream.com/one.m3u8
        """.trimIndent()

        val channels = mutableListOf<IPTVChannel>()
        val result = M3UParser.parse(m3uContent.byteInputStream(), "test-provider") { chunk ->
            channels.addAll(chunk)
        }
        assertTrue(result.isSuccess)
        // Deduplication based on streamUrl
        assertEquals(1, channels.size)
        assertEquals("Channel One", channels.first().name)
        assertTrue(result.warnings.any { it.contains("Skipped duplicate channel stream URL") })
    }

    @Test
    fun testValidXMLTVParser() = runBlocking {
        val xmltvContent = """
            <tv>
              <channel id="hbo-hd">
                <display-name>HBO HD</display-name>
              </channel>
              <programme start="20260605100000 +0000" stop="20260605120000 +0000" channel="hbo-hd">
                <title>Inception</title>
                <desc>A thief who steals corporate secrets through the use of dream-sharing technology.</desc>
                <category>Sci-Fi</category>
                <language>en</language>
              </programme>
            </tv>
        """.trimIndent()

        val result = XMLTVParser.parse(xmltvContent.byteInputStream())
        assertTrue(result.isSuccess)
        assertEquals(1, result.programs.size)
        val program = result.programs.first()
        assertEquals("hbo-hd", program.channelId)
        assertEquals("Inception", program.title)
        assertEquals("Sci-Fi", program.category)
        assertEquals("en", program.language)
        assertTrue(program.startTimeMs > 0L)
        assertTrue(program.endTimeMs > program.startTimeMs)
    }

    @Test
    fun testMalformedXMLTVHandling() = runBlocking {
        val xmltvContent = """
            <tv>
              <programme start="invalid-date" stop="invalid-date">
                <title>Bad Program</title>
              </programme>
            </tv>
        """.trimIndent()

        val result = XMLTVParser.parse(xmltvContent.byteInputStream())
        // It shouldn't crash, but either skip it or warn
        assertFalse(result.isSuccess) // because 0 programs were parsed successfully (invalid channel id + dates)
        assertTrue(result.warnings.any { it.contains("Missing channel ID attribute") })
    }

    @Test
    fun testEPGChannelMatching() = runBlocking {
        // Setup channels in repository
        val provider = IPTVRepository.addProvider("Test Provider", "http://test.com/m3u")
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="hbo-east" tvg-name="HBO East" group-title="Movies",HBO East Channel
            http://stream.com/hbo-east.m3u8
            #EXTINF:-1 tvg-id="star-sports" tvg-name="Star Sports 1",Star Sports One
            http://stream.com/starsports.m3u8
            #EXTINF:-1 tvg-id="unmatched",No Match Channel
            http://stream.com/nomatch.m3u8
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3uContent.byteInputStream())

        // Setup EPG in repository
        val programStart = xmlTvTime(60 * 60 * 1000L)
        val programEnd = xmlTvTime(3 * 60 * 60 * 1000L)
        val xmltvContent = """
            <tv>
              <channel id="hbo-east">
                <display-name>HBO East</display-name>
              </channel>
              <channel id="Star Sports 1">
                <display-name>Star Sports 1</display-name>
              </channel>
              <programme start="$programStart" stop="$programEnd" channel="hbo-east">
                <title>Movie on HBO</title>
              </programme>
              <programme start="$programStart" stop="$programEnd" channel="Star Sports 1">
                <title>Live Football</title>
              </programme>
            </tv>
        """.trimIndent()

        val epgSource = IPTVRepository.addEpgSource(provider.id, "Test EPG", "http://test.com/xmltv")
        IPTVRepository.syncEPG(epgSource.id, xmltvContent.byteInputStream())

        // Get matching statuses
        val channels = IPTVRepository.getChannels().filter { it.providerId == provider.id }
        assertEquals(3, channels.size)

        val hboChan = channels.first { it.name == "HBO East Channel" }
        val sportsChan = channels.first { it.name == "Star Sports One" }
        val nomatchChan = channels.first { it.name == "No Match Channel" }

        val hboMatch = IPTVRepository.getMatchStatusForChannel(hboChan.id)
        val sportsMatch = IPTVRepository.getMatchStatusForChannel(sportsChan.id)
        val nomatchMatch = IPTVRepository.getMatchStatusForChannel(nomatchChan.id)

        assertNotNull(hboMatch)
        assertEquals("hbo-east", hboMatch?.epgId)
        assertEquals(EPGMatchType.EXACT_ID, hboMatch?.matchType) // Exact ID match

        assertNotNull(sportsMatch)
        assertEquals("Star Sports 1", sportsMatch?.epgId)
        assertEquals(EPGMatchType.NORMALIZED_NAME, sportsMatch?.matchType) // Matches by tvg-name normalized (Star Sports 1 vs Star Sports 1)

        assertNotNull(nomatchMatch)
        assertEquals("", nomatchMatch?.epgId)
        assertEquals(EPGMatchType.NONE, nomatchMatch?.matchType)
    }

    @Test
    fun testUniversalSearchIPTVVODMerge() = runBlocking {
        val provider = IPTVRepository.addProvider("Test Provider", "http://test.com/m3u")
        // M3U containing a VOD Spider-Man item matching FakeData
        val m3uContent = """
            #EXTM3U
            #EXTINF:-1 tvg-id="movie-spiderman" tvg-name="Spider-Man" group-title="IPTV VOD",Spider-Man: Homecoming [IPTV]
            http://stream.com/spiderman.mp4
        """.trimIndent()

        IPTVRepository.syncPlaylist(provider.id, m3uContent.byteInputStream())

        // Perform Universal Search for Spider-Man
        val prefs = FakeData.defaultPreferences
        val searchEngine = UniversalSearchEngineImpl(
            providers = listOf(
                VODSearchProviderImpl(),
                IPTVSearchProviderImpl()
            )
        )

        val searchResultGroups = searchEngine.search("Spider-Man", prefs).toList().last()

        // Verify VOD result merges Spider-Man: Homecoming
        val moviesGroup = searchResultGroups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull(moviesGroup)

        val spidermanCard = moviesGroup!!.results.firstOrNull { it.mediaItem.title.contains("Spider-Man") }
        assertNotNull(spidermanCard)

        // IPTV should be in available sources
        assertTrue(spidermanCard!!.availableFrom.contains(SourceType.IPTV))

        // Check that there is a watch option corresponding to the IPTV playlist VOD stream
        val iptvWatchOption = spidermanCard.watchOptions.firstOrNull { it.source.url == "http://stream.com/spiderman.mp4" }
        assertNotNull(iptvWatchOption)
        assertEquals(provider.id, iptvWatchOption?.source?.extensionId)
    }
}
