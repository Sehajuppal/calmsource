package com.example.calmsource.feature.iptv.xtream

import org.junit.Assert.assertEquals
import org.junit.Test

class XtreamSeriesEpisodeParsingTest {

    @Test
    fun `parseSeriesEpisodes reads season maps from get_series_info`() {
        val episodes = XtreamApiClientImpl().parseSeriesEpisodes(
            body = """
                {
                  "episodes": {
                    "1": [
                      {
                        "id": "111",
                        "episode_num": 1,
                        "title": "Pilot",
                        "container_extension": "mkv"
                      }
                    ],
                    "2": [
                      {
                        "id": "222",
                        "episode_num": "3",
                        "title": "Finale"
                      }
                    ]
                  }
                }
            """.trimIndent(),
            providerId = "iptv-provider",
            seriesId = "900"
        )

        assertEquals(2, episodes.size)
        assertEquals("111", episodes[0].episodeId)
        assertEquals(1, episodes[0].seasonNumber)
        assertEquals(1, episodes[0].episodeNumber)
        assertEquals("mkv", episodes[0].containerExtension)
        assertEquals("222", episodes[1].episodeId)
        assertEquals(2, episodes[1].seasonNumber)
        assertEquals(3, episodes[1].episodeNumber)
        assertEquals("mp4", episodes[1].containerExtension)
    }
}
