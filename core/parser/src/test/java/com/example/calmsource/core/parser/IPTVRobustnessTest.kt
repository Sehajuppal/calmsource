package com.example.calmsource.core.parser

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Robustness tests for M3U and XMLTV parsers using sanitized fixtures.
 * Addresses constraints: malformed inputs, empty playlists, duplicate channels,
 * and missing tags.
 */
class IPTVRobustnessTest {

    // --- Sanitized Fixtures ---

    private val malformedM3u = """
        <html>
        <head><title>Not an M3U</title></head>
        <body>This is completely malformed and not an M3U file.</body>
        </html>
    """.trimIndent()

    private val malformedXmltv = """
        {
            "status": "error",
            "message": "This is a JSON response, not XMLTV"
        }
    """.trimIndent()

    private val emptyM3uPlaylist = """
        #EXTM3U
        
        # End of playlist
    """.trimIndent()

    private val duplicateChannelsM3u = """
        #EXTM3U
        #EXTINF:-1 tvg-id="cnn" tvg-name="CNN" tvg-logo="http://example.com/logo.png",CNN News
        http://stream.example.com/cnn1
        #EXTINF:-1 tvg-id="cnn" tvg-name="CNN" tvg-logo="http://example.com/logo.png",CNN News
        http://stream.example.com/cnn1
        #EXTINF:-1,CNN News Duplicate URL 2
        http://stream.example.com/cnn2
        #EXTINF:-1,CNN News Duplicate URL 2
        http://stream.example.com/cnn2
    """.trimIndent()

    private val missingTvgTagsM3u = """
        #EXTM3U
        #EXTINF:-1,Just A Channel
        http://stream.example.com/just-a-channel
    """.trimIndent()

    // --- Tests ---

    @Test
    fun `M3U parser handles completely malformed input gracefully`() = kotlinx.coroutines.runBlocking {
        val result = M3UParser.parse(ByteArrayInputStream(malformedM3u.toByteArray()), "test")
        assertFalse("Parsing completely malformed M3U should fail", result.isSuccess)
        assertEquals(0, result.channelCount)
        assertTrue(result.stats.malformedEntries > 0)
    }

    @Test
    fun `XMLTV parser handles completely malformed input gracefully`() = kotlinx.coroutines.runBlocking {
        val result = XMLTVParser.parse(ByteArrayInputStream(malformedXmltv.toByteArray()))
        assertFalse("Parsing completely malformed XMLTV should fail", result.isSuccess)
        assertTrue(result.programs.isEmpty())
    }

    @Test
    fun `M3U parser handles empty playlist`() = kotlinx.coroutines.runBlocking {
        val result = M3UParser.parse(ByteArrayInputStream(emptyM3uPlaylist.toByteArray()), "test")
        assertFalse("Parsing empty playlist should report failure (no channels)", result.isSuccess)
        assertEquals(0, result.channelCount)
    }

    @Test
    fun `M3U parser skips duplicate channel stream URLs`() = kotlinx.coroutines.runBlocking {
        val channels = mutableListOf<com.example.calmsource.core.model.IPTVChannel>()
        val result = M3UParser.parse(ByteArrayInputStream(duplicateChannelsM3u.toByteArray()), "test") { chunk ->
            channels.addAll(chunk)
        }
        assertTrue("Should successfully parse the non-duplicate channels", result.isSuccess)
        assertEquals(2, result.channelCount)
        assertEquals(2, result.stats.duplicateChannels)
        assertEquals(2, result.stats.parsedChannels)
        assertEquals(2, channels.size)
        
        // We expect cnn1 and cnn2 to be there, but only once each.
        val urls = channels.map { it.streamUrl }.toSet()
        assertTrue(urls.contains("http://stream.example.com/cnn1"))
        assertTrue(urls.contains("http://stream.example.com/cnn2"))
        
        // Verify a warning was added
        assertTrue(result.warnings.any { it.contains("duplicate") })
    }

    @Test
    fun `M3U parser handles missing tvg tags correctly`() = kotlinx.coroutines.runBlocking {
        val channels = mutableListOf<com.example.calmsource.core.model.IPTVChannel>()
        val result = M3UParser.parse(ByteArrayInputStream(missingTvgTagsM3u.toByteArray()), "test") { chunk ->
            channels.addAll(chunk)
        }
        assertTrue("Should parse channel with no tvg tags", result.isSuccess)
        assertEquals(1, channels.size)
        
        val channel = channels.first()
        assertEquals("Just A Channel", channel.name)
        assertNull(channel.tvgId)
        assertNull(channel.tvgName)
        assertNull(channel.tvgLogo)
        assertEquals("http://stream.example.com/just-a-channel", channel.streamUrl)
    }
}
