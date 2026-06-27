package com.example.calmsource.core.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Test

class FrameRateMatchingTest {
    @Test
    fun `frame rate matching defaults to off`() {
        assertEquals(FrameRateMatchingMode.OFF, FrameRateMatchingPreferences.mode)
        assertEquals(FrameRateMatchingMode.OFF, FrameRateMatchingPreferences.modeFromStorage(null))
        assertEquals(FrameRateMatchingMode.OFF, FrameRateMatchingPreferences.modeFromStorage("bogus"))
    }

    @Test
    fun `stored seamless mode is restored`() {
        assertEquals(
            FrameRateMatchingMode.SEAMLESS_ONLY,
            FrameRateMatchingPreferences.modeFromStorage("SEAMLESS_ONLY")
        )
    }

    @Test
    fun `policy maps modes to Media3 strategies`() {
        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_OFF,
            FrameRateMatchingPolicy.media3Strategy(FrameRateMatchingMode.OFF)
        )
        assertEquals(
            C.VIDEO_CHANGE_FRAME_RATE_STRATEGY_ONLY_IF_SEAMLESS,
            FrameRateMatchingPolicy.media3Strategy(FrameRateMatchingMode.SEAMLESS_ONLY)
        )
    }
}
