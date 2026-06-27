package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TunnelingPolicyTest {
    @Before
    fun setUp() {
        TunnelingPreferences.mode = TunnelingMode.OFF
    }

    @Test
    fun `tunneling defaults to off`() {
        assertEquals(TunnelingMode.OFF, TunnelingPreferences.mode)
        assertEquals(TunnelingMode.OFF, TunnelingPreferences.modeFromStorage(null))
        assertEquals(TunnelingMode.OFF, TunnelingPreferences.modeFromStorage("bogus"))
        assertEquals(TunnelingMode.AUTO, TunnelingPreferences.modeFromStorage("AUTO"))

        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(),
            profile = profile(),
            mode = TunnelingMode.OFF,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.MODE_OFF, decision.reason)
    }

    @Test
    fun `tunneling rejects sdk below android 11`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(),
            profile = profile(),
            mode = TunnelingMode.AUTO,
            sdkInt = 29,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.SDK_TOO_LOW, decision.reason)
    }

    @Test
    fun `fallback safe profile disables tunneling`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(),
            profile = profile(tunnelingEnabled = false),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.PROFILE_DISABLED, decision.reason)
    }

    @Test
    fun `device must be allowlisted before tunneling is enabled`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(),
            profile = profile(),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "unknown-box",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.DEVICE_NOT_ALLOWLISTED, decision.reason)
    }

    @Test
    fun `video-only sources are never tunneled`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(audioCodec = null, videoCodec = "H264"),
            profile = profile(),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.VIDEO_ONLY, decision.reason)
    }

    @Test
    fun `unsupported codecs are rejected`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(audioCodec = "DTS-HD", videoCodec = "AV1"),
            profile = profile(),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.UNSUPPORTED_CODECS, decision.reason)
    }

    @Test
    fun `blacklisted codec combination is rejected`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(audioCodec = "AAC", videoCodec = "H264"),
            profile = profile(),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true,
            isBlacklisted = { true }
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.BLACKLISTED, decision.reason)
        assertNotNull(decision.key)
    }

    @Test
    fun `current Media3 without public renderer setters disables tunneling safely`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(),
            profile = profile(),
            mode = TunnelingMode.ON,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = false
        )

        assertFalse(decision.enabled)
        assertEquals(TunnelingRejectReason.RENDERER_API_UNAVAILABLE, decision.reason)
    }

    @Test
    fun `compatible source on supported device can enable tunneling`() {
        val decision = TunnelingPolicy.decisionFor(
            source = playableSource(audioCodec = "E-AC3", videoCodec = "HEVC"),
            profile = profile(),
            mode = TunnelingMode.AUTO,
            sdkInt = 34,
            deviceModel = "test-tv",
            supportedDeviceModels = setOf("test-tv"),
            rendererFactorySupportsTunneling = true
        )

        assertTrue(decision.enabled)
        assertEquals(TunnelingRejectReason.NONE, decision.reason)
        assertNotNull(decision.key)
    }

    private fun playableSource(
        audioCodec: String? = "AAC",
        videoCodec: String? = "H264"
    ): PlaybackSource {
        return PlaybackSource(
            id = "provider-stream",
            type = PlaybackSourceType.EXTENSION,
            title = "Stream",
            rawUrl = "https://example.com/movie.m3u8",
            metadata = PlaybackItemMetadata(
                title = "Movie",
                audioCodec = audioCodec,
                videoCodec = videoCodec
            )
        )
    }

    private fun profile(
        tunnelingEnabled: Boolean = true
    ): PlaybackResourceProfile {
        return PlaybackResourceProfile(
            kind = PlaybackProfileKind.VOD_PROFILE,
            isLive = false,
            lowMemoryMode = false,
            memoryClassMb = 512,
            minBufferMs = 24_000,
            maxBufferMs = 72_000,
            bufferForPlaybackMs = 1_500,
            bufferForPlaybackAfterRebufferMs = 3_000,
            targetBufferBytes = 64 * 1024 * 1024,
            tunnelingEnabled = tunnelingEnabled
        )
    }
}
