package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackProfileManagerTest {
    @Test
    fun `vod profile uses larger VOD buffer`() {
        val profile = PlaybackProfileManager.profileFor(
            isLive = false,
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 512)
        )

        assertEquals(PlaybackProfileKind.VOD_PROFILE, profile.kind)
        assertEquals(24_000, profile.minBufferMs)
        assertEquals(72_000, profile.maxBufferMs)
        assertEquals(1_500, profile.bufferForPlaybackMs)
        assertEquals(3_000, profile.bufferForPlaybackAfterRebufferMs)
        assertFalse(profile.lowMemoryMode)
        assertNull(profile.liveTargetOffsetMs)
    }

    @Test
    fun `live IPTV profile uses smaller buffer and live speed hints`() {
        val profile = PlaybackProfileManager.profileFor(
            source = source(isLive = true),
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 512)
        )

        assertEquals(PlaybackProfileKind.LIVE_IPTV_PROFILE, profile.kind)
        assertTrue(profile.isLive)
        assertEquals(8_000, profile.minBufferMs)
        assertEquals(24_000, profile.maxBufferMs)
        assertEquals(PlaybackProfileManager.LIVE_TARGET_OFFSET_MS, profile.liveTargetOffsetMs)
        assertEquals(PlaybackProfileManager.LIVE_MAX_PLAYBACK_SPEED, profile.liveMaxPlaybackSpeed ?: 0f, 0.0001f)
        assertTrue(PlaybackProfileManager.livePlaybackSpeedControl(profile) != null)
        assertTrue(PlaybackProfileManager.liveConfiguration(profile) != null)
    }

    @Test
    fun `low memory profile computes target buffer from memory class`() {
        val profile = PlaybackProfileManager.profileFor(
            isLive = false,
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 128, lowRamDevice = true)
        )

        assertEquals(PlaybackProfileKind.LOW_MEMORY_PROFILE, profile.kind)
        assertTrue(profile.lowMemoryMode)
        assertEquals(12_000, profile.minBufferMs)
        assertEquals(36_000, profile.maxBufferMs)
        assertEquals(16 * MB, profile.targetBufferBytes)
    }

    @Test
    fun `target buffer is capped at sixty four megabytes`() {
        assertEquals(64 * MB, PlaybackProfileManager.constrainedTargetBufferBytes(512))
        assertEquals(64 * MB, PlaybackProfileManager.constrainedTargetBufferBytes(1024))
    }

    @Test
    fun `fallback safe profile lowers risky playback preferences`() {
        val profile = PlaybackProfileManager.profileFor(
            isLive = false,
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 512),
            history = PlaybackProfileHistory(useFallbackSafeProfile = true)
        )

        assertEquals(PlaybackProfileKind.FALLBACK_SAFE_PROFILE, profile.kind)
        assertEquals(12_000, profile.minBufferMs)
        assertEquals(24_000, profile.maxBufferMs)
        assertEquals(720, profile.maxVideoHeight)
        assertFalse(profile.tunnelingEnabled)
    }

    @Test
    fun `compatibility key changes between VOD and live profiles`() {
        val vod = PlaybackProfileManager.profileFor(
            isLive = false,
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 512)
        )
        val live = PlaybackProfileManager.profileFor(
            isLive = true,
            deviceProfile = PlaybackDeviceProfile(memoryClassMb = 512)
        )

        assertNotEquals(vod.compatibilityKey, live.compatibilityKey)
    }

    private fun source(isLive: Boolean): PlaybackSource {
        return PlaybackSource(
            id = if (isLive) "live-1" else "vod-1",
            type = PlaybackSourceType.IPTV,
            title = "Test Source",
            rawUrl = "http://example.com/stream.m3u8",
            metadata = PlaybackItemMetadata(
                title = "Test Source",
                isLive = isLive
            )
        )
    }

    private companion object {
        const val MB = 1024 * 1024
    }
}
