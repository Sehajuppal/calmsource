package com.example.calmsource.core.discoveryengine

import android.content.Context
import com.example.calmsource.core.discoveryengine.database.*
import com.example.calmsource.core.discoveryengine.models.*
import com.example.calmsource.core.discoveryengine.normalization.Vectorizer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class SemanticSearchTest {

    private lateinit var mockContext: Context
    private lateinit var mockDao: DiscoveryEngineDao
    private lateinit var repository: DiscoveryEngineRepository

    @Before
    fun setUp() {
        mockContext = mock(Context::class.java)
        mockDao = mock(DiscoveryEngineDao::class.java)
        repository = DiscoveryEngineRepository(mockContext, mockDao)
    }

    @Test
    fun testVectorizerCosineSimilarity() {
        val title = "Inception"
        val overview = "A thief who steals corporate secrets through the use of dream-sharing technology."
        val genres = listOf("Action", "Sci-Fi", "Adventure")
        val cast = listOf("Leonardo DiCaprio", "Joseph Gordon-Levitt")
        val director = "Christopher Nolan"
        val language = "English"

        // Generate vector for Inception
        val vec1 = Vectorizer.vectorize(title, overview, genres, cast, director, language, null)
        assertEquals(Vectorizer.DIMENSIONS, vec1.size)

        // Generate vector for Interstellar (similar director, genre, cast members, etc.)
        val vec2 = Vectorizer.vectorize(
            "Interstellar",
            "A team of explorers travel through a wormhole in space in an attempt to ensure humanity's survival.",
            listOf("Sci-Fi", "Drama", "Adventure"),
            listOf("Matthew McConaughey", "Anne Hathaway"),
            "Christopher Nolan",
            "English",
            null
        )

        // Generate vector for a totally different movie
        val vec3 = Vectorizer.vectorize(
            "Pride and Prejudice",
            "Sparks fly when spirited Elizabeth Bennet meets single, rich, and proud Mr. Darcy.",
            listOf("Romance", "Drama"),
            listOf("Keira Knightley", "Matthew Macfadyen"),
            "Joe Wright",
            "English",
            null
        )

        val simSame = Vectorizer.cosineSimilarity(vec1, vec1)
        val simSimilar = Vectorizer.cosineSimilarity(vec1, vec2)
        val simDifferent = Vectorizer.cosineSimilarity(vec1, vec3)

        println("=== Cosine Similarity Heuristics ===")
        println("Self similarity (Inception vs Inception): $simSame")
        println("Similar movie similarity (Inception vs Interstellar): $simSimilar")
        println("Different movie similarity (Inception vs Pride and Prejudice): $simDifferent")

        // Self similarity must be 1.0 (or extremely close due to floating point precision)
        assertEquals(1.0, simSame, 0.0001)
        // Similar movie should have higher similarity than completely different movie
        assertTrue("Similar movies should have higher cosine similarity", simSimilar > simDifferent)
    }

    @Test
    fun testSearchSemantic() = runBlocking {
        val profileId = "profile-1"
        val query = "dream sharing"

        val inceptionEmb = MediaEmbeddingEntity(
            itemId = "m-inception",
            version = Vectorizer.VERSION,
            dimension = Vectorizer.DIMENSIONS,
            norm = 1.0,
            embedding = Vectorizer.vectorToBytes(
                Vectorizer.vectorize("Inception", "dream sharing heist", listOf("Sci-Fi"), emptyList(), null, null, null)
            ),
            updatedAt = System.currentTimeMillis()
        )
        val romanceEmb = MediaEmbeddingEntity(
            itemId = "m-romance",
            version = Vectorizer.VERSION,
            dimension = Vectorizer.DIMENSIONS,
            norm = 1.0,
            embedding = Vectorizer.vectorToBytes(
                Vectorizer.vectorize("Notebook", "romantic story", listOf("Romance"), emptyList(), null, null, null)
            ),
            updatedAt = System.currentTimeMillis()
        )

        val inceptionMedia = MediaItemEntity(
            id = "m-inception", type = "movie", title = "Inception", overview = "dream sharing heist",
            posterUrl = null, rating = 8.8, releaseYear = 2010, genres = "Sci-Fi", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "inception", updatedAt = 0L
        )
        val romanceMedia = MediaItemEntity(
            id = "m-romance", type = "movie", title = "Notebook", overview = "romantic story",
            posterUrl = null, rating = 7.8, releaseYear = 2004, genres = "Romance", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "notebook", updatedAt = 0L
        )

        // Mock DAO responses
        `when`(mockDao.getMediaItemsByPrefix("dream sharing", 500)).thenReturn(listOf(inceptionMedia, romanceMedia))
        `when`(mockDao.getChannelsByPrefix("dream sharing", 500)).thenReturn(emptyList())
        `when`(mockDao.getEmbeddingsByIds(listOf("m-inception", "m-romance"))).thenReturn(listOf(inceptionEmb, romanceEmb))
        `when`(mockDao.getChannelsByIds(listOf("m-inception", "m-romance"))).thenReturn(emptyList())
        `when`(mockDao.getUserChannelStatesForProfile(profileId)).thenReturn(emptyList())
        
        `when`(mockDao.getMediaItemsByIds(listOf("m-inception", "m-romance")))
            .thenReturn(listOf(inceptionMedia, romanceMedia))
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(emptyList())

        val results = repository.searchSemantic(profileId, query, limit = 5)

        // Verify Inception matches the "dream sharing" query and is returned at rank 1
        assertTrue(results.isNotEmpty())
        assertEquals("m-inception", results[0].id)
        assertTrue(results[0].score > 0.0)
    }

    @Test
    fun testGetSemanticRecommendations() = runBlocking {
        val profileId = "profile-1"

        val testSim = Vectorizer.cosineSimilarity(
            Vectorizer.vectorize("Inception", "dream sharing heist", listOf("Sci-Fi", "Action"), emptyList(), "Christopher Nolan", null, null),
            Vectorizer.vectorize("Interstellar", "space exploration travel", listOf("Sci-Fi", "Action"), emptyList(), "Christopher Nolan", null, null)
        )
        println("=== DEBUG: Cosine Similarity between Inception and Interstellar = $testSim")

        // Mock user states: user favorited m-inception
        val favoriteState = UserItemStateEntity(
            profileId = profileId,
            itemId = "m-inception",
            isFavorite = true,
            isHidden = false,
            lastWatchedAt = System.currentTimeMillis(),
            progressMs = 100L,
            durationMs = 100L,
            watchCount = 1,
            isCompleted = true
        )
        `when`(mockDao.getUserItemStatesForProfile(profileId)).thenReturn(listOf(favoriteState))

        // Mock embeddings
        val inceptionEmb = MediaEmbeddingEntity(
            itemId = "m-inception",
            version = Vectorizer.VERSION,
            dimension = Vectorizer.DIMENSIONS,
            norm = 1.0,
            embedding = Vectorizer.vectorToBytes(
                Vectorizer.vectorize("Inception", "dream sharing heist", listOf("Sci-Fi", "Action"), emptyList(), "Christopher Nolan", null, null)
            ),
            updatedAt = System.currentTimeMillis()
        )
        val interstellarEmb = MediaEmbeddingEntity(
            itemId = "m-interstellar",
            version = Vectorizer.VERSION,
            dimension = Vectorizer.DIMENSIONS,
            norm = 1.0,
            embedding = Vectorizer.vectorToBytes(
                Vectorizer.vectorize("Interstellar", "space exploration travel", listOf("Sci-Fi", "Action"), emptyList(), "Christopher Nolan", null, null)
            ),
            updatedAt = System.currentTimeMillis()
        )
        val romanceEmb = MediaEmbeddingEntity(
            itemId = "m-romance",
            version = Vectorizer.VERSION,
            dimension = Vectorizer.DIMENSIONS,
            norm = 1.0,
            embedding = Vectorizer.vectorToBytes(
                Vectorizer.vectorize("Notebook", "romantic drama story", listOf("Romance"), emptyList(), null, null, null)
            ),
            updatedAt = System.currentTimeMillis()
        )

        val interstellarMedia = MediaItemEntity(
            id = "m-interstellar", type = "movie", title = "Interstellar", overview = "space exploration travel",
            posterUrl = null, rating = 8.6, releaseYear = 2014, genres = "Sci-Fi,Drama", cast = "",
            director = null, language = "en", durationMs = 0L, externalId = null,
            externalIdsJson = "{}", source = "pack-1", seriesId = null, seasonNumber = null,
            episodeNumber = null, normalizedTitle = "interstellar", updatedAt = 0L
        )

        `when`(mockDao.getEmbeddingsByIds(listOf("m-inception"))).thenReturn(listOf(inceptionEmb))
        `when`(mockDao.getEmbeddingsByIds(listOf("m-interstellar"))).thenReturn(listOf(interstellarEmb))
        `when`(mockDao.getSearchCandidates(1000)).thenReturn(listOf(interstellarMedia))
        `when`(mockDao.getLatestWatchEventsForProfile(profileId)).thenReturn(emptyList())
        `when`(mockDao.getMediaItemsByIds(listOf("m-interstellar", "m-romance")))
            .thenReturn(listOf(interstellarMedia)) // Mock returning interstellar

        val recommendations = repository.getSemanticRecommendations(profileId, limit = 5)

        // Interstellar should be recommended because it has Sci-Fi overlapping with Inception
        assertTrue(recommendations.isNotEmpty())
        assertEquals("m-interstellar", recommendations[0].id)
    }

    @Test
    fun testUninstallPackPurgesEmbeddings() = runBlocking {
        val packId = "test-pack"
        
        repository.uninstallPack(packId)
        
        // Verify that deleteEmbeddingsBySource was called to purge embeddings related to that pack
        verify(mockDao).deleteEmbeddingsBySource(packId)
    }
}
