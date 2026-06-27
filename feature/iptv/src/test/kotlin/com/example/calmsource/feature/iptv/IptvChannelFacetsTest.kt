package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.assertEquals
import org.junit.Test

class IptvChannelFacetsTest {
    @Test
    fun `classifies common live channel sections`() {
        assertEquals(IptvContentSection.SPORTS, IptvChannelFacets.contentSection(channel("ESPN HD")))
        assertEquals(IptvContentSection.MOVIES, IptvChannelFacets.contentSection(channel("Classic Cinema")))
        assertEquals(IptvContentSection.NEWS, IptvChannelFacets.contentSection(channel("BBC News")))
        assertEquals(IptvContentSection.KIDS, IptvChannelFacets.contentSection(channel("Disney Kids")))
    }

    @Test
    fun `short language code words in names do not create false language matches`() {
        assertEquals("", IptvChannelOrganizer.detectLanguage(channel("Canal de Noticias")))
        assertEquals(
            "German",
            IptvChannelOrganizer.detectLanguage(
                channel("Das Erste", attributes = mapOf("tvg-language" to "de"))
            )
        )
    }

    @Test
    fun `detects country from explicit metadata and delimited Xtream categories`() {
        assertEquals(
            "United States",
            IptvChannelOrganizer.detectCountry(channel("Local News", group = "US | News"))
        )
        assertEquals(
            "United States",
            IptvChannelOrganizer.detectCountry(channel("ESPN", group = "US Sports"))
        )
        assertEquals(
            "United Kingdom",
            IptvChannelOrganizer.detectCountry(
                channel("BBC One", attributes = mapOf("tvg-country" to "GB"))
            )
        )
        assertEquals("", IptvChannelOrganizer.detectCountry(channel("In the Night")))
    }

    private fun channel(
        name: String,
        group: String = "General",
        attributes: Map<String, String> = emptyMap()
    ): IPTVChannel {
        return IPTVChannel(
            id = name,
            tvgId = null,
            tvgName = name,
            tvgLogo = null,
            groupTitle = group,
            name = name,
            streamUrl = "https://example.com/${name.hashCode()}.m3u8",
            providerId = "provider",
            rawAttributes = attributes
        )
    }
}
