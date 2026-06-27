package com.example.calmsource.feature.iptv.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class XtreamStreamUrlBuilderTest {

    // ── buildLiveUrl ────────────────────────────────────────────────────

    @Test
    fun `buildLiveUrl produces correct format`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://example.com:8080",
            username = "testuser",
            password = "testpass",
            streamId = "12345"
        )
        assertEquals("http://example.com:8080/live/testuser/testpass/12345.ts", url)
    }

    @Test
    fun `buildLiveUrl with custom output format`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://example.com:8080",
            username = "testuser",
            password = "testpass",
            streamId = "12345",
            outputFormat = "m3u8"
        )
        assertEquals("http://example.com:8080/live/testuser/testpass/12345.m3u8", url)
    }

    @Test
    fun `buildLiveUrl strips trailing slash from serverUrl`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://example.com:8080/",
            username = "user",
            password = "pass",
            streamId = "999"
        )
        assertEquals("http://example.com:8080/live/user/pass/999.ts", url)
    }

    @Test
    fun `buildLiveUrl with https and no port`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "https://secure.example.com",
            username = "admin",
            password = "secret",
            streamId = "42"
        )
        assertEquals("https://secure.example.com/live/admin/secret/42.ts", url)
    }

    // ── buildVodUrl ─────────────────────────────────────────────────────

    @Test
    fun `buildVodUrl produces correct format`() {
        val url = XtreamStreamUrlBuilder.buildVodUrl(
            serverUrl = "http://example.com:8080",
            username = "testuser",
            password = "testpass",
            streamId = "67890"
        )
        assertEquals("http://example.com:8080/movie/testuser/testpass/67890.mp4", url)
    }

    @Test
    fun `buildVodUrl with custom container extension`() {
        val url = XtreamStreamUrlBuilder.buildVodUrl(
            serverUrl = "http://example.com",
            username = "user",
            password = "pass",
            streamId = "555",
            containerExtension = "mkv"
        )
        assertEquals("http://example.com/movie/user/pass/555.mkv", url)
    }

    @Test
    fun `buildVodUrl strips trailing slash`() {
        val url = XtreamStreamUrlBuilder.buildVodUrl(
            serverUrl = "http://example.com/",
            username = "u",
            password = "p",
            streamId = "1"
        )
        assertEquals("http://example.com/movie/u/p/1.mp4", url)
    }

    // ── buildSeriesUrl ──────────────────────────────────────────────────

    @Test
    fun `buildSeriesUrl produces correct format`() {
        val url = XtreamStreamUrlBuilder.buildSeriesUrl(
            serverUrl = "http://example.com:8080",
            username = "testuser",
            password = "testpass",
            episodeId = "11111"
        )
        assertEquals("http://example.com:8080/series/testuser/testpass/11111.mp4", url)
    }

    @Test
    fun `buildSeriesUrl with custom container extension`() {
        val url = XtreamStreamUrlBuilder.buildSeriesUrl(
            serverUrl = "http://example.com",
            username = "user",
            password = "pass",
            episodeId = "222",
            containerExtension = "avi"
        )
        assertEquals("http://example.com/series/user/pass/222.avi", url)
    }

    @Test
    fun `buildSeriesUrl strips trailing slash`() {
        val url = XtreamStreamUrlBuilder.buildSeriesUrl(
            serverUrl = "http://example.com:9090/",
            username = "u",
            password = "p",
            episodeId = "3"
        )
        assertEquals("http://example.com:9090/series/u/p/3.mp4", url)
    }

    // ── extractStreamId ─────────────────────────────────────────────────

    @Test
    fun `extractStreamId parses xtream pseudo-URL correctly`() {
        val streamId = XtreamStreamUrlBuilder.extractStreamId("xtream://stream_id/provider1/12345")
        assertEquals("12345", streamId)
    }

    @Test
    fun `extractStreamId also works with legacy single-segment format`() {
        val streamId = XtreamStreamUrlBuilder.extractStreamId("xtream://stream_id/12345")
        assertEquals("12345", streamId)
    }

    @Test
    fun `extractStreamId returns null for non-xtream URLs`() {
        assertNull(XtreamStreamUrlBuilder.extractStreamId("http://example.com/stream"))
        assertNull(XtreamStreamUrlBuilder.extractStreamId("https://example.com/live/user/pass/12345.ts"))
        assertNull(XtreamStreamUrlBuilder.extractStreamId(""))
        assertNull(XtreamStreamUrlBuilder.extractStreamId("xtream://other/12345"))
    }

    @Test
    fun `extractStreamId returns null for xtream prefix without stream_id path`() {
        // "xtream://something_else/123" does not start with "xtream://stream_id/"
        assertNull(XtreamStreamUrlBuilder.extractStreamId("xtream://something_else/123"))
    }

    @Test
    fun `extractStreamId returns null for xtream stream_id with empty id`() {
        // "xtream://stream_id/" → empty after removePrefix → takeIf returns null
        assertNull(XtreamStreamUrlBuilder.extractStreamId("xtream://stream_id/"))
    }

    // ── createPseudoUrl ─────────────────────────────────────────────────

    @Test
    fun `createPseudoUrl creates correct format`() {
        val pseudoUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider1", "12345")
        assertEquals("xtream://stream_id/provider1/12345", pseudoUrl)
    }

    @Test
    fun `createPseudoUrl roundtrips with extractStreamId`() {
        val original = "99999"
        val pseudoUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider1", original)
        val extracted = XtreamStreamUrlBuilder.extractStreamId(pseudoUrl)
        assertEquals(original, extracted)
    }

    // ── isXtreamChannel detection ───────────────────────────────────────

    @Test
    fun `isXtreamChannel detects xtream pseudo-URL channels`() {
        val channel = createTestChannel(streamUrl = "xtream://stream_id/12345")
        assertTrue(XtreamPlaybackHelper.isXtreamChannel(channel))
    }

    @Test
    fun `isXtreamChannel returns false for regular URL channels`() {
        val channel = createTestChannel(streamUrl = "http://example.com/stream.ts")
        assertFalse(XtreamPlaybackHelper.isXtreamChannel(channel))
    }

    @Test
    fun `isXtreamChannel returns false for empty URL`() {
        val channel = createTestChannel(streamUrl = "")
        assertFalse(XtreamPlaybackHelper.isXtreamChannel(channel))
    }

    // ── URL construction with various server URL formats ────────────────

    @Test
    fun `buildLiveUrl with server URL containing path`() {
        // Subdirectory installs keep the base path for stream URLs.
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://example.com:8080/panel",
            username = "u",
            password = "p",
            streamId = "1"
        )
        assertEquals("http://example.com:8080/panel/live/u/p/1.ts", url)
    }

    @Test
    fun `buildLiveUrl with standard port 80`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://example.com",
            username = "user",
            password = "pass",
            streamId = "100"
        )
        assertEquals("http://example.com/live/user/pass/100.ts", url)
    }

    @Test
    fun `buildLiveUrl with non-standard port`() {
        val url = XtreamStreamUrlBuilder.buildLiveUrl(
            serverUrl = "http://192.168.1.1:25461",
            username = "user",
            password = "pass",
            streamId = "777"
        )
        assertEquals("http://192.168.1.1:25461/live/user/pass/777.ts", url)
    }

    // ── Helper ──────────────────────────────────────────────────────────

    private fun createTestChannel(
        id: String = "test-ch-1",
        streamUrl: String = "http://example.com/stream.ts",
        providerId: String = "provider-1"
    ): com.example.calmsource.core.model.IPTVChannel {
        return com.example.calmsource.core.model.IPTVChannel(
            id = id,
            tvgId = null,
            tvgName = null,
            tvgLogo = null,
            groupTitle = null,
            name = "Test Channel",
            streamUrl = streamUrl,
            providerId = providerId,
            rawAttributes = emptyMap()
        )
    }
}
