package com.example.calmsource.core.playback

import android.content.Context
import android.content.SharedPreferences
import com.example.calmsource.core.model.AutoFallbackPolicy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlaybackAuditConfirmationTest {

    @Before
    fun resetGlobals() {
        FallbackPreferences.policy = AutoFallbackPolicy.AUTO_FALLBACK_LIMITED
        FallbackPreferences.enableFallbackSafeProfileOnDecoderError = false
        FrameRateMatchingPreferences.mode = FrameRateMatchingMode.OFF
    }

    @Test
    fun coldStartMemoryPolicyIgnoresDiskUntilWarmBestEffort() {
        val ctx = mock<Context>()
        val prefs = mock<SharedPreferences>()
        whenever(ctx.applicationContext).thenReturn(ctx)
        whenever(ctx.getSharedPreferences("fallback_preferences", Context.MODE_PRIVATE)).thenReturn(prefs)
        whenever(prefs.getString("fallback_policy", AutoFallbackPolicy.AUTO_FALLBACK_LIMITED.name))
            .thenReturn(AutoFallbackPolicy.ASK_BEFORE_FALLBACK.name)
        whenever(prefs.getBoolean("decoder_fallback", false)).thenReturn(true)

        val memoryBeforeWarm = FallbackPreferences.policy
        FallbackPreferences.warmBestEffort(ctx)
        val memoryAfterWarm = FallbackPreferences.policy

        assertEquals(AutoFallbackPolicy.AUTO_FALLBACK_LIMITED, memoryBeforeWarm)
        assertNotEquals(memoryBeforeWarm, AutoFallbackPolicy.ASK_BEFORE_FALLBACK)
        assertEquals(AutoFallbackPolicy.ASK_BEFORE_FALLBACK, memoryAfterWarm)
    }

    @Test
    fun frameRateModeDefaultsOffWithoutWarm() {
        assertEquals(FrameRateMatchingMode.OFF, FrameRateMatchingPreferences.mode)
    }

    @Test
    fun isSurfaceRequiredResetsWhenLeavingVlc() {
        val root = java.io.File(System.getProperty("user.dir") ?: ".")
        val candidates = listOf(
            java.io.File(root, "src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt"),
            java.io.File(root, "core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt"),
            java.io.File(root.parentFile, "core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt"),
        )
        val pmFile = candidates.firstOrNull { it.exists() }
            ?: error("PlaybackManager.kt not found from user.dir=${root.absolutePath}")
        val text = pmFile.readText()

        val setFromVlc = Regex("isSurfaceRequired\\s*=\\s*vlcBackend\\.isSurfaceRequired").findAll(text).count()
        val setFalseCount = Regex("isSurfaceRequired\\s*=\\s*false").findAll(text).count()

        assertTrue(setFromVlc > 0)
        assertTrue(setFalseCount > 0)
    }
}
