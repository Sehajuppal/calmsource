package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLiveGuideIndexTest {
    @Test
    fun `buildFromChannels skips vod and indexes live metadata`() {
        val live = liveChannel("live-1", "BBC One", language = "English", country = "UK")
        val vod = vodChannel("vod-1", "Action Movie")

        val index = IptvLiveGuideIndex.buildFromChannels(listOf(live, vod))

        assertEquals(listOf("live-1"), index.liveChannels.map { it.id })
        assertEquals(live, index.iptvChannelById["live-1"])
        assertEquals("English", index.languageById["live-1"])
        assertEquals("UK", index.countryById["live-1"])
        assertTrue(index.sectionById["live-1"] != null)
        assertEquals(listOf("English"), index.languages)
        assertEquals(listOf("UK"), index.countries)
        assertEquals(1, index.uiChannels.size)
        assertEquals("BBC One", index.uiChannels.first().name)
        assertEquals(listOf("All", "Entertainment"), index.categories)
    }

    @Test
    fun `buildFromChannels returns empty index for blank input`() {
        assertEquals(IptvLiveGuideIndex.EMPTY, IptvLiveGuideIndex.buildFromChannels(emptyList()))
        assertEquals(IptvLiveGuideIndex.EMPTY, IptvLiveGuideIndex.buildFromChannels(listOf(vodChannel("vod-1", "Movie"))))
    }

    @Test
    fun `lightweight build skips language detection heuristics`() {
        val live = liveChannel("live-1", "Canal de Noticias")

        val index = IptvLiveGuideIndex.buildFromChannels(listOf(live), lightweight = true)

        assertEquals("", index.languageById["live-1"])
        assertEquals(IptvContentSection.OTHER, index.sectionById["live-1"])
    }

    private fun liveChannel(
        id: String,
        name: String,
        language: String? = null,
        country: String? = null
    ): IPTVChannel = IPTVChannel(
        id = id,
        tvgId = null,
        tvgName = name,
        tvgLogo = null,
        groupTitle = "Entertainment",
        name = name,
        streamUrl = "xtream://stream_id/provider/$id",
        providerId = "provider",
        rawAttributes = mapOf("xtream_content_type" to "live"),
        language = language,
        country = country
    )

    private fun vodChannel(id: String, name: String): IPTVChannel = IPTVChannel(
        id = id,
        tvgId = null,
        tvgName = name,
        tvgLogo = null,
        groupTitle = "Movies",
        name = name,
        streamUrl = "xtream://stream_id/provider/$id/movie",
        providerId = "provider",
        rawAttributes = mapOf("xtream_content_type" to "vod")
    )
}
