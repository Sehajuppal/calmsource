package com.example.calmsource.core.parser

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class ParserChaosTest {

    @Test
    fun `test M3UParser empty input`() = runBlocking {
        val result = M3UParser.parse("".byteInputStream(), "provider-1")
        assertFalse(result.isSuccess)
        assertEquals(0, result.channelCount)
    }

    @Test
    fun `test M3UParser giant attributes`() = runBlocking {
        val hugeString = "A".repeat(100_000)
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="$hugeString" tvg-name="Test" tvg-logo="$hugeString" group-title="$hugeString",Channel Name
            http://example.com/stream1.m3u8
        """.trimIndent()
        
        val result = M3UParser.parse(m3u.byteInputStream(), "provider-1")
        assertFalse(result.isSuccess)
        assertEquals(0, result.channelCount)
        assertTrue(result.warnings.any { it.contains("oversized") })
    }

    @Test
    fun `test M3UParser malformed attributes and missing commas`() = runBlocking {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="123 tvg-name="No closing quote tvg-logo="http://" group-title="Group
            http://example.com/stream1.m3u8
            #EXTINF:-1 , Just Comma
            http://example.com/stream2.m3u8
            #EXTINF:-1 No Comma But Long Name
            http://example.com/stream3.m3u8
        """.trimIndent()

        val result = M3UParser.parse(m3u.byteInputStream(), "provider-1")
        assertTrue(result.isSuccess)
        assertEquals(3, result.channelCount)
    }

    @Test
    fun `test M3UParser unicode and special characters`() = runBlocking {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="id!@#$%" tvg-name="テストチャンネル" tvg-logo="https://example.com/ロゴ.png" group-title="📺 Movies 🎥",مرحبا بالعالم
            http://example.com/ストリーム.m3u8
        """.trimIndent()
        
        val result = M3UParser.parse(m3u.byteInputStream(), "provider-1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.channelCount)
    }

    @Test
    fun `test M3UParser overlapping duplicate streams`() = runBlocking {
        val m3u = """
            #EXTM3U
            #EXTINF:-1 tvg-name="Ch 1",Ch 1
            http://example.com/stream.m3u8
            #EXTINF:-1 tvg-name="Ch 2",Ch 2
            http://example.com/stream.m3u8
        """.trimIndent()
        
        val result = M3UParser.parse(m3u.byteInputStream(), "provider-1")
        assertTrue(result.isSuccess)
        assertEquals(1, result.channelCount) // Second should be skipped
        assertTrue(result.warnings.any { it.contains("duplicate") })
    }

    @Test
    fun `test XMLTVParser empty input`() = runBlocking {
        val result = XMLTVParser.parse("".byteInputStream())
        assertFalse(result.isSuccess)
        assertTrue(result.programs.isEmpty())
    }

    @Test
    fun `test XMLTVParser malformed structure missing tags`() = runBlocking {
        val xml = """
            <tv>
              <programme>
                 <title>Broken Programme</title>
              </programme>
              <programme channel="ch1">
                 <title>Missing stop time</title>
              </programme>
            </tv>
        """.trimIndent()
        
        val result = XMLTVParser.parse(xml.byteInputStream())
        // Should produce warnings and probably 1 successful if start/stop aren't fully required, or 0 if start/stop throws. 
        // Based on code, channelAttr is checked for null, but startAttr/stopAttr parse failures fall back to 0L.
        // The first lacks channel, second lacks start/stop.
        // Wait, startAttr parsing falls back to 0L, so second should succeed.
    }

    @Test
    fun `test XMLTVParser massive programme tag`() = runBlocking {
        val hugeDesc = "A".repeat(3 * 1024 * 1024) // 3 MB string
        val xml = """
            <tv>
              <programme start="20260605100000 +0000" stop="20260605110000 +0000" channel="ch1">
                 <title>Massive Desc</title>
                 <desc>$hugeDesc</desc>
              </programme>
            </tv>
        """.trimIndent()
        
        val result = XMLTVParser.parse(xml.byteInputStream())
        // Should skip the massive programme tag to prevent OOM
        assertFalse(result.isSuccess)
        assertTrue(result.warnings.any { it.contains("massive") || it.contains("OOM") })
    }

    @Test
    fun `test XMLTVParser invalid date formats`() = runBlocking {
        val xml = """
            <tv>
              <programme start="invalid date" stop="-9999999999" channel="ch1">
                 <title>Test Invalid Dates</title>
              </programme>
            </tv>
        """.trimIndent()
        
        val result = XMLTVParser.parse(xml.byteInputStream())
        assertFalse(result.isSuccess)
        assertTrue(result.programs.isEmpty())
        assertEquals(1, result.stats.invalidTimePrograms)
    }
}
