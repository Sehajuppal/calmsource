package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.toUserMemoryReference

/** Smart-browsing view facets shared by mobile and TV live guides. */
enum class IptvLiveGuideView(val label: String, val section: IptvContentSection? = null) {
    LIVE("Live"),
    FAVORITES("Favorites"),
    RECENT("Recent"),
    SPORTS("Sports", IptvContentSection.SPORTS),
    MOVIES("Movies", IptvContentSection.MOVIES),
    NEWS("News", IptvContentSection.NEWS),
    KIDS("Kids", IptvContentSection.KIDS)
}

/** Sort modes shared by mobile and TV live guides. */
enum class IptvLiveGuideSort {
    RECOMMENDED,
    POPULAR,
    NAME,
    CATEGORY,
    LANGUAGE,
    RECENT
}

object IptvLiveGuideFilters {
    const val ALL_LANGUAGES = "All languages"
    const val ALL_REGIONS = "All regions"

    /**
     * Live guide should show a spinner only while channels are genuinely unavailable.
     * If live channels are already in memory/DB, show them even while catalog sync continues.
     */
    fun isGuideLoading(
        liveChannelCount: Int,
        channelsReady: Boolean,
        isProviderSyncing: Boolean,
        hasProviders: Boolean
    ): Boolean {
        if (liveChannelCount > 0) return false
        return isProviderSyncing || (!channelsReady && hasProviders)
    }

    fun filterAndSort(
        channels: List<Channel>,
        activeCategory: String,
        query: String,
        selectedView: IptvLiveGuideView,
        selectedLanguage: String,
        selectedCountry: String,
        sortMode: IptvLiveGuideSort,
        iptvChannelById: Map<String, IPTVChannel>,
        languageById: Map<String, String>,
        countryById: Map<String, String>,
        sectionById: Map<String, IptvContentSection>,
        favoriteKeys: Set<String>,
        recentOrder: Map<String, Int>
    ): List<Channel> {
        val categoryMatches = if (activeCategory == "All") {
            channels
        } else {
            channels.filter { it.category == activeCategory }
        }
        val normalizedQuery = query.trim()
        val visible = categoryMatches.filter { channel ->
            val memoryKey = iptvChannelById[channel.id]?.toUserMemoryReference()?.itemKey
            val matchesQuery = normalizedQuery.isEmpty() ||
                channel.name.contains(normalizedQuery, ignoreCase = true) ||
                channel.category.orEmpty().contains(normalizedQuery, ignoreCase = true)
            val matchesView = when (selectedView) {
                IptvLiveGuideView.LIVE -> true
                IptvLiveGuideView.FAVORITES -> memoryKey in favoriteKeys
                IptvLiveGuideView.RECENT -> memoryKey != null && recentOrder.containsKey(memoryKey)
                else -> sectionById[channel.id] == selectedView.section
            }
            val matchesLanguage = selectedLanguage == ALL_LANGUAGES ||
                languageById[channel.id].equals(selectedLanguage, ignoreCase = true)
            val matchesCountry = selectedCountry == ALL_REGIONS ||
                countryById[channel.id].equals(selectedCountry, ignoreCase = true)
            matchesQuery && matchesView && matchesLanguage && matchesCountry
        }
        return sortChannels(
            visible = visible,
            sortMode = sortMode,
            selectedView = selectedView,
            iptvChannelById = iptvChannelById,
            languageById = languageById,
            favoriteKeys = favoriteKeys,
            recentOrder = recentOrder
        )
    }

    private fun sortChannels(
        visible: List<Channel>,
        sortMode: IptvLiveGuideSort,
        selectedView: IptvLiveGuideView,
        iptvChannelById: Map<String, IPTVChannel>,
        languageById: Map<String, String>,
        favoriteKeys: Set<String>,
        recentOrder: Map<String, Int>
    ): List<Channel> = when (sortMode) {
        IptvLiveGuideSort.RECOMMENDED -> if (selectedView == IptvLiveGuideView.RECENT) {
            visible.sortedBy { channel ->
                val memoryKey = iptvChannelById[channel.id]?.toUserMemoryReference()?.itemKey
                recentOrder[memoryKey] ?: Int.MAX_VALUE
            }
        } else {
            visible
        }
        IptvLiveGuideSort.POPULAR -> visible.sortedWith(
            compareByDescending<Channel> {
                val memoryKey = iptvChannelById[it.id]?.toUserMemoryReference()?.itemKey
                memoryKey in favoriteKeys
            }.thenBy {
                val memoryKey = iptvChannelById[it.id]?.toUserMemoryReference()?.itemKey
                recentOrder[memoryKey] ?: Int.MAX_VALUE
            }.thenBy { it.name.lowercase() }
        )
        IptvLiveGuideSort.NAME -> visible.sortedBy { it.name.lowercase() }
        IptvLiveGuideSort.CATEGORY -> visible.sortedWith(
            compareBy<Channel> { it.category.orEmpty().lowercase() }
                .thenBy { it.name.lowercase() }
        )
        IptvLiveGuideSort.LANGUAGE -> visible.sortedWith(
            compareBy<Channel> { languageById[it.id].orEmpty().lowercase() }
                .thenBy { it.name.lowercase() }
        )
        IptvLiveGuideSort.RECENT -> visible.sortedBy { channel ->
            val memoryKey = iptvChannelById[channel.id]?.toUserMemoryReference()?.itemKey
            recentOrder[memoryKey] ?: Int.MAX_VALUE
        }
    }
}
