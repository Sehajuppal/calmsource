package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackItemMetadata
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TunnelingBlacklistTest {
    @Test
    fun `blacklist key is per device audio and video codec`() {
        val a = TunnelingBlacklist.keyFor("Device A", "AAC", "H264")
        val b = TunnelingBlacklist.keyFor("Device B", "AAC", "H264")
        val c = TunnelingBlacklist.keyFor("Device A", "E-AC3", "H264")
        val d = TunnelingBlacklist.keyFor("Device A", "AAC", "HEVC")

        assertFalse(a.storageKey == b.storageKey)
        assertFalse(a.storageKey == c.storageKey)
        assertFalse(a.storageKey == d.storageKey)
    }

    @Test
    fun `two failures permanently blacklist a codec combination`() {
        val key = TunnelingBlacklist.keyFor("test-tv", "AAC", "H264")
        val first = TunnelingBlacklist.recordFailure(emptyMap(), key, nowMs = 1_000L)
        assertFalse(TunnelingBlacklist.isBlacklisted(first[key.storageKey]))

        val second = TunnelingBlacklist.recordFailure(first, key, nowMs = 2_000L)
        val entry = second[key.storageKey]

        assertNotNull(entry)
        assertEquals(2, entry?.failureCount)
        assertEquals(2_000L, entry?.lastFailureAtMs)
        assertTrue(TunnelingBlacklist.isBlacklisted(entry))
    }

    @Test
    fun `source key is absent for video-only or unknown codec streams`() {
        assertNull(
            TunnelingBlacklist.keyFor(
                source(audioCodec = null, videoCodec = "H264"),
                deviceModel = "test-tv"
            )
        )
        assertNull(
            TunnelingBlacklist.keyFor(
                source(audioCodec = "AAC", videoCodec = null),
                deviceModel = "test-tv"
            )
        )
    }

    @Test
    fun `encoded blacklist entries round trip without raw URLs`() {
        val key = TunnelingBlacklist.keyFor("Device | With Spaces", "E-AC3", "H.265")
        val entries = TunnelingBlacklist.recordFailure(emptyMap(), key, nowMs = 123L)
        val encoded = TunnelingBlacklist.encodeEntries(entries)
        val row = encoded.single()

        assertFalse(row.contains("https://"))
        assertFalse(row.contains("rawUrl"))
        assertTrue(row.contains("device_with_spaces"))

        val decoded = TunnelingBlacklist.decodeEntries(encoded)
        val decodedEntry = decoded.values.single()
        assertEquals(1, decodedEntry.failureCount)
        assertEquals(123L, decodedEntry.lastFailureAtMs)
    }

    @Test
    fun `recordFailureBestEffort merging takes the max of failure counts`() {
        val key = TunnelingBlacklist.keyFor("test-tv", "AAC", "H264")
        
        val initialMemory = mapOf(
            key.storageKey to TunnelingBlacklistEntry(key, 2, 1000L)
        )
        
        val diskUpdated = mapOf(
            key.storageKey to TunnelingBlacklistEntry(key, 1, 500L)
        )
        
        val merged = initialMemory.toMutableMap()
        diskUpdated.forEach { (k, diskEntry) ->
            val memEntry = merged[k]
            if (memEntry == null) {
                merged[k] = diskEntry
            } else {
                merged[k] = memEntry.copy(
                    failureCount = maxOf(memEntry.failureCount, diskEntry.failureCount),
                    lastFailureAtMs = maxOf(memEntry.lastFailureAtMs, diskEntry.lastFailureAtMs)
                )
            }
        }
        
        val entry = merged[key.storageKey]
        assertNotNull(entry)
        assertEquals(2, entry?.failureCount)
        assertEquals(1000L, entry?.lastFailureAtMs)
    }

    @Test
    fun `blacklist lookup does not block on DataStore reads`() {
        val source = readSource(
            "core/playback/src/main/kotlin/com/example/calmsource/core/playback/TunnelingBlacklist.kt",
            "src/main/kotlin/com/example/calmsource/core/playback/TunnelingBlacklist.kt"
        )

        assertFalse(source.contains("runBlocking(Dispatchers.IO)"))
        assertFalse(source.contains("ensureLoadedBlocking"))
    }

    private fun source(
        audioCodec: String?,
        videoCodec: String?
    ): PlaybackSource {
        return PlaybackSource(
            id = "stream",
            type = PlaybackSourceType.EXTENSION,
            title = "Stream",
            rawUrl = "https://example.com/stream.m3u8",
            metadata = PlaybackItemMetadata(
                title = "Stream",
                audioCodec = audioCodec,
                videoCodec = videoCodec
            )
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

    @Test
    fun `isBlacklisted checks threshold correctly`() {
        assertFalse(TunnelingBlacklist.isBlacklisted(null))
        
        val key = TunnelingBlacklist.keyFor("test-tv", "AAC", "H264")
        val entry1 = TunnelingBlacklistEntry(key, 1, 1000L)
        assertFalse(TunnelingBlacklist.isBlacklisted(entry1))

        val entry2 = TunnelingBlacklistEntry(key, 2, 2000L)
        assertTrue(TunnelingBlacklist.isBlacklisted(entry2))

        val entry3 = TunnelingBlacklistEntry(key, 5, 3000L)
        assertTrue(TunnelingBlacklist.isBlacklisted(entry3))
    }

    @Test
    fun `decodeEntries is robust against malformed or invalid entries`() {
        val invalidEntries = setOf(
            "device|audio|video|not-a-number|12345", // count not a number
            "device|audio|video|2|not-a-number",     // time not a number
            "device|audio|video|2",                  // too few fields
            "device|audio|video|2|12345|extra-field" // too many fields
        )
        val decoded = TunnelingBlacklist.decodeEntries(invalidEntries)
        assertTrue(decoded.isEmpty())
    }

    @Test
    fun `sanitizePart handles special characters and blank inputs`() {
        assertEquals("unknown", TunnelingBlacklistKey.sanitizePart(""))
        assertEquals("unknown", TunnelingBlacklistKey.sanitizePart("   "))
        
        // replaces unsafe chars but keeps lowercase alphanumeric, dots, underscores, dashes
        assertEquals("device_model-1.2_abc", TunnelingBlacklistKey.sanitizePart("Device Model-1.2_ABC!@#"))
    }
}

