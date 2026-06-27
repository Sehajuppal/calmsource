package com.example.calmsource.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class SeasonDisplayTest {

    @Test
    fun `normal seasons start at one and specials are labeled clearly`() {
        val videos = listOf(
            StremioVideo(id = "s0e1", title = "Holiday Special", season = 0, episode = 1),
            StremioVideo(id = "s1e1", title = "Pilot", season = 1, episode = 1),
            StremioVideo(id = "s1e1", title = "Duplicate", season = 1, episode = 1),
            StremioVideo(id = null, title = null, season = 0, episode = null),
            StremioVideo(id = "invalid", title = "Invalid", season = -1, episode = 1)
        )

        assertEquals(listOf(1, 0), videos.displayableSeasons())
        assertEquals(2, videos.displayableEpisodes().size)
        assertEquals("Season 1", seasonDisplayLabel(1))
        assertEquals("Specials", seasonDisplayLabel(0))
    }
}
