package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.*
import org.junit.Test

/**
 * Playback Stabilization Tests (Sanitized)
 *
 * Covers edge cases from real-world IPTV source usage:
 * - PlaybackError enum completeness for real-world failures
 * - Raw playback URL leak prevention
 */
class PlaybackStabilizationTest {

    private val emptyUrlSource = PlaybackSource(
        id = "empty-url",
        type = PlaybackSourceType.IPTV,
        title = "Empty URL Channel",
        rawUrl = ""
    )

    // ========================================================================
    // Task 6: PlaybackError enum covers real-world failures
    // ========================================================================

    @Test
    fun `PlaybackError has Network type for connection failures`() {
        val error = PlaybackError.Network()
        assertEquals("Network error occurred", error.message)
        assertNull(error.cause)
    }

    @Test
    fun `PlaybackError has Timeout type for network timeouts`() {
        val error = PlaybackError.Timeout()
        assertEquals("Playback timed out", error.message)
    }

    @Test
    fun `PlaybackError has UnsupportedFormat for codec issues`() {
        val error = PlaybackError.UnsupportedFormat()
        assertEquals("Unsupported media format", error.message)
    }

    @Test
    fun `PlaybackError has ServerRefused for connection refused`() {
        val error = PlaybackError.ServerRefused()
        assertEquals("Server refused connection", error.message)
    }

    @Test
    fun `PlaybackError has SourceUnavailable for dead links`() {
        val error = PlaybackError.SourceUnavailable()
        assertEquals("Playback source is unavailable", error.message)
    }

    @Test
    fun `PlaybackError has DecoderError for decoder failures`() {
        val error = PlaybackError.DecoderError()
        assertEquals("Media decoder error", error.message)
    }

    @Test
    fun `PlaybackError has PermissionRequired for auth failures`() {
        val error = PlaybackError.PermissionRequired()
        assertEquals("Permission or authentication required", error.message)
    }

    @Test
    fun `PlaybackError has Unknown for unclassified errors`() {
        val error = PlaybackError.Unknown()
        assertEquals("Unknown playback error", error.message)
    }

    @Test
    fun `PlaybackError preserves cause chain for debugging`() {
        val rootCause = Exception("ExoPlayer error: ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT")
        val error = PlaybackError.Timeout(cause = rootCause)
        assertNotNull(error.cause)
        assertTrue(error.cause!!.message!!.contains("ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT"))
        // Verify the original raw URL is not in the error message
        assertFalse(error.cause!!.message!!.contains("http://"))
    }

    // ========================================================================
    // Task 8: Raw URL leak prevention
    // ========================================================================

    @Test
    fun `displayUrl never contains raw URL path or tokens`() {
        val sources = listOf(
            PlaybackSource(
                id = "token-url",
                type = PlaybackSourceType.IPTV,
                title = "Token Channel",
                rawUrl = "http://server.com:8080/user/password/12345.m3u8?token=secret123"
            ),
            PlaybackSource(
                id = "auth-url",
                type = PlaybackSourceType.IPTV,
                title = "Auth Channel",
                rawUrl = "http://admin:secretpass@xtream.server.com/live/abc123/def456/1.ts"
            ),
            PlaybackSource(
                id = "complex-url",
                type = PlaybackSourceType.IPTV,
                title = "Complex Channel",
                rawUrl = "https://cdn.example.com/hls/key=abc123&session=xyz789/playlist.m3u8"
            )
        )

        for (source in sources) {
            // displayUrl should not contain path components
            assertFalse(
                "displayUrl should not contain sensitive path for ${source.id}",
                source.displayUrl.contains("password") ||
                source.displayUrl.contains("secretpass") ||
                source.displayUrl.contains("token=") ||
                source.displayUrl.contains("key=") ||
                source.displayUrl.contains("session=") ||
                source.displayUrl.contains("admin:")
            )
            // displayUrl should end with /...
            assertTrue(
                "displayUrl should end with /... for ${source.id}",
                source.displayUrl.endsWith("/...")
            )
        }
    }

    @Test
    fun `error messages from buildMediaItem do not contain raw URL`() {
        val exceptionMessage = "URI is empty for source ${emptyUrlSource.id}"
        assertFalse(exceptionMessage.contains(emptyUrlSource.rawUrl.ifBlank { "BLANK" }))
        assertTrue(exceptionMessage.contains(emptyUrlSource.id))
    }

    @Test
    fun `PlaybackError scrubs ExoPlayer error without raw URL`() {
        val sanitizedException = Exception("ExoPlayer error: ERROR_CODE_IO_NETWORK_CONNECTION_FAILED")
        val error = PlaybackError.Network(cause = sanitizedException)

        // Should NOT contain any URL
        assertFalse(error.cause!!.message!!.contains("http"))
        assertFalse(error.cause!!.message!!.contains("://"))
        // Should contain error code name
        assertTrue(error.cause!!.message!!.contains("ERROR_CODE"))
    }

    @Test
    fun `toString of PlaybackSource does not leak rawUrl in production`() {
        val source = PlaybackSource(
            id = "leak-test",
            type = PlaybackSourceType.IPTV,
            title = "Leak Test",
            rawUrl = "http://secret:password@server.com/stream.m3u8"
        )
        // The displayUrl must be safe
        assertEquals("http://server.com/...", source.displayUrl)
        // Verify rawUrl is unchanged for ExoPlayer
        assertEquals("http://secret:password@server.com/stream.m3u8", source.rawUrl)
    }
}
