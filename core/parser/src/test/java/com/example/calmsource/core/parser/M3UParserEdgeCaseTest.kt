package com.example.calmsource.core.parser

import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

/**
 * Comprehensive edge-case tests for [M3UParser].
 *
 * Covers:
 * - Standard M3U parsing
 * - BOM markers (U+FEFF)
 * - Windows line endings (\r\n)
 * - Missing comma in #EXTINF
 * - Empty lines
 * - Duplicate stream URLs
 * - Missing group-title
 * - Empty/broken logo URLs
 * - tvg-language extraction via rawAttributes
 * - Resolution detection from channel names
 * - URL sanitization in warnings (no raw URLs logged)
 * - Streams with no #EXTINF
 */
class M3UParserEdgeCaseTest {

    private fun parse(content: String, providerId: String = "test-provider"): TestResult = kotlinx.coroutines.runBlocking {
        val channels = mutableListOf<IPTVChannel>()
        val result = M3UParser.parse(ByteArrayInputStream(content.toByteArray(Charsets.UTF_8)), providerId) { chunk ->
            channels.addAll(chunk)
        }
        TestResult(result.isSuccess, channels, result.warnings)
    }

    data class TestResult(
        val isSuccess: Boolean,
        val channels: List<IPTVChannel>,
        val warnings: List<String>
    )

