package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.Channel
import com.example.calmsource.core.model.IPTVChannel

/**
 * Precomputed live-guide metadata for mobile and TV channel lists.
 *
 * Built on a background thread whenever [IPTVRepository] updates its channel
 * cache so screens do not re-scan thousands of channels on every navigation.
 */
data class IptvLiveGuideIndex(
    val liveChannels: List<IPTVChannel>,
    val uiChannels: List<Channel>,
    val categories: List<String>,
    val iptvChannelById: Map<String, IPTVChannel>,
    val languageById: Map<String, String>,
    val countryById: Map<String, String>,
    val sectionById: Map<String, IptvContentSection>,
    val languages: List<String>,
    val countries: List<String>
) {
    companion object {
        val EMPTY = IptvLiveGuideIndex(
            liveChannels = emptyList(),
            uiChannels = emptyList(),
            categories = listOf("All"),
            iptvChannelById = emptyMap(),
            languageById = emptyMap(),
            countryById = emptyMap(),
            sectionById = emptyMap(),
            languages = emptyList(),
            countries = emptyList()
        )

        fun buildFromChannels(
            channels: List<IPTVChannel>,
            lightweight: Boolean = false
        ): IptvLiveGuideIndex {
            if (channels.isEmpty()) return EMPTY

            val liveChannels = ArrayList<IPTVChannel>(channels.size)
            val uiChannels = ArrayList<Channel>(channels.size)
            val iptvChannelById = HashMap<String, IPTVChannel>(channels.size)
            val languageById = HashMap<String, String>(channels.size)
            val countryById = HashMap<String, String>(channels.size)
            val sectionById = HashMap<String, IptvContentSection>(channels.size)
            val languageSet = LinkedHashSet<String>()
            val countrySet = LinkedHashSet<String>()
            val categorySet = LinkedHashSet<String>()

            for (channel in channels) {
                if (channel.isVod) continue
                liveChannels.add(channel)
                iptvChannelById[channel.id] = channel

                val category = channel.groupTitle ?: "General"
                categorySet.add(category)
                uiChannels.add(
                    Channel(
                        id = channel.id,
                        name = channel.name,
                        logoUrl = channel.tvgLogo,
                        streamUrl = channel.streamUrl,
                        category = category
                    )
                )

                val language = channel.language?.takeIf { it.isNotBlank() }
                    ?: if (lightweight) "" else IptvChannelOrganizer.detectLanguage(channel)
                val country = channel.country?.takeIf { it.isNotBlank() }
                    ?: if (lightweight) "" else IptvChannelOrganizer.detectCountry(channel)
                val section = if (lightweight) {
                    IptvContentSection.OTHER
                } else {
                    IptvChannelFacets.contentSection(channel)
                }

                languageById[channel.id] = language
                countryById[channel.id] = country
                sectionById[channel.id] = section

                if (language.isNotBlank()) {
                    languageSet.add(language)
                }
                if (country.isNotBlank()) {
                    countrySet.add(country)
                }
            }

            if (liveChannels.isEmpty()) return EMPTY

            return IptvLiveGuideIndex(
                liveChannels = liveChannels,
                uiChannels = uiChannels,
                categories = listOf("All") + categorySet.sorted(),
                iptvChannelById = iptvChannelById,
                languageById = languageById,
                countryById = countryById,
                sectionById = sectionById,
                languages = languageSet.sortedBy { it.lowercase() },
                countries = countrySet.sortedBy { it.lowercase() }
            )
        }

        /** Fast channel list for progressive live-guide rendering before facets finish. */
        fun lightweightUiChannels(channels: List<IPTVChannel>): List<Channel> {
            if (channels.isEmpty()) return emptyList()
            val uiChannels = ArrayList<Channel>(channels.size)
            for (channel in channels) {
                if (channel.isVod) continue
                uiChannels.add(
                    Channel(
                        id = channel.id,
                        name = channel.name,
                        logoUrl = channel.tvgLogo,
                        streamUrl = channel.streamUrl,
                        category = channel.groupTitle ?: "General"
                    )
                )
            }
            return uiChannels
        }
    }
}
