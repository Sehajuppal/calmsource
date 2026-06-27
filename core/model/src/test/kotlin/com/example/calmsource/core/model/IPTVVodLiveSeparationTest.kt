package com.example.calmsource.core.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IPTVVodLiveSeparationTest {

    @Test
    fun testIsVodLogic() {
        // Live channel
        val liveChannel = IPTVChannel(
            id = "1",
            tvgId = "live1",
            tvgName = "Live Channel",
            tvgLogo = null,
            groupTitle = "News",
            name = "Live Channel",
            streamUrl = "http://example.com/live.m3u8",
            providerId = "p1"
        )
        assertFalse(liveChannel.isVod)

        // VOD channel by groupTitle (VOD)
        val vodGroupChannel = IPTVChannel(
            id = "2",
            tvgId = "vod1",
            tvgName = "VOD Movie",
            tvgLogo = null,
            groupTitle = "English VOD",
            name = "VOD Movie",
            streamUrl = "http://example.com/vod.mp4",
            providerId = "p1"
        )
        assertTrue(vodGroupChannel.isVod)

        // VOD channel by groupTitle (Movies)
        val moviesGroupChannel = IPTVChannel(
            id = "3",
            tvgId = "vod2",
            tvgName = "Action Movie",
            tvgLogo = null,
            groupTitle = "Action Movies",
            name = "Action Movie",
            streamUrl = "http://example.com/movie.mkv",
            providerId = "p1"
        )
        assertTrue(moviesGroupChannel.isVod)

        // VOD channel by URL (/movie/)
        val urlMovieChannel = IPTVChannel(
            id = "4",
            tvgId = "vod3",
            tvgName = "Comedy Movie",
            tvgLogo = null,
            groupTitle = "General",
            name = "Comedy Movie",
            streamUrl = "http://example.com/movie/user/pass/123.mp4",
            providerId = "p1"
        )
        assertTrue(urlMovieChannel.isVod)

        // VOD channel by URL (/series/)
        val urlSeriesChannel = IPTVChannel(
            id = "5",
            tvgId = "vod4",
            tvgName = "Drama Series",
            tvgLogo = null,
            groupTitle = "General",
            name = "Drama Series",
            streamUrl = "http://example.com/series/user/pass/123.mp4",
            providerId = "p1"
        )
        assertTrue(urlSeriesChannel.isVod)
    }
}