    @Test
    fun `standard M3U with all attributes parses correctly`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CNN.us" tvg-name="CNN" tvg-logo="https://example.com/cnn.png" group-title="News" tvg-language="English",CNN International
            http://stream.example.com/cnn
            #EXTINF:-1 tvg-id="BBC.uk" tvg-name="BBC" tvg-logo="https://example.com/bbc.png" group-title="News" tvg-language="English",BBC World
            http://stream.example.com/bbc
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(2, result.channels.size)
        assertEquals("CNN International", result.channels[0].name)
        assertEquals("CNN.us", result.channels[0].tvgId)
        assertEquals("CNN", result.channels[0].tvgName)
        assertEquals("https://example.com/cnn.png", result.channels[0].tvgLogo)
        assertEquals("News", result.channels[0].groupTitle)
        assertEquals("English", result.channels[0].rawAttributes["tvg-language"])
        assertEquals("http://stream.example.com/cnn", result.channels[0].streamUrl)
    }

    @Test
    fun `BOM marker at start of file is handled`() {
        val bom = "\uFEFF"
        val m3u = "${bom}#EXTM3U\n#EXTINF:-1,Test Channel\nhttp://stream.example.com/test"

        val result = parse(m3u)
        assertTrue("Parser should handle BOM", result.isSuccess)
        assertEquals(1, result.channels.size)
        assertEquals("Test Channel", result.channels[0].name)
    }

    @Test
    fun `Windows line endings are handled`() {
        val m3u = "#EXTM3U\r\n#EXTINF:-1,Channel One\r\nhttp://stream.example.com/one\r\n#EXTINF:-1,Channel Two\r\nhttp://stream.example.com/two\r\n"

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(2, result.channels.size)
        assertEquals("Channel One", result.channels[0].name)
        assertEquals("Channel Two", result.channels[1].name)
    }

    @Test
    fun `missing comma in EXTINF is handled gracefully`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="TEST" tvg-name="Test Channel"
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        // Should still parse, using the tvg-name or a fallback as the channel name
        assertTrue("Missing comma should not crash parser", result.isSuccess)
        assertEquals(1, result.channels.size)
        // The name should be derived from the EXTINF line (after duration stripping)
        assertNotNull(result.channels[0].name)
        assertTrue(result.channels[0].name.isNotEmpty())
        // tvg-id and tvg-name should still be extracted
        assertEquals("TEST", result.channels[0].tvgId)
        assertEquals("Test Channel", result.channels[0].tvgName)
    }

    @Test
    fun `empty lines between entries are skipped`() {
        val m3u = """
            #EXTM3U
            
            #EXTINF:-1,Channel One
            
            http://stream.example.com/one
            
            #EXTINF:-1,Channel Two
            http://stream.example.com/two
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(2, result.channels.size)
    }

    @Test
    fun `duplicate stream URLs are detected and skipped`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Channel One
            http://stream.example.com/same
            #EXTINF:-1,Channel Two
            http://stream.example.com/same
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(1, result.channels.size)
        assertEquals("Channel One", result.channels[0].name)
        assertEquals(1, result.warnings.size)
        assertTrue(result.warnings[0].contains("duplicate"))
    }

    @Test
    fun `duplicate warning does not contain raw URL`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Channel One
            http://secret.example.com/stream?token=abc123
            #EXTINF:-1,Channel Two
            http://secret.example.com/stream?token=abc123
        """.trimIndent()

        val result = parse(m3u)
        val warning = result.warnings.firstOrNull() ?: ""
        assertFalse("Warning should not contain raw URL", warning.contains("secret.example.com"))
        assertFalse("Warning should not contain token", warning.contains("abc123"))
    }

    @Test
    fun `stream URL without EXTINF warning does not contain raw URL`() {
        val m3u = """
            #EXTM3U
            http://secret.example.com/stream?token=xyz
        """.trimIndent()

        val result = parse(m3u)
        assertFalse(result.isSuccess)
        val warning = result.warnings.firstOrNull() ?: ""
        assertTrue("Should warn about missing EXTINF", warning.contains("without preceding"))
        assertFalse("Warning should not contain raw URL", warning.contains("secret.example.com"))
        assertFalse("Warning should not contain token", warning.contains("xyz"))
    }

    @Test
    fun `title-like m3u8 text without a URL scheme is rejected`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Trailer Playlist Title
            WatchBigBuckBunny.m3u8
        """.trimIndent()

        val result = parse(m3u)

        assertFalse(result.isSuccess)
        assertTrue(result.channels.isEmpty())
        assertTrue(result.warnings.any { it.contains("invalid stream reference") })
    }

    @Test
    fun `missing group-title defaults to null`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="TEST",Test Channel
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertNull(result.channels[0].groupTitle)
    }

    @Test
    fun `empty logo URL is sanitized to null`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="",Channel With Empty Logo
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertNull("Empty logo should be null", result.channels[0].tvgLogo)
    }

    @Test
    fun `whitespace-only logo URL is sanitized to null`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-logo="   ",Channel With Blank Logo
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertNull("Blank logo should be null", result.channels[0].tvgLogo)
    }

    @Test
    fun `tvg-language is extracted into rawAttributes`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-language="Hindi",Hindi Channel
            http://stream.example.com/hindi
            #EXTINF:-1 tvg-language="English",English Channel
            http://stream.example.com/english
            #EXTINF:-1,No Language Channel
            http://stream.example.com/nolang
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(3, result.channels.size)
        assertEquals("Hindi", result.channels[0].rawAttributes["tvg-language"])
        assertEquals("English", result.channels[1].rawAttributes["tvg-language"])
        assertNull(result.channels[2].rawAttributes["tvg-language"])
    }

    @Test
    fun `channel name with HD, FHD, 4K markers is preserved`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Star Sports HD
            http://stream.example.com/star-hd
            #EXTINF:-1,Sony FHD
            http://stream.example.com/sony-fhd
            #EXTINF:-1,Discovery 4K
            http://stream.example.com/disc-4k
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals("Star Sports HD", result.channels[0].name)
        assertEquals("Sony FHD", result.channels[1].name)
        assertEquals("Discovery 4K", result.channels[2].name)
    }

    @Test
    fun `empty channel name falls back to tvg-name`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Fallback Name",
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals("Fallback Name", result.channels[0].name)
    }

    @Test
    fun `empty channel name without tvg-name falls back to Channel`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals("Channel", result.channels[0].name)
    }

    @Test
    fun `empty input returns failure`() {
        val result = parse("")
        assertFalse(result.isSuccess)
        assertTrue(result.channels.isEmpty())
    }

    @Test
    fun `only header returns failure`() {
        val result = parse("#EXTM3U\n")
        assertFalse(result.isSuccess)
    }

    @Test
    fun `comments other than EXTINF and EXTM3U are ignored`() {
        val m3u = """
            #EXTM3U
            # This is a comment
            #EXTVLCOPT:some-option
            #EXTINF:-1,Real Channel
            http://stream.example.com/real
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(1, result.channels.size)
        assertEquals("Real Channel", result.channels[0].name)
    }

    @Test
    fun `attributes with single quotes are extracted safely`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id='bad',Single Quote Channel
            http://stream.example.com/test
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals("bad", result.channels[0].tvgId)
    }

    @Test
    fun `provider ID is correctly assigned to all channels`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Ch1
            http://stream.example.com/1
            #EXTINF:-1,Ch2
            http://stream.example.com/2
        """.trimIndent()

        val result = parse(m3u, "my-provider-123")
        assertTrue(result.isSuccess)
        assertEquals("my-provider-123", result.channels[0].providerId)
        assertEquals("my-provider-123", result.channels[1].providerId)
    }

    @Test
    fun `all channels get unique IDs`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1,Ch1
            http://stream.example.com/1
            #EXTINF:-1,Ch2
            http://stream.example.com/2
            #EXTINF:-1,Ch3
            http://stream.example.com/3
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        val ids = result.channels.map { it.id }.toSet()
        assertEquals("All channel IDs should be unique", 3, ids.size)
    }

    @Test
    fun `channel and attributes containing commas are parsed correctly`() {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="CNN.us" group-title="News, Sports" tvg-logo="https://example.com/logo,1.png",BBC News, HD
            http://stream.example.com/bbc
        """.trimIndent()

        val result = parse(m3u)
        assertTrue(result.isSuccess)
        assertEquals(1, result.channels.size)
        val channel = result.channels[0]
        assertEquals("BBC News, HD", channel.name)
        assertEquals("News, Sports", channel.groupTitle)
        assertEquals("https://example.com/logo,1.png", channel.tvgLogo)
        assertEquals("CNN.us", channel.tvgId)
    }
}
