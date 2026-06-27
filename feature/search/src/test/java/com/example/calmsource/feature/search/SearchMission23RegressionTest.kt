package com.example.calmsource.feature.search

import com.example.calmsource.core.model.MediaItem
import com.example.calmsource.core.model.MediaType
import com.example.calmsource.core.model.FakeData
import com.example.calmsource.core.model.Program
import com.example.calmsource.core.model.SearchGroupType
import com.example.calmsource.core.model.SearchProviderResult
import com.example.calmsource.core.model.SearchQuery
import com.example.calmsource.core.model.SourceType
import com.example.calmsource.core.model.StreamSource
import com.example.calmsource.core.model.UserPreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchMission23RegressionTest {

    private val prefs = FakeData.defaultPreferences

    @Test
    fun `provider result survives normalized query mismatch without a stream source`() = runBlocking {
        val item = MediaItem(
            id = "tt-search-hit",
            title = "Spider-Man",
            type = MediaType.MOVIE
        )
        val providerResult = SearchProviderResult(
            providerId = "prov-extensions",
            providerName = "Extensions Indexer",
            query = SearchQuery("spiderman"),
            mediaItems = listOf(item)
        )

        val groups = SearchResultMerger.merge(listOf(providerResult), "spiderman", prefs)

        val movies = groups.firstOrNull { it.groupType == SearchGroupType.MOVIES }
        assertNotNull("The merger must trust the provider's normalized search hit", movies)
        val matchedMovie = movies!!.results.firstOrNull { it.mediaItem.id == item.id }
        assertNotNull(matchedMovie)
        assertTrue("Catalog hits must retain extension provenance", matchedMovie!!.availableFrom.contains(SourceType.EXTENSION))
    }

    @Test
    fun `catalog-only provider result is exposed to app search without local ingestion`() = runBlocking {
        val item = MediaItem(
            id = "tt-catalog-only",
            title = "Remote Catalog Movie",
            type = MediaType.MOVIE,
            overview = "Fetched directly from an installed addon",
            externalIds = mapOf(
                "imdb" to "tt1234567",
                "stremio" to "catalog:remote-movie"
            )
        )
        val providerResult = SearchProviderResult(
            providerId = "prov-extensions",
            providerName = "Extensions Indexer",
            query = SearchQuery("remote catalog"),
            mediaItems = listOf(item)
        )

        val displayResults = SearchResultMerger
            .merge(listOf(providerResult), "remote catalog", prefs)
            .toSearchDisplayResults()

        assertEquals(1, displayResults.size)
        assertEquals(item.id, displayResults.single().id)
        assertEquals("movie", displayResults.single().type)
        assertEquals("Extension", displayResults.single().sourceLabel)
        assertFalse(displayResults.single().hasPlayableSource)
        assertEquals(item.externalIds, displayResults.single().externalIds)
    }

    @Test
    fun `live program search result keeps channel id for playback`() = runBlocking {
        val providerResult = SearchProviderResult(
            providerId = "prov-epg",
            providerName = "Live TV Guide",
            query = SearchQuery("news"),
            programs = listOf(
                Program(
                    id = "program-7",
                    channelId = "channel-42",
                    title = "Evening News",
                    description = "Live",
                    startTimeMs = 0L,
                    endTimeMs = 1_000L
                )
            )
        )

        val displayResult = SearchResultMerger
            .merge(listOf(providerResult), "news", prefs)
            .toSearchDisplayResults()
            .single()

        assertEquals("channel-42", displayResult.id)
        assertEquals("channel", displayResult.type)
        assertTrue(displayResult.hasPlayableSource)
    }

    @Test
    fun `real IPTV VOD provider id keeps its source and IPTV classification`() = runBlocking {
        val item = MediaItem(
            id = "iptv-vod-evening-news",
            title = "Evening News Replay",
            type = MediaType.MOVIE
        )
        val source = StreamSource(
            id = "channel-42",
            name = "Evening News Replay",
            url = "https://stream.invalid/vod/42",
            extensionId = "provider-room-id-42",
            resolution = "VOD",
            language = "Unknown"
        )
        val providerResult = SearchProviderResult(
            providerId = "prov-vod",
            providerName = "IPTV VOD Catalogs",
            query = SearchQuery("eveningnews"),
            mediaItems = listOf(item),
            streamSources = listOf(source)
        )

        val groups = SearchResultMerger.merge(listOf(providerResult), "eveningnews", prefs)
        val result = groups
            .first { it.groupType == SearchGroupType.IPTV_VOD }
            .results
            .first { it.mediaItem.id == item.id }

        assertEquals(source.id, result.bestMatchOption?.source?.id)
        assertTrue(result.availableFrom.contains(SourceType.IPTV))
        assertFalse(result.availableFrom.contains(SourceType.EXTENSION))
    }

    @Test
    fun `injected favorite and history snapshot affects engine ranking`() = runBlocking {
        val provider = mediaProvider(
            MediaItem("a-unranked", "Movie Alpha", MediaType.MOVIE),
            MediaItem("z-favorite", "Movie Beta", MediaType.MOVIE)
        )
        val engine = UniversalSearchEngineImpl(
            providers = listOf(provider),
            memorySnapshot = SearchMemorySnapshot {
                SearchMemorySignals(
                    favoriteMediaIds = setOf("z-favorite"),
                    historyMediaIds = setOf("z-favorite")
                )
            }
        )

        val groups = engine.search("movie", prefs).toList().last()
        val movies = groups.first { it.groupType == SearchGroupType.MOVIES }

        assertEquals("z-favorite", movies.results.first().mediaItem.id)
    }

    @Test
    fun `completed safe query is recorded once after all providers finish`() = runBlocking {
        val recorded = mutableListOf<String>()
        val provider = object : SearchProvider {
            override val id = "delayed"
            override val name = "Delayed"
            override val priority = 1

            override fun search(
                query: SearchQuery,
                prefs: UserPreferences
            ): Flow<SearchProviderResult> = flow {
                delay(20)
                assertTrue("Query must not be recorded before providers finish", recorded.isEmpty())
                emit(SearchProviderResult(id, name, query))
            }
        }
        val engine = UniversalSearchEngineImpl(
            providers = listOf(provider),
            signalSink = SearchSignalSink { query -> recorded.add(query) }
        )

        engine.search("  The   Matrix  ", prefs).toList()

        assertEquals(listOf("The Matrix"), recorded)
    }

    @Test
    fun `unsafe or incomplete queries are never recorded`() = runBlocking {
        val recorded = mutableListOf<String>()
        val emptyEngine = UniversalSearchEngineImpl(
            providers = emptyList(),
            signalSink = SearchSignalSink { query -> recorded.add(query) }
        )
        val unsafeQueries = listOf(
            "https://private.invalid/manifest.json",
            "manifest.json",
            "magnet:?xt=urn:btih:abc123",
            "xtream://stream_id/123",
            "access_token=secret-value",
            "username=viewer password=secret"
        )

        unsafeQueries.forEach { query ->
            emptyEngine.search(query, prefs).toList()
        }
        emptyEngine.search("   ", prefs).toList()

        val cancellableEngine = UniversalSearchEngineImpl(
            providers = listOf(mediaProvider(MediaItem("one", "One", MediaType.MOVIE))),
            signalSink = SearchSignalSink { query -> recorded.add(query) }
        )
        cancellableEngine.search("one", prefs).first()

        assertTrue(recorded.isEmpty())
    }

    private fun mediaProvider(vararg items: MediaItem): SearchProvider {
        return object : SearchProvider {
            override val id = "media-provider"
            override val name = "Media Provider"
            override val priority = 1

            override fun search(
                query: SearchQuery,
                prefs: UserPreferences
            ): Flow<SearchProviderResult> = flow {
                emit(SearchProviderResult(
                    providerId = id,
                    providerName = name,
                    query = query,
                    mediaItems = items.toList()
                ))
            }
        }
    }
}
