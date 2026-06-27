package com.example.calmsource.core.parser

import com.example.calmsource.core.model.ChannelMapper
import com.example.calmsource.core.model.IPTVChannel
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for [ChannelMapper]'s language and resolution extraction.
 *
 * Verifies that bugs #39 (hardcoded "Hindi") and #40 (hardcoded "1080p")
 * are properly fixed by extracting values from M3U attributes and channel names.
 */
class ChannelMapperExtractionTest {

    private fun makeChannel(
        name: String = "Test Channel",
        tvgName: String? = null,
        rawAttributes: Map<String, String> = emptyMap()
    ) = IPTVChannel(
        id = "test-id",
        tvgId = "test-tvg",
        tvgName = tvgName,
        tvgLogo = null,
        groupTitle = null,
        name = name,
        streamUrl = "http://test.com/stream",
        providerId = "test-provider",
        rawAttributes = rawAttributes
    )

    // ---- Language extraction tests (Bug #39) ----

    @Test
    fun `extractLanguage returns tvg-language when present`() {
        val channel = makeChannel(rawAttributes = mapOf("tvg-language" to "Hindi"))
        assertEquals("Hindi", ChannelMapper.extractLanguage(channel))
    }

    @Test
    fun `extractLanguage returns tvg-language for English`() {
        val channel = makeChannel(rawAttributes = mapOf("tvg-language" to "English"))
        assertEquals("English", ChannelMapper.extractLanguage(channel))
    }

    @Test
    fun `extractLanguage returns empty string when no tvg-language`() {
        val channel = makeChannel(rawAttributes = emptyMap())
        assertEquals("", ChannelMapper.extractLanguage(channel))
    }

    @Test
    fun `extractLanguage trims whitespace from tvg-language`() {
        val channel = makeChannel(rawAttributes = mapOf("tvg-language" to "  Hindi  "))
        assertEquals("Hindi", ChannelMapper.extractLanguage(channel))
    }

    @Test
    fun `extractLanguage returns empty string for blank tvg-language`() {
        val channel = makeChannel(rawAttributes = mapOf("tvg-language" to "   "))
        assertEquals("", ChannelMapper.extractLanguage(channel))
    }

    @Test
    fun `extractLanguage does NOT hardcode Hindi`() {
        // This is the core regression test for bug #39
        val channel = makeChannel(name = "Sony TV", rawAttributes = emptyMap())
        assertNotEquals("Hindi", ChannelMapper.extractLanguage(channel))
        assertEquals("", ChannelMapper.extractLanguage(channel))
    }

    // ---- Resolution extraction tests (Bug #40) ----

    @Test
    fun `extractResolution detects 4K from channel name`() {
        val channel = makeChannel(name = "Discovery 4K")
        assertEquals("4K", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects UHD from channel name`() {
        val channel = makeChannel(name = "Nature UHD")
        assertEquals("4K", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects 2160p from channel name`() {
        val channel = makeChannel(name = "Movie 2160p")
        assertEquals("4K", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects FHD from channel name`() {
        val channel = makeChannel(name = "Sony FHD")
        assertEquals("1080p", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects 1080p from channel name`() {
        val channel = makeChannel(name = "Star Sports 1080p")
        assertEquals("1080p", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects HD from channel name`() {
        val channel = makeChannel(name = "Star Sports HD")
        assertEquals("720p", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects 720p from channel name`() {
        val channel = makeChannel(name = "Sports 720p")
        assertEquals("720p", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects SD from channel name`() {
        val channel = makeChannel(name = "Colors SD")
        assertEquals("SD", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution detects 480p from channel name`() {
        val channel = makeChannel(name = "Channel 480p")
        assertEquals("SD", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution returns empty for unknown resolution`() {
        val channel = makeChannel(name = "Sony Entertainment")
        assertEquals("", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution does NOT hardcode 1080p`() {
        // This is the core regression test for bug #40
        val channel = makeChannel(name = "Simple Channel")
        assertNotEquals("1080p", ChannelMapper.extractResolution(channel))
        assertEquals("", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution checks tvgName as well`() {
        val channel = makeChannel(name = "Some Channel", tvgName = "Some Channel HD")
        assertEquals("720p", ChannelMapper.extractResolution(channel))
    }

    @Test
    fun `extractResolution FHD takes precedence over standalone HD`() {
        // "FHD" should match as 1080p, not as HD (720p)
        val channel = makeChannel(name = "Star Sports FHD")
        assertEquals("1080p", ChannelMapper.extractResolution(channel))
    }

    // ---- toChannelsWithMetadata tests ----

    @Test
    fun `toChannelsWithMetadata produces correct metadata`() {
        val channels = listOf(
            makeChannel(
                name = "Star Sports HD",
                rawAttributes = mapOf("tvg-language" to "Hindi")
            ),
            makeChannel(
                name = "BBC 4K",
                rawAttributes = mapOf("tvg-language" to "English")
            ),
            makeChannel(
                name = "Local Channel",
                rawAttributes = emptyMap()
            )
        )

        val metadata = ChannelMapper.toChannelsWithMetadata(channels)
        assertEquals(3, metadata.size)

        assertEquals("Hindi", metadata[0].language)
        assertEquals("720p", metadata[0].resolution)

        assertEquals("English", metadata[1].language)
        assertEquals("4K", metadata[1].resolution)

        assertEquals("", metadata[2].language)
        assertEquals("", metadata[2].resolution)
    }

    @Test
    fun `toChannelsWithMetadata preserves channel data`() {
        val channels = listOf(
            IPTVChannel(
                id = "id1",
                tvgId = "tvg1",
                tvgName = "Name1",
                tvgLogo = "http://logo.com/1.png",
                groupTitle = "Sports",
                name = "Channel One HD",
                streamUrl = "http://stream.com/1",
                providerId = "prov1"
            )
        )

        val metadata = ChannelMapper.toChannelsWithMetadata(channels)
        assertEquals(1, metadata.size)
        assertEquals("id1", metadata[0].channel.id)
        assertEquals("Channel One HD", metadata[0].channel.name)
        assertEquals("http://logo.com/1.png", metadata[0].channel.logoUrl)
        assertEquals("http://stream.com/1", metadata[0].channel.streamUrl)
        assertEquals("Sports", metadata[0].channel.category)
    }
}
