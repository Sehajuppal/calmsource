package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineDao
import com.example.calmsource.core.discoveryengine.database.DiscoveryEngineRepository
import com.example.calmsource.core.discoveryengine.database.MediaItemEntity
import com.example.calmsource.core.discoveryengine.database.SearchEventEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class HomePersonalizationTest {

    private lateinit var dao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        dao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mock(Context::class.java), dao)
    }

    @Test
    fun `recent selected search produces a home row with the title and similar content`() = runBlocking {
        val dune = media(
            id = "tt1160419",
            title = "Dune",
            poster = "https://images.example/dune.jpg",
            genres = "Adventure,Drama,Sci-Fi"
        )
        val arrival = media(
            id = "tt2543164",
            title = "Arrival",
            poster = "https://images.example/arrival.jpg",
            genres = "Drama,Sci-Fi"
        )
        val event = SearchEventEntity(
            profileId = "default",
            query = "dune",
            timestamp = 100L,
            selectedItemId = dune.id
        )

        `when`(dao.getRecentSelectedSearchEvents("default", 10)).thenReturn(listOf(event))
        `when`(dao.getMediaItemsByIds(listOf(dune.id))).thenReturn(listOf(dune))
        `when`(dao.getMediaItem(dune.id)).thenReturn(dune) // needed by getMoreLikeThis
        `when`(dao.getSearchCandidates(200)).thenReturn(listOf(dune, arrival))

        val rows = repository.getHomeRows("default", forceRefresh = true)
        val row = rows.first { it.rowType == "search_interest:${dune.id}" }

        assertEquals("Because You Searched for Dune", row.title)
        assertEquals(listOf("Dune", "Arrival"), row.items.map { it.title })
        assertEquals("https://images.example/arrival.jpg", row.items[1].posterUrl)
        assertTrue(row.items[1].externalIds.containsKey("imdb"))
    }

    private fun media(
        id: String,
        title: String,
        poster: String,
        genres: String
    ): MediaItemEntity {
        return MediaItemEntity(
            id = id,
            type = "movie",
            title = title,
            overview = null,
            posterUrl = poster,
            rating = 8.0,
            releaseYear = 2021,
            genres = genres,
            cast = "",
            director = null,
            language = "en",
            durationMs = null,
            externalId = id,
            externalIdsJson = """{"imdb":"$id"}""",
            source = "com.linvo.cinemeta",
            seriesId = null,
            seasonNumber = null,
            episodeNumber = null,
            normalizedTitle = title.lowercase(),
            updatedAt = 1L
        )
    }
}
