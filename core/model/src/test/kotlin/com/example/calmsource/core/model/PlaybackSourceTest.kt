package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSourceTest {

    @Test
    fun `redactUrl removes tokens and queries`() {
        val rawUrl = "https://example.com/stream/123.m3u8?token=SECRET"
        val redacted = PlaybackSource.redactUrl(rawUrl)
        assertEquals("https://example.com/...", redacted)
    }

    @Test
    fun `redactUrl removes basic auth`() {
        val rawUrl = "http://user:password@malicious.com:8080/file.mp4"
        val redacted = PlaybackSource.redactUrl(rawUrl)
        assertEquals("http://malicious.com:8080/...", redacted)
    }

    @Test
    fun `redactUrl handles missing scheme safely`() {
        val rawUrl = "invalid-url-format"
        val redacted = PlaybackSource.redactUrl(rawUrl)
        assertEquals("redacted-url", redacted)
    }

    @Test
    fun `PlaybackSource default displayUrl is redacted`() {
        val source = PlaybackSource(
            id = "test_1",
            type = PlaybackSourceType.EXTENSION,
            title = "Test Stream",
            rawUrl = "https://secret-domain.com/movie.mp4?apikey=12345"
        )
        assertEquals("https://secret-domain.com/...", source.displayUrl)
        assertEquals("https://secret-domain.com/movie.mp4?apikey=12345", source.rawUrl)
        assertFalse(source.allowInsecureHttp)
    }

    @Test
    fun `PlaybackSource can carry source scoped cleartext approval`() {
        val source = PlaybackSource(
            id = "xtream-live-1",
            type = PlaybackSourceType.IPTV,
            title = "Xtream Live",
            rawUrl = "xtream://stream_id/1",
            allowInsecureHttp = true
        )

        assertTrue(source.allowInsecureHttp)
    }
}
