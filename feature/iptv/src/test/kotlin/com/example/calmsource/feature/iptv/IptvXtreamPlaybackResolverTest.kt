package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class IptvXtreamPlaybackResolverTest {

    @Test
    fun resolveProviderId_prefersPseudoUrlProviderId() {
        val source = PlaybackSource(
            id = "legacy-id",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "Channel",
            rawUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider-abc", "12345")!!
        )
        assertEquals("provider-abc", IptvXtreamPlaybackResolver.resolveProviderId(source, null))
    }

    @Test
    fun resolveProviderId_fallsBackToExistingChannelProvider() {
        val source = PlaybackSource(
            id = "channel-1",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "Channel",
            rawUrl = "http://example.com/stream.m3u8"
        )
        val channel = IPTVChannel(
            id = "channel-1",
            tvgId = null,
            tvgName = null,
            tvgLogo = null,
            groupTitle = "Live",
            name = "Channel",
            streamUrl = "xtream://stream_id/provider-xyz/99",
            providerId = "provider-xyz"
        )
        assertEquals("provider-xyz", IptvXtreamPlaybackResolver.resolveProviderId(source, channel))
    }

    @Test
    fun resolveProviderId_returnsNullWhenUnknown() {
        val source = PlaybackSource(
            id = "unknown",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "Channel",
            rawUrl = "http://example.com/stream.m3u8"
        )
        assertNull(IptvXtreamPlaybackResolver.resolveProviderId(source, null))
    }

    @Test
    fun syntheticAttributes_vodSourceUsesVodContentTypeAndContainer() {
        val source = PlaybackSource(
            id = "provider-abc_vod_12345",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "A Movie",
            rawUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider-abc", "12345")!!,
            metadata = com.example.calmsource.core.model.PlaybackItemMetadata(
                title = "A Movie",
                isLive = false,
                containerFormat = "mkv"
            )
        )
        val attrs = IptvXtreamPlaybackResolver.buildSyntheticXtreamAttributes(source, isLive = false)
        assertEquals("vod", attrs["xtream_content_type"])
        assertEquals("mkv", attrs["container_extension"])
    }

    @Test
    fun syntheticAttributes_liveSourceUsesLiveContentType() {
        val source = PlaybackSource(
            id = "provider-abc_live_777",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "A Channel",
            rawUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider-abc", "777")!!,
            metadata = com.example.calmsource.core.model.PlaybackItemMetadata(
                title = "A Channel",
                isLive = true,
                containerFormat = "ts"
            )
        )
        val attrs = IptvXtreamPlaybackResolver.buildSyntheticXtreamAttributes(source, isLive = true)
        assertEquals("live", attrs["xtream_content_type"])
    }

    @Test
    fun syntheticAttributes_seriesEpisodeSourceUsesSeriesContentType() {
        val source = PlaybackSource(
            id = "${PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX}provider-abc|9001",
            type = com.example.calmsource.core.model.PlaybackSourceType.IPTV,
            title = "Show S1E1",
            rawUrl = XtreamStreamUrlBuilder.createPseudoUrl("provider-abc", "9001")!!,
            metadata = com.example.calmsource.core.model.PlaybackItemMetadata(
                title = "Show S1E1",
                isLive = false,
                containerFormat = "mp4"
            )
        )
        val attrs = IptvXtreamPlaybackResolver.buildSyntheticXtreamAttributes(source, isLive = false)
        assertEquals("series", attrs["xtream_content_type"])
        assertEquals("mp4", attrs["container_extension"])
    }
}
