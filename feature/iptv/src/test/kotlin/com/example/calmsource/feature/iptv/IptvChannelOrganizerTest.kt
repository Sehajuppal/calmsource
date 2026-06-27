package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.model.SourceHealth
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IptvChannelOrganizerTest {
    @Test
    fun `preferred language hides detected mismatches but keeps unknown channels`() {
        val english = channel("english", "News One", language = "English")
        val spanish = channel("spanish", "Noticias Uno", language = "Spanish")
        val unknown = channel("unknown", "World News")

        val result = IptvChannelOrganizer.organize(
            listOf(english, spanish, unknown),
            IptvOptimizationPreferences(preferredLanguages = setOf("English"))
        )

        assertEquals(setOf("english", "unknown"), result.channels.map { it.id }.toSet())
        assertEquals(1, result.stats.languageHidden)
    }

    @Test
    fun `duplicates prefer healthy high quality channel`() {
        val standard = channel("standard", "Sports Network SD")
        val highDefinition = channel("hd", "Sports Network FHD")
        val health = mapOf(
            standard.safeSourceId to SourceHealth(
                sourceId = standard.safeSourceId,
                providerId = standard.providerId,
                sourceType = PlaybackSourceType.IPTV,
                healthScore = 40
            ),
            highDefinition.safeSourceId to SourceHealth(
                sourceId = highDefinition.safeSourceId,
                providerId = highDefinition.providerId,
                sourceType = PlaybackSourceType.IPTV,
                healthScore = 90
            )
        )

        val result = IptvChannelOrganizer.organize(
            listOf(standard, highDefinition),
            IptvOptimizationPreferences(removeDuplicates = true),
            health
        )

        assertEquals(listOf("hd"), result.channels.map { it.id })
        assertEquals(1, result.stats.duplicatesRemoved)
    }

    @Test
    fun `adult and unsupported channels are hidden and reported`() {
        val adult = channel("adult", "Adult Movies")
        val broken = channel("broken", "Local News")
        val healthy = channel("healthy", "Family News")
        val health = mapOf(
            broken.safeSourceId to SourceHealth(
                sourceId = broken.safeSourceId,
                providerId = broken.providerId,
                sourceType = PlaybackSourceType.IPTV,
                healthScore = 0
            )
        )

        val result = IptvChannelOrganizer.organize(
            listOf(adult, broken, healthy),
            IptvOptimizationPreferences(),
            health
        )

        assertEquals(listOf("healthy"), result.channels.map { it.id })
        assertEquals(1, result.stats.adultHidden)
        assertEquals(1, result.stats.unsupportedHidden)
        assertTrue(result.stats.visibleCount < result.stats.inputCount)
    }

    @Test
    fun `favorite smart category prioritizes prefixed provider groups`() {
        val general = channel("general", "General Network")
        val sports = channel("sports", "ESPN HD").copy(groupTitle = "US Sports")

        val result = IptvChannelOrganizer.organize(
            listOf(general, sports),
            IptvOptimizationPreferences(favoriteCategories = setOf("Sports"))
        )

        assertEquals("sports", result.channels.first().id)
    }

    private fun channel(
        id: String,
        name: String,
        language: String = ""
    ): IPTVChannel {
        return IPTVChannel(
            id = id,
            tvgId = null,
            tvgName = name,
            tvgLogo = null,
            groupTitle = "General",
            name = name,
            streamUrl = "https://example.com/$id.m3u8",
            providerId = "provider",
            rawAttributes = if (language.isEmpty()) emptyMap() else mapOf("tvg-language" to language)
        )
    }
}
