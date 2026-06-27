package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.toUserMemoryReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvLiveGuideFiltersTest {
    @Test
    fun `favorites facet filters to favorited channels only`() {
        val sports = channel("sports-1", "ESPN", "Sports")
        val news = channel("news-1", "CNN", "News")
        val channels = listOf(
            uiChannel(sports),
            uiChannel(news)
        )
        val iptvById = mapOf(sports.id to sports, news.id to news)

        val filtered = IptvLiveGuideFilters.filterAndSort(
            channels = channels,
            activeCategory = "All",
            query = "",
            selectedView = IptvLiveGuideView.FAVORITES,
            selectedLanguage = IptvLiveGuideFilters.ALL_LANGUAGES,
            selectedCountry = IptvLiveGuideFilters.ALL_REGIONS,
            sortMode = IptvLiveGuideSort.RECOMMENDED,
            iptvChannelById = iptvById,
            languageById = emptyMap(),
            countryById = emptyMap(),
            sectionById = mapOf(
                sports.id to IptvContentSection.SPORTS,
                news.id to IptvContentSection.NEWS
            ),
            favoriteKeys = setOf(sports.toUserMemoryReference().itemKey),
            recentOrder = emptyMap()
        )

        assertEquals(listOf("sports-1"), filtered.map { it.id })
    }

    @Test
    fun `search query matches channel name and category`() {
        val channelA = channel("a", "Sky Sports F1", "Sports")
        val channelB = channel("b", "BBC One", "Entertainment")
        val channels = listOf(uiChannel(channelA), uiChannel(channelB))

        val byName = IptvLiveGuideFilters.filterAndSort(
            channels = channels,
            activeCategory = "All",
            query = "f1",
            selectedView = IptvLiveGuideView.LIVE,
            selectedLanguage = IptvLiveGuideFilters.ALL_LANGUAGES,
            selectedCountry = IptvLiveGuideFilters.ALL_REGIONS,
            sortMode = IptvLiveGuideSort.RECOMMENDED,
            iptvChannelById = channels.associate { it.id to channel(it.id, it.name, it.category ?: "General") },
            languageById = emptyMap(),
            countryById = emptyMap(),
            sectionById = emptyMap(),
            favoriteKeys = emptySet(),
            recentOrder = emptyMap()
        )
        assertEquals(listOf("a"), byName.map { it.id })

        val byCategory = IptvLiveGuideFilters.filterAndSort(
            channels = channels,
            activeCategory = "All",
            query = "entertainment",
            selectedView = IptvLiveGuideView.LIVE,
            selectedLanguage = IptvLiveGuideFilters.ALL_LANGUAGES,
            selectedCountry = IptvLiveGuideFilters.ALL_REGIONS,
            sortMode = IptvLiveGuideSort.RECOMMENDED,
            iptvChannelById = channels.associate { it.id to channel(it.id, it.name, it.category ?: "General") },
            languageById = emptyMap(),
            countryById = emptyMap(),
            sectionById = emptyMap(),
            favoriteKeys = emptySet(),
            recentOrder = emptyMap()
        )
        assertEquals(listOf("b"), byCategory.map { it.id })
    }

    @Test
    fun `shared facet labels cover mobile and TV smart browsing`() {
        val labels = IptvLiveGuideView.entries.map { it.label }
        listOf("Live", "Favorites", "Recent", "Sports", "Movies", "News", "Kids").forEach { label ->
            assertTrue("Missing facet label: $label", label in labels)
        }
    }

    @Test
    fun `isGuideLoading returns false when live channels already exist during sync`() {
        assertFalse(
            IptvLiveGuideFilters.isGuideLoading(
                liveChannelCount = 12,
                channelsReady = false,
                isProviderSyncing = true,
                hasProviders = true
            )
        )
    }

    @Test
    fun `isGuideLoading stays true while syncing with no channels yet`() {
        assertTrue(
            IptvLiveGuideFilters.isGuideLoading(
                liveChannelCount = 0,
                channelsReady = false,
                isProviderSyncing = true,
                hasProviders = true
            )
        )
    }

    private fun channel(id: String, name: String, group: String): IPTVChannel = IPTVChannel(
        id = id,
        tvgId = null,
        tvgName = name,
        tvgLogo = null,
        groupTitle = group,
        name = name,
        streamUrl = "xtream://stream_id/provider/$id",
        providerId = "provider"
    )

    private fun uiChannel(channel: IPTVChannel): Channel = Channel(
        id = channel.id,
        name = channel.name,
        logoUrl = channel.tvgLogo,
        streamUrl = channel.streamUrl,
        category = channel.groupTitle
    )
}
