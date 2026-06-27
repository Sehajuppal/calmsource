package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefaultSourceParserTest {

    private val parser = DefaultSourceParser()

    // --- Pipe-delimited format ---

    @Test
    fun `pipe format with title and URL`() {
        val result = parser.parse("My Movie|https://example.com/movie.mkv", "verified_debrid")
        assertEquals(1, result.size)
        assertEquals("My Movie", result.first().title)
        assertEquals("https://example.com/movie.mkv", result.first().getRawUrlUnsafe())
        assertEquals(PlaybackSourceType.EXTENSION, result.first().type)
    }

    @Test
    fun `pipe format with blank lines and whitespace`() {
        val payload = """
            Movie One|https://example.com/a.mp4
            
            Movie Two|https://example.com/b.mp4
        """.trimIndent()
        val result = parser.parse(payload, "verified_debrid")
        assertEquals(2, result.size)
    }

    @Test
    fun `pipe format with only one part is rejected`() {
        val result = parser.parse("JustATitle", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with blank title is rejected`() {
        val result = parser.parse("  |https://example.com/movie.mkv", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with unsupported URL scheme is rejected`() {
        val result = parser.parse("Movie|ftp://example.com/movie.mkv", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with javascript URL is rejected`() {
        val result = parser.parse("Movie|javascript:alert(1)", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with data URL is rejected`() {
        val result = parser.parse("Movie|data:text/plain;base64,SGVsbG8=", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with blank lines only returns empty`() {
        val result = parser.parse("\n\n  \n", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `pipe format with three parts only uses first two`() {
        val result = parser.parse("Title|https://example.com/movie.mkv|extra|stuff", "verified_debrid")
        assertEquals(1, result.size)
        assertEquals("Title", result.first().title)
    }

    // --- Stremio JSON format ---

    @Test
    fun `stremio JSON with single stream`() {
        val payload = """{"streams":[{"title":"Movie 4K","url":"https://example.com/stream.mkv"}]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertEquals(1, result.size)
        assertEquals("Movie 4K", result.first().title)
    }

    @Test
    fun `stremio JSON with infoHash generates magnet URL`() {
        val payload = """{"streams":[{"title":"Torrent","infoHash":"abc123def456"}]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertEquals(1, result.size)
        assertTrue(result.first().getRawUrlUnsafe().startsWith("magnet:?xt=urn:btih:abc123def456"))
    }

    @Test
    fun `stremio JSON stream missing both title and URL returns nothing`() {
        val payload = """{"streams":[{"quality":"4K"}]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `stremio JSON with empty streams array`() {
        val payload = """{"streams":[]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `stremio JSON with name field as fallback title`() {
        val payload = """{"streams":[{"name":"Fallback Name","url":"https://example.com/s.mkv"}]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertEquals(1, result.size)
        assertEquals("Fallback Name", result.first().title)
    }

    @Test
    fun `stremio JSON with behaviourHints filename as fallback title`() {
        val payload = """{"streams":[{"behaviorHints":{"filename":"hint_name.mkv"},"url":"https://example.com/s.mkv"}]}"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertEquals(1, result.size)
        assertEquals("hint_name.mkv", result.first().title)
    }

    @Test
    fun `top-level JSON array is parsed as streams`() {
        val payload = """[{"title":"Array Movie","url":"https://example.com/m.mkv"}]"""
        val result = parser.parse(payload, "official_stremio_addon")
        assertEquals(1, result.size)
        assertEquals("Array Movie", result.first().title)
    }

    // --- Origin filtering ---

    @Test
    fun `origin in allowed set is accepted`() {
        val result = parser.parse("Movie|https://example.com/m.mkv", "local_iptv")
        assertEquals(1, result.size)
    }

    @Test
    fun `origin not in allowed set is rejected`() {
        val result = parser.parse("Movie|https://example.com/m.mkv", "unknown_scraper")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `empty allowed origins rejects everything`() {
        val restricted = DefaultSourceParser(allowedOrigins = emptySet())
        val result = restricted.parse("Movie|https://example.com/m.mkv", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    // --- Secret redaction ---

    @Test
    fun `API key in title is redacted`() {
        val result = parser.parse("Movie apikey=secret123|https://example.com/m.mkv", "verified_debrid")
        assertEquals(1, result.size)
        assertTrue(result.first().title.contains("[REDACTED]"))
        assertTrue(!result.first().title.contains("secret123"))
    }

    @Test
    fun `token in title is redacted`() {
        val result = parser.parse("Movie token=abc|https://example.com/m.mkv", "verified_debrid")
        assertEquals(1, result.size)
        assertTrue(result.first().title.contains("[REDACTED]"))
    }

    @Test
    fun `secret in title is redacted`() {
        val result = parser.parse("Movie secret=xyz|https://example.com/m.mkv", "verified_debrid")
        assertEquals(1, result.size)
        assertTrue(result.first().title.contains("[REDACTED]"))
    }

    @Test
    fun `key param in title is redacted`() {
        val result = parser.parse("Movie key=mykey|https://example.com/m.mkv", "verified_debrid")
        assertEquals(1, result.size)
        assertTrue(result.first().title.contains("[REDACTED]"))
    }

    // --- URL validation ---

    @Test
    fun `empty payload returns empty`() {
        val result = parser.parse("", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `whitespace payload returns empty`() {
        val result = parser.parse("   ", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `magnet URL is accepted`() {
        val result = parser.parse("Torrent|magnet:?xt=urn:btih:abc", "verified_debrid")
        assertEquals(1, result.size)
    }

    @Test
    fun `xtream URL is accepted`() {
        val result = parser.parse("Live|xtream://example.com/live/stream", "verified_debrid")
        assertEquals(1, result.size)
    }

    @Test
    fun `acestream URL is accepted`() {
        val result = parser.parse("Stream|acestream://uuid", "verified_debrid")
        assertEquals(1, result.size)
    }

    @Test
    fun `sop URL is accepted`() {
        val result = parser.parse("Stream|sop://example.com/stream", "verified_debrid")
        assertEquals(1, result.size)
    }

    @Test
    fun `URL with control characters is rejected`() {
        val result = parser.parse("Bad|\u0000https://example.com/stream", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `URL with whitespace is rejected`() {
        val result = parser.parse("Bad|https://example.com/ bad.mkv", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `file scheme is rejected`() {
        val result = parser.parse("Local|file:///sdcard/movie.mkv", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `blob scheme is rejected`() {
        val result = parser.parse("Blob|blob:https://example.com/uuid", "verified_debrid")
        assertTrue(result.isEmpty())
    }

    // --- Robustness ---

    @Test
    fun `very long payload does not crash`() {
        val longTitle = "A".repeat(10_000)
        val result = parser.parse("$longTitle|https://example.com/m.mkv", "verified_debrid")
        assertEquals(1, result.size)
    }

    @Test
    fun `very many lines do not crash`() {
        val lines = (1..500).joinToString("\n") { i ->
            "Movie $i|https://example.com/m$i.mkv"
        }
        val result = parser.parse(lines, "verified_debrid")
        assertEquals(500, result.size)
    }

    @Test
    fun `deterministic origin rejection`() {
        val r1 = parser.parse("Movie|https://example.com/m.mkv", "unknown")
        val r2 = parser.parse("Movie|https://example.com/m.mkv", "unknown")
        assertTrue(r1.isEmpty())
        assertTrue(r2.isEmpty())
    }
}
