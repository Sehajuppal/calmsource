package com.example.calmsource.core.playback

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackPreparationTest {

    private val xtreamSource = PlaybackSource(
        id = "chan1",
        type = PlaybackSourceType.IPTV,
        title = "Xtream Channel",
        rawUrl = "xtream://live/123"
    )

    private val magnetSource = PlaybackSource(
        id = "mag1",
        type = PlaybackSourceType.STREMIO,
        title = "Magnet Stream",
        rawUrl = "magnet:?xt=urn:btih:hash123"
    )

    private val regularSource = PlaybackSource(
        id = "reg1",
        type = PlaybackSourceType.IPTV,
        title = "Regular Stream",
        rawUrl = "https://example.com/stream.m3u8"
    )

    @Test
    fun `resolveSourceUrl with regular URL returns unchanged`() = runTest {
        val resolved = resolveSourceUrl(
            regularSource,
            resolveXtream = { _ -> "resolved_xtream" },
            resolveMagnet = { _ -> "resolved_magnet" }
        )
        assertNotNull(resolved)
        assertEquals(regularSource.rawUrl, resolved?.rawUrl)
    }

    @Test
    fun `resolveSourceUrl with xtream URL calls resolveXtream and returns resolved URL`() = runTest {
        val resolved = resolveSourceUrl(
            xtreamSource,
            resolveXtream = { source -> "http://server.com/live/user/pass/${source.id}.ts" },
            resolveMagnet = { _ -> null }
        )
        assertNotNull(resolved)
        assertEquals("http://server.com/live/user/pass/chan1.ts", resolved?.rawUrl)
    }

    @Test
    fun `resolveSourceUrl with magnet URL calls resolveMagnet and returns resolved URL`() = runTest {
        val resolved = resolveSourceUrl(
            magnetSource,
            resolveXtream = { _ -> null },
            resolveMagnet = { source -> "http://debrid.com/stream/${source.id}" }
        )
        assertNotNull(resolved)
        assertEquals("http://debrid.com/stream/mag1", resolved?.rawUrl)
    }

    @Test
    fun `resolveSourceUrl with magnet URL returns null when resolveMagnet returns null`() = runTest {
        val resolved = resolveSourceUrl(
            magnetSource,
            resolveXtream = { _ -> null },
            resolveMagnet = { _ -> null }
        )
        assertNull(resolved)
    }

    @Test
    fun `resolvePlaybackRequest resolves xtream and updates the request`() = runTest {
        val request = PlaybackRequest(source = xtreamSource)
        val resolvedRequest = resolvePlaybackRequest(
            request,
            resolveXtream = { _ -> "http://resolved.xtream" },
            resolveMagnet = { _ -> null }
        )
        assertNotNull(resolvedRequest)
        assertEquals("http://resolved.xtream", resolvedRequest?.source?.rawUrl)
    }

    @Test
    fun `resolvePlaybackRequest returns null when magnet resolution fails`() = runTest {
        val request = PlaybackRequest(source = magnetSource)
        val resolvedRequest = resolvePlaybackRequest(
            request,
            resolveXtream = { _ -> null },
            resolveMagnet = { _ -> null }
        )
        assertNull(resolvedRequest)
    }

    @Test
    fun `resolveSourceUrl with xtream URL returns null when resolveXtream returns null`() = runTest {
        val resolved = resolveSourceUrl(
            xtreamSource,
            resolveXtream = { _ -> null },
            resolveMagnet = { _ -> null }
        )
        assertNull(resolved)
    }

    @Test
    fun `resolveSourceUrl with xtream URL returns null when resolveXtream returns unresolved xtream pseudo-URL`() = runTest {
        val resolved = resolveSourceUrl(
            xtreamSource,
            resolveXtream = { _ -> "xtream://live/still_unresolved" },
            resolveMagnet = { _ -> null }
        )
        assertNull(resolved)
    }

    @Test
    fun `fallback filtering drops unresolved xtream fallbacks`() = runTest {
        val fallbackSources = listOf(
            regularSource,
            xtreamSource,
            magnetSource
        )
        val resolvedFallbacks = fallbackSources.mapNotNull { source ->
            val resolved = resolveSourceUrl(
                source,
                resolveXtream = { _ -> null }, // Fail Xtream resolution
                resolveMagnet = { _ -> "http://debrid.com/resolved" } // Resolve magnet
            )
            if (resolved != null && resolved.rawUrl.startsWith("xtream://")) null else resolved
        }
        assertEquals(2, resolvedFallbacks.size)
        assertEquals(regularSource.rawUrl, resolvedFallbacks[0].rawUrl)
        assertEquals("http://debrid.com/resolved", resolvedFallbacks[1].rawUrl)
    }

    @Test
    fun `isResolvedPlaybackUrlInvalid flags null empty and unresolved xtream`() {
        assertEquals(true, isResolvedPlaybackUrlInvalid(null))
        assertEquals(true, isResolvedPlaybackUrlInvalid(PlaybackRequest(source = regularSource.copy(rawUrl = ""))))
        assertEquals(true, isResolvedPlaybackUrlInvalid(PlaybackRequest(source = xtreamSource)))
        assertEquals(false, isResolvedPlaybackUrlInvalid(PlaybackRequest(source = regularSource)))
    }

    @Test
    fun `mergeFallbackIdentityPreservingPseudoUrl keeps pseudo url and headers`() {
        val request = PlaybackRequest(
            source = xtreamSource.copy(headers = mapOf("X-Test" to "1"))
        )
        val fallback = xtreamSource.copy(
            id = "chan1-alt-m3u8",
            rawUrl = "http://credentialed.example/live.ts",
            headers = mapOf("Authorization" to "secret")
        )
        val merged = mergeFallbackIdentityPreservingPseudoUrl(request, fallback)
        assertEquals("chan1-alt-m3u8", merged.source.id)
        assertEquals("xtream://live/123", merged.source.rawUrl)
        assertEquals(mapOf("X-Test" to "1"), merged.source.headers)
    }

    @Test
    fun `selectAutoLiveFallbackCandidates uses explicit fallbacks when present`() {
        val explicit = listOf(regularSource)
        val result = selectAutoLiveFallbackCandidates(
            explicitFallbacks = explicit,
            currentSourceId = "chan1",
            findChannel = { null },
            buildLiveFallbackSources = { emptyList() }
        )
        assertEquals(explicit, result)
    }

    @Test
    fun `selectAutoLiveFallbackCandidates builds live fallbacks for guide channel`() {
        val channel = IPTVChannel(
            id = "live-1",
            tvgId = "live-1",
            tvgName = "Live 1",
            tvgLogo = null,
            groupTitle = "News",
            name = "Live 1",
            streamUrl = "xtream://stream_id/1",
            providerId = "p1"
        )
        val alt = PlaybackSource(
            id = "live-1-alt-m3u8",
            type = PlaybackSourceType.IPTV,
            title = "Live 1",
            rawUrl = channel.streamUrl
        )
        val result = selectAutoLiveFallbackCandidates(
            explicitFallbacks = emptyList(),
            currentSourceId = "live-1",
            findChannel = { id -> if (id == "live-1") channel else null },
            buildLiveFallbackSources = { listOf(alt) }
        )
        assertEquals(listOf(alt), result)
    }

    @Test
    fun `selectAutoLiveFallbackCandidates resolves alt id to base channel`() {
        val channel = IPTVChannel(
            id = "live-1",
            tvgId = "live-1",
            tvgName = "Live 1",
            tvgLogo = null,
            groupTitle = "News",
            name = "Live 1",
            streamUrl = "xtream://stream_id/1",
            providerId = "p1"
        )
        val alt = PlaybackSource(
            id = "live-1-alt-m3u8",
            type = PlaybackSourceType.IPTV,
            title = "Live 1",
            rawUrl = channel.streamUrl
        )
        val result = selectAutoLiveFallbackCandidates(
            explicitFallbacks = emptyList(),
            currentSourceId = "live-1-alt-m3u8",
            findChannel = { id -> if (id == "live-1") channel else null },
            buildLiveFallbackSources = { listOf(alt) }
        )
        assertEquals(listOf(alt), result)
    }
}

