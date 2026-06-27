package com.example.calmsource.core.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.put
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StremioCompatibilityRegressionTest {

    @Test
    fun `extension manifest keeps scoped resource types`() {
        val manifest = ExtensionManifest(
            id = "streamer",
            name = "Streamer",
            resources = listOf("stream"),
            types = emptyList(),
            resourceTypes = mapOf("stream" to listOf("movie"))
        )

        assertTrue(manifest.isResourceSupported("stream", "movie"))
        assertFalse(manifest.isResourceSupported("stream", "series"))
    }

    @Test
    fun `declared catalog is supported even when resources omits catalog`() {
        val manifest = ExtensionManifest(
            id = "catalog-only",
            name = "Catalog Only",
            resources = emptyList(),
            types = emptyList(),
            catalogs = listOf(
                ExtensionCatalog(type = "movie", id = "popular", name = "Popular")
            )
        )

        assertTrue(manifest.isResourceSupported("catalog", "movie"))
        assertFalse(manifest.isResourceSupported("catalog", "series"))
    }

    @Test
    fun `catalog response tolerates null metas and missing preview type`() {
        val nullResponse = Json { ignoreUnknownKeys = true }
            .decodeFromString<StremioCatalogResponse>("""{"metas":null}""")
        val missingTypeResponse = Json { ignoreUnknownKeys = true }
            .decodeFromString<StremioCatalogResponse>(
                """{"metas":[{"id":"tt123","name":"Catalog Movie"}]}"""
            )

        assertTrue(nullResponse.metas.orEmpty().isEmpty())
        assertEquals("", missingTypeResponse.metas.orEmpty().single().type)
    }

    @Test
    fun `stremio meta parses imdb id and episode videos`() {
        val response = Json { ignoreUnknownKeys = true }.decodeFromString<StremioMetaResponse>(
            """
            {
              "meta": {
                "id": "tmdb:123",
                "imdb_id": "tt9999999",
                "type": "series",
                "name": "Example Show",
                "videos": [
                  { "id": "tt9999999:1:1", "title": "Pilot", "season": 1, "episode": 1 }
                ]
              }
            }
            """.trimIndent()
        )

        val meta = response.meta
        assertNotNull(meta)
        assertEquals("tt9999999", meta!!.imdbId)
        assertEquals("tt9999999:1:1", meta.videos!!.first().id)
        assertEquals(1, meta.videos!!.first().season)
        assertEquals(1, meta.videos!!.first().episode)
    }

    @Test
    fun `stremio stream behaviorHints requestHeaders are mapped correctly to stream source`() {
        val behaviorHints = mapOf(
            "requestHeaders" to kotlinx.serialization.json.buildJsonObject {
                put("User-Agent", "MyCustomUserAgent")
                put("Referer", "https://myreferer.com")
            }
        )
        val stream = StremioStream(
            name = "Test Stream",
            title = "Test Stream Title",
            url = "https://example.com/stream.mkv",
            behaviorHints = behaviorHints
        )
        val source = WatchOptionResolver.mapStremioStreamToSource(
            stream = stream,
            providerId = "ext-test",
            providerName = "Test Provider",
            mediaId = "tt12345"
        )
        assertEquals("MyCustomUserAgent", source.headers["User-Agent"])
        assertEquals("https://myreferer.com", source.headers["Referer"])
    }
}
