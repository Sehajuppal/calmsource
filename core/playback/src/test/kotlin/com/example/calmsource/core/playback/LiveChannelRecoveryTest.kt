package com.example.calmsource.core.playback

import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.model.PlayerUiState
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Test

class LiveChannelRecoveryTest {

    private fun channel(id: String, url: String = "xtream://live/$id") = IPTVChannel(
        id = id,
        tvgId = id,
        tvgName = id,
        tvgLogo = null,
        groupTitle = "News",
        name = "Channel $id",
        streamUrl = url,
        providerId = "provider"
    )

    @Test
    fun `maxAutoSwitchCount respects fallback policy`() {
        assertEquals(0, LiveChannelRecovery.maxAutoSwitchCount(AutoFallbackPolicy.OFF))
        assertEquals(0, LiveChannelRecovery.maxAutoSwitchCount(AutoFallbackPolicy.ASK_BEFORE_FALLBACK))
        assertEquals(1, LiveChannelRecovery.maxAutoSwitchCount(AutoFallbackPolicy.AUTO_FALLBACK_ONCE))
        assertEquals(3, LiveChannelRecovery.maxAutoSwitchCount(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED))
    }

    @Test
    fun `suggestNextChannel resolves alt stream identity to base channel`() = runTest {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val next = LiveChannelRecovery.suggestNextChannel(
            currentChannelId = "a-alt-m3u8",
            channels = channels,
            healthScoreFor = { 100 }
        )
        assertEquals("b", next?.id)
    }

    @Test
    fun `suggestNextChannel returns null when current channel is not in guide`() = runTest {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        assertNull(
            LiveChannelRecovery.suggestNextChannel(
                currentChannelId = "unknown",
                channels = channels,
                healthScoreFor = { 100 }
            )
        )
    }

    @Test
    fun `suggestNextChannel picks healthiest of next three candidates`() = runTest {
        val channels = listOf(
            channel("a"),
            channel("b"),
            channel("c"),
            channel("d"),
            channel("e")
        )
        val health = mapOf(
            channels[1].safeSourceId to 50,
            channels[2].safeSourceId to 90,
            channels[3].safeSourceId to 70
        )
        val next = LiveChannelRecovery.suggestNextChannel(
            currentChannelId = "a",
            channels = channels,
            healthScoreFor = { health[it] ?: 100 }
        )
        assertEquals("c", next?.id)
    }

    @Test
    fun `suggestNextChannel wraps around end of guide`() = runTest {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        val next = LiveChannelRecovery.suggestNextChannel(
            currentChannelId = "c",
            channels = channels,
            healthScoreFor = { 100 }
        )
        assertEquals("a", next?.id)
    }

    @Test
    fun `suggestNextChannel never returns the current channel in a short guide`() = runTest {
        val channels = listOf(channel("a"), channel("b"))
        val health = mapOf(
            channels[0].safeSourceId to 100,
            channels[1].safeSourceId to 50
        )
        val next = LiveChannelRecovery.suggestNextChannel(
            currentChannelId = "a",
            channels = channels,
            healthScoreFor = { health[it] ?: 100 }
        )
        assertEquals("b", next?.id)
    }

    @Test
    fun `shouldAttemptLiveChannelAutoSwitch requires failed settled state`() {
        val policy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED
        val failed = PlayerUiState(
            playerState = PlayerState.FAILED,
            error = PlaybackError.SourceUnavailable("x"),
            isTransitioningSource = false,
        )
        assertTrue(LiveChannelRecovery.shouldAttemptLiveChannelAutoSwitch(failed, policy))

        val transitioning = failed.copy(isTransitioningSource = true)
        assertFalse(LiveChannelRecovery.shouldAttemptLiveChannelAutoSwitch(transitioning, policy))

        val buffering = failed.copy(playerState = PlayerState.BUFFERING)
        assertFalse(LiveChannelRecovery.shouldAttemptLiveChannelAutoSwitch(buffering, policy))

        assertFalse(
            LiveChannelRecovery.shouldAttemptLiveChannelAutoSwitch(
                failed,
                AutoFallbackPolicy.OFF,
            )
        )
    }

    @Test
    fun `suggestNextChannel rejects poor candidates`() = runTest {
        val channels = listOf(channel("a"), channel("b"), channel("c"))
        assertNull(
            LiveChannelRecovery.suggestNextChannel(
                currentChannelId = "a",
                channels = channels,
                healthScoreFor = { 20 }
            )
        )
    }
}
