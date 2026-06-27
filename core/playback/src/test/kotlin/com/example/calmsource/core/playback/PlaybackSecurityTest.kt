package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackSecurityTest {

    @Test
    fun `redactUrl hides credentials and path`() {
        val cases = mapOf(
            "http://user:pass@example.com:8080/stream/123?token=abc" to "http://example.com:8080/...",
            "https://server.com/test.m3u8" to "https://server.com/...",
            "http://xtream-server.com:80/username/password/1234.m3u8" to "http://xtream-server.com:80/...",
            "invalid-url-no-scheme" to "redacted-url",
            "http://domain.com" to "http://domain.com/..."
        )

        for ((input, expected) in cases) {
            assertEquals("URL should be redacted correctly", expected, PlaybackSource.redactUrl(input))
        }
    }

    @Test
    fun `playback source displayUrl defaults to redacted url`() {
        val source = PlaybackSource(
            id = "test",
            type = PlaybackSourceType.IPTV,
            title = "Test Channel",
            rawUrl = "http://user:pass@example.com/stream.m3u8"
        )

        assertEquals("Display URL must not leak raw URL", "http://example.com/...", source.displayUrl)
    }

    // ── Xtream-style embedded credential URL tests ──────────────────

    @Test
    fun `redactUrl strips Xtream path-based credentials`() {
        // Xtream URLs embed username/password in the path: /username/password/stream_id.ts
        val url = "http://xtream-server.com:25461/myuser/mypassword/12345.ts"
        val redacted = PlaybackSource.redactUrl(url)
        assertEquals("http://xtream-server.com:25461/...", redacted)
        // Verify credentials don't appear
        assert(!redacted.contains("myuser"))
        assert(!redacted.contains("mypassword"))
    }

    @Test
    fun `redactUrl strips Xtream query-based credentials`() {
        // Xtream URLs with query params: ?username=X&password=Y
        val url = "http://xtream.tv:8080/player_api.php?username=admin&password=secret&action=get_live_streams"
        val redacted = PlaybackSource.redactUrl(url)
        assertEquals("http://xtream.tv:8080/...", redacted)
        assert(!redacted.contains("admin"))
        assert(!redacted.contains("secret"))
    }

    @Test
    fun `redactUrl strips Xtream m3u8 with embedded credentials`() {
        val url = "http://line.provider.com:80/testuser/testpass/live/channel1.m3u8"
        val redacted = PlaybackSource.redactUrl(url)
        assertEquals("http://line.provider.com:80/...", redacted)
        assert(!redacted.contains("testuser"))
        assert(!redacted.contains("testpass"))
    }

    @Test
    fun `redactUrl handles ports correctly`() {
        val urls = mapOf(
            "http://server.com:25461/user/pass/123.ts" to "http://server.com:25461/...",
            "https://secure.tv:443/stream.m3u8" to "https://secure.tv:443/...",
            "http://plain.tv/stream.m3u8" to "http://plain.tv/..."
        )
        for ((input, expected) in urls) {
            assertEquals(expected, PlaybackSource.redactUrl(input))
        }
    }
}
