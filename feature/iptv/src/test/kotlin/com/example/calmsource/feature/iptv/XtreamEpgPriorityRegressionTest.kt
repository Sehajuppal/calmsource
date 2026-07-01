package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamEpgPriorityRegressionTest {

    @Test
    fun `recent and favorite source ids are selected before ordinary tvg channels`() {
        val ordinary = channel("ordinary", "ordinary.epg")
        val favorite = channel("favorite", null)
        val recent = channel("recent", "recent.epg")

        val selected = XtreamRepository.selectChannelsForEpgSync(
            channels = listOf(ordinary, favorite, recent),
            prioritizedChannelIds = setOf(favorite.id, recent.id),
            maxChannels = 2
        )

        assertEquals(listOf("favorite", "recent"), selected.map { it.id })
    }

    private fun channel(id: String, tvgId: String?) = IPTVChannel(
        id = id,
        tvgId = tvgId,
        name = id,
        streamUrl = "xtream://stream_id/provider/$id",
        providerId = "provider",
        rawAttributes = mapOf("xtream_stream_id" to id)
    )
}
