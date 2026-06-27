package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackCrashMarkerTest {
    @Test
    fun `media URL hash stores sixteen bytes of SHA256 as hex`() {
        val rawUrl = "https://user:password@example.com/stream.m3u8?token=secret"
        val hash = PlaybackCrashMarker.mediaUrlHash(rawUrl)

        assertEquals(32, hash.length)
        assertTrue(hash.matches(Regex("[0-9a-f]{32}")))
        assertFalse(hash.contains("password"))
        assertFalse(hash.contains("token"))
        assertFalse(hash.contains("example.com"))
    }

    @Test
    fun `record stores safe source id and hashed media URL only`() {
        val source = PlaybackSource(
            id = "iptv-provider-channel-1",
            type = PlaybackSourceType.IPTV,
            title = "Channel",
            rawUrl = "xtream://stream_id/12345?token=very-secret"
        )

        val record = PlaybackCrashMarker.recordFor(
            source = source,
            nowMs = 1_000L,
            sessionId = "session-1"
        )

        assertEquals("session-1", record.sessionId)
        assertEquals("iptv", record.providerId)
        assertNotEquals(source.rawUrl, record.sourceId)
        assertNotEquals(source.rawUrl, record.mediaUrlHash)
        assertEquals(32, record.mediaUrlHash.length)
    }

    @Test
    fun `recovery marker is ignored before ten seconds`() {
        val record = marker(startedAtMs = 1_000L)

        assertNull(
            PlaybackCrashMarker.recoveryFor(
                record = record,
                nowMs = 10_999L
            )
        )
    }

    @Test
    fun `recovery marker is returned between ten seconds and five minutes`() {
        val record = marker(startedAtMs = 1_000L)

        val recovery = PlaybackCrashMarker.recoveryFor(
            record = record,
            nowMs = 11_000L
        )

        assertNotNull(recovery)
        assertEquals("source-1", recovery?.sourceIdToSkip)
        assertEquals("provider", recovery?.providerId)
        assertEquals(10_000L, recovery?.ageMs)
    }

    @Test
    fun `stale recovery marker is ignored after five minutes`() {
        val record = marker(startedAtMs = 1_000L)

        assertNull(
            PlaybackCrashMarker.recoveryFor(
                record = record,
                nowMs = 301_001L
            )
        )
    }

    @Test
    fun `playback manager and apps wire crash marker lifecycle`() {
        val manager = readSource(
            "core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt",
            "src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt"
        )
        val marker = readSource(
            "core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackCrashMarker.kt",
            "src/main/kotlin/com/example/calmsource/core/playback/PlaybackCrashMarker.kt"
        )
        val mobileApp = readSource("app-mobile/src/main/java/com/example/calmsource/CalmSourceApp.kt")
        val tvApp = readSource("app-tv/src/main/java/com/example/calmsource/tv/CalmSourceApp.kt")

        assertTrue(manager.contains("markStartedBestEffort(context, source)"))
        assertTrue(manager.contains("PlaybackCrashMarker.clearAsync(context, it.sessionId)"))
        assertTrue(mobileApp.contains("PlaybackCrashMarker.installGlobalUncaughtHandler(this)"))
        assertTrue(tvApp.contains("PlaybackCrashMarker.installGlobalUncaughtHandler(this)"))
        assertTrue(marker.contains("mediaUrlHash(source.rawUrl)"))
        assertFalse(marker.contains("runBlocking(Dispatchers.IO)"))
        assertFalse(marker.contains("stringPreferencesKey(\"raw_url\")"))
        assertFalse(marker.contains("stringPreferencesKey(\"rawUrl\")"))
    }

    private fun marker(startedAtMs: Long): PlaybackCrashMarkerRecord {
        return PlaybackCrashMarkerRecord(
            sessionId = "session-1",
            sourceId = "source-1",
            providerId = "provider",
            startedAtMs = startedAtMs,
            mediaUrlHash = "0123456789abcdef0123456789abcdef",
            processCrashed = true
        )
    }

    private fun readSource(vararg candidates: String): String {
        val roots = generateSequence(java.io.File(System.getProperty("user.dir") ?: ".")) { file ->
            file.parentFile
        }.toList()
        return candidates
            .flatMap { candidate ->
                listOf(java.io.File(candidate)) + roots.map { root -> java.io.File(root, candidate) }
            }
            .firstOrNull { it.exists() }
            ?.readText()
            ?: ""
    }
}
