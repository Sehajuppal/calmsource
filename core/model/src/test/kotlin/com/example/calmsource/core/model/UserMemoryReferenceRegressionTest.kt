package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserMemoryReferenceRegressionTest {

    @Test
    fun `live and VOD channels create distinct safe reference types without playback URLs`() {
        val live = channel(
            id = "live-channel-42",
            name = "News Live",
            groupTitle = "News",
            streamUrl = "xtream://stream_id/42",
            contentType = "live"
        )
        val vod = channel(
            id = "vod-item-42",
            name = "Evening News Replay",
            groupTitle = "Movies",
            streamUrl = "xtream://stream_id/4242",
            contentType = "vod"
        )

        val liveReference = live.toUserMemoryReference()
        val vodReference = vod.toUserMemoryReference()

        assertEquals(UserMemoryContentType.LIVE_CHANNEL, liveReference.contentType)
        assertEquals(UserMemoryContentType.VOD, vodReference.contentType)
        assertEquals(live.id, liveReference.sourceId)
        assertEquals(vod.id, vodReference.sourceId)
        assertNotEquals(liveReference.itemKey, vodReference.itemKey)

        listOf(liveReference, vodReference).forEach { reference ->
            assertTrue(reference.itemKey.startsWith("channel-"))
            assertFalse(reference.toString().contains("xtream://"))
            assertFalse(reference.toString().contains("stream_id"))
        }
    }

    @Test
    fun `explicit Xtream content type wins over misleading URL and group hints`() {
        val liveReference = channel(
            id = "live-with-movie-path",
            name = "Cinema Live",
            groupTitle = "Movies",
            streamUrl = "https://example.invalid/movie/live-feed.ts",
            contentType = "live"
        ).toUserMemoryReference()
        val vodReference = channel(
            id = "vod-with-live-path",
            name = "Recorded Event",
            groupTitle = "Sports",
            streamUrl = "https://example.invalid/live/replay.ts",
            contentType = "vod"
        ).toUserMemoryReference()

        assertEquals(UserMemoryContentType.LIVE_CHANNEL, liveReference.contentType)
        assertEquals(UserMemoryContentType.VOD, vodReference.contentType)
    }

    private fun channel(
        id: String,
        name: String,
        groupTitle: String,
        streamUrl: String,
        contentType: String
    ): IPTVChannel {
        return IPTVChannel(
            id = id,
            tvgId = id,
            tvgName = name,
            tvgLogo = null,
            groupTitle = groupTitle,
            name = name,
            streamUrl = streamUrl,
            providerId = "provider-42",
            rawAttributes = mapOf("xtream_content_type" to contentType)
        )
    }
}
