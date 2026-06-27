package com.example.calmsource.core.playback

import coil.memory.MemoryCache
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ImageCacheControllerTest {
    @Test
    fun `playback target is twenty five percent of memory cache max size`() {
        assertEquals(250, ImageCacheController.playbackTargetSizeBytes(1_000))
        assertEquals(1, ImageCacheController.playbackTargetSizeBytes(1))
        assertEquals(0, ImageCacheController.playbackTargetSizeBytes(0))
    }

    @Test
    fun `trim removes non critical entries until cache reaches playback target`() {
        val cache = FakeMemoryCache(
            maxSizeBytes = 1_000,
            entries = linkedMapOf(
                MemoryCache.Key("poster-1") to 200,
                MemoryCache.Key("poster-2") to 200,
                MemoryCache.Key("backdrop-1") to 200,
                MemoryCache.Key("backdrop-2") to 200,
                MemoryCache.Key("logo-1") to 200
            )
        )

        val result = ImageCacheController.trimMemoryCacheForPlayback(cache)

        assertEquals(1_000, result.beforeSizeBytes)
        assertEquals(250, result.targetSizeBytes)
        assertEquals(200, result.afterSizeBytes)
        assertEquals(4, result.removedEntries)
        assertEquals(setOf(MemoryCache.Key("logo-1")), cache.keys)
    }

    @Test
    fun `trim preserves player control cache keys`() {
        val protectedKey = MemoryCache.Key("player-control-scrubber")
        val cache = FakeMemoryCache(
            maxSizeBytes = 1_000,
            entries = linkedMapOf(
                protectedKey to 500,
                MemoryCache.Key("poster-1") to 300,
                MemoryCache.Key("poster-2") to 300
            )
        )

        val result = ImageCacheController.trimMemoryCacheForPlayback(cache)

        assertEquals(2, result.removedEntries)
        assertEquals(1, result.protectedEntries)
        assertEquals(500, result.afterSizeBytes)
        assertTrue(cache.keys.contains(protectedKey))
        assertFalse(cache.keys.contains(MemoryCache.Key("poster-1")))
    }

    @Test
    fun `playback manager wires image cache trim and delayed restore`() {
        val manager = readSource(
            "core/playback/src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt",
            "src/main/kotlin/com/example/calmsource/core/playback/PlaybackManager.kt"
        )
        val buildFile = readSource(
            "core/playback/build.gradle.kts",
            "build.gradle.kts"
        )

        assertTrue(manager.contains("ImageCacheController.trimForPlayback(context)"))
        assertTrue(manager.contains("ImageCacheController.scheduleRestoreAfterPlayback(context)"))
        assertTrue(manager.contains("scheduleImageCacheRestore()"))
        assertTrue(buildFile.contains("api(libs.coil.core)"))
    }

    private class FakeMemoryCache(
        private val maxSizeBytes: Int,
        entries: LinkedHashMap<MemoryCache.Key, Int>
    ) : MemoryCache {
        private val entrySizes = LinkedHashMap(entries)

        override val size: Int
            get() = entrySizes.values.sum()

        override val maxSize: Int
            get() = maxSizeBytes

        override val keys: Set<MemoryCache.Key>
            get() = entrySizes.keys

        override fun get(key: MemoryCache.Key): MemoryCache.Value? = null

        override fun set(key: MemoryCache.Key, value: MemoryCache.Value) = Unit

        override fun remove(key: MemoryCache.Key): Boolean {
            return entrySizes.remove(key) != null
        }

        override fun clear() {
            entrySizes.clear()
        }

        override fun trimMemory(level: Int) = Unit
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
