package com.example.calmsource.core.discoveryengine

import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.ranking.TasteProfileBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.mockito.Mockito.*

class ColdStartTest {

    @Test
    fun testColdStartSeedGenres() {
        val mockDao = mock(DiscoveryEngineDao::class.java)
        val profileId = "p-cold"

        // Watch history and favorites are empty
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(emptyList())
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())
        
        // Mock seed genres setting
        `when`(mockDao.getSetting("seed_genres_$profileId")).thenReturn("Action,Sci-Fi")

        val profile = TasteProfileBuilder.buildTasteProfile(mockDao, profileId)

        assertNotNull(profile)
        assertEquals(profileId, profile.profileId)
        assertEquals(1.0, profile.genreAffinities["action"]!!, 0.01)
        assertEquals(1.0, profile.genreAffinities["sci-fi"]!!, 0.01)
        assertEquals(2, profile.genreAffinities.size)
    }
}
