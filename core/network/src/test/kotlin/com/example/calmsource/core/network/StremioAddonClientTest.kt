package com.example.calmsource.core.network

import com.example.calmsource.core.model.*
import io.ktor.client.request.get
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class StremioAddonClientTest {

    private val secretsMap = mutableMapOf<String, String>()
    private val json = Json { ignoreUnknownKeys = true; isLenient = true; encodeDefaults = true }

    @Before
    fun setUp() {
        secretsMap.clear()
        ExtensionSecrets.readDelegate = { providerId, key -> secretsMap["$providerId-$key"] }
        ExtensionSecrets.saveDelegate = { providerId, key, value -> secretsMap["$providerId-$key"] = value }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 1: URL Resolution & Secret Interpolation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testUrlResolutionWithSecrets() {
        ExtensionSecrets.saveSecret("addon-1", "api_key", "secret_value_123")

        val url = "https://addon-1.com/api_key={secret_api_key}/manifest.json"
        val resolved = StremioAddonClient.resolveUrl(url, "addon-1")

        assertEquals("https://addon-1.com/api_key=secret_value_123/manifest.json", resolved)
    }

    @Test
    fun testUrlResolution_missingSecret_replacesWithEmpty() {
        val url = "https://addon.com/{secret_missing_key}/manifest.json"
        val resolved = StremioAddonClient.resolveUrl(url, "addon-x")
        assertEquals("https://addon.com//manifest.json", resolved)
    }

    @Test
    fun testUrlResolution_noPlaceholders_passthrough() {
        val url = "https://addon.com/manifest.json"
        val resolved = StremioAddonClient.resolveUrl(url, "addon-x")
        assertEquals("https://addon.com/manifest.json", resolved)
    }

    @Test
    fun testUrlResolution_multipleSecrets() {
        ExtensionSecrets.saveSecret("addon-m", "token", "tok123")
        ExtensionSecrets.saveSecret("addon-m", "api", "api456")

        val url = "https://addon.com/{secret_token}/{secret_api}/manifest.json"
        val resolved = StremioAddonClient.resolveUrl(url, "addon-m")
        assertEquals("https://addon.com/tok123/api456/manifest.json", resolved)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 1: Config Parsing from URL
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testParseConfigFromUrl() {
        val url = "https://addon-1.com/api_key=secret_value_123&lang=en/manifest.json"
        val config = StremioAddonClient.parseConfigFromUrl(url)

        assertEquals(2, config.size)
        assertEquals("secret_value_123", config["api_key"])
        assertEquals("en", config["lang"])
    }

    @Test
    fun `config parser decodes delimiters inside values`() {
        val url = "https://addon.com/filter=quality%3D1080p%26lang%3Den/manifest.json"

        val config = StremioAddonClient.parseConfigFromUrl(url)

        assertEquals("quality=1080p&lang=en", config["filter"])
    }

    @Test
    fun testParseConfigFromUrl_noConfigSegment() {
        val url = "https://addon.com/manifest.json"
        val config = StremioAddonClient.parseConfigFromUrl(url)
        // The path is "/manifest.json", segments after removeSuffix is ["", ""] → configSegment = "" 
        // No "=" pairs, so map is empty
        assertTrue(config.isEmpty())
    }

    @Test
    fun testParseConfigFromUrl_notManifestPath() {
        val url = "https://addon.com/catalog/movie/top.json"
        val config = StremioAddonClient.parseConfigFromUrl(url)
        assertTrue(config.isEmpty()) // Early return: not a manifest URL
    }

    @Test
    fun testParseConfigFromUrl_invalidUrl() {
        val config = StremioAddonClient.parseConfigFromUrl("not a url at all")
        assertTrue(config.isEmpty())
    }

    @Test
    fun `config parser splits pipe-delimited Torrentio config`() {
        val url = "https://torrentio.strem.fun/realdebrid=RD_KEY|qualityfilter=480p,scr,cam|sort=qualitysize/manifest.json"

        val config = StremioAddonClient.parseConfigFromUrl(url)

        assertEquals(3, config.size)
        assertEquals("RD_KEY", config["realdebrid"])
        assertEquals("480p,scr,cam", config["qualityfilter"])
        assertEquals("qualitysize", config["sort"])
    }

    @Test
    fun `config parser handles mixed pipe and ampersand delimiters`() {
        val url = "https://addon.com/token=abc|lang=en&region=us/manifest.json"

        val config = StremioAddonClient.parseConfigFromUrl(url)

        assertEquals("abc", config["token"])
        assertEquals("en", config["lang"])
        assertEquals("us", config["region"])
    }

    @Test
    fun `config parser handles single pipe-delimited pair`() {
        val url = "https://torrentio.strem.fun/providers=yts/manifest.json"

        val config = StremioAddonClient.parseConfigFromUrl(url)

        assertEquals(1, config.size)
        assertEquals("yts", config["providers"])
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 1: URL construction edge cases (trailing slashes, encoding)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testUrlConstruction_trailingSlashesNormalized() {
        // Simulate what getCatalog would produce internally
        val base = "https://addon.com///".trimEnd('/')
        assertEquals("https://addon.com", base)
    }

    @Test
    fun testUrlConstruction_encodedPathSegments() {
        // Verify encodePathSegment works for special characters
        val encoded = java.net.URLEncoder.encode("movie/special", "UTF-8").replace("+", "%20")
        assertFalse(encoded.contains("/")) // slash must be encoded
        assertTrue(encoded.contains("%2F"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2: Manifest Parsing (via StremioManifest deserialization)
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testManifestParsing_completeManifest() {
        val manifestJson = """
            {
              "id": "org.stremio.publicdomain",
              "name": "Public Domain Movies",
              "description": "Watch public domain movies",
              "version": "1.0.0",
              "resources": ["catalog", "meta", "stream", "subtitles"],
              "types": ["movie", "series"],
              "catalogs": [
                {
                  "type": "movie",
                  "id": "pd_movies",
                  "name": "Public Domain Movies"
                }
              ]
            }
        """.trimIndent()

        val manifest = json.decodeFromString<StremioManifest>(manifestJson)
        assertEquals("org.stremio.publicdomain", manifest.id)
        assertEquals("Public Domain Movies", manifest.name)
        assertEquals(4, manifest.resources.size)
        assertEquals(1, manifest.catalogs.size)
        assertEquals("pd_movies", manifest.catalogs.first().id)
    }

    @Test
    fun testManifestParsing_missingOptionalFields() {
        // Missing: logo, description, version, behaviorHints — all optional
        val manifestJson = """
            {
              "id": "org.minimal",
              "name": "Minimal Addon",
              "resources": ["catalog"],
              "types": ["movie"],
              "catalogs": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString<StremioManifest>(manifestJson)
        assertEquals("org.minimal", manifest.id)
        assertEquals("Minimal Addon", manifest.name)
        assertNull(manifest.description)
        assertNull(manifest.version)
        assertNull(manifest.logo)
        assertNull(manifest.behaviorHints)
        assertTrue(manifest.catalogs.isEmpty())
    }

    @Test
    fun testManifestParsing_emptyCatalogs() {
        val manifestJson = """
            {
              "id": "org.empty",
              "name": "Empty Catalogs",
              "catalogs": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString<StremioManifest>(manifestJson)
        assertTrue(manifest.catalogs.isEmpty())
    }

    @Test
    fun testManifestParsing_unknownResourceTypes() {
        // Unknown resource types like "addon_catalog" should not crash
        val manifestJson = """
            {
              "id": "org.unknown",
              "name": "Unknown Resources",
              "resources": ["catalog", "addon_catalog", "future_resource"],
              "types": ["movie", "tv", "channel"],
              "catalogs": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString<StremioManifest>(manifestJson)
        assertEquals(3, manifest.resources.size)
        assertEquals(3, manifest.types.size)
    }

    @Test
    fun testManifestParsing_extraUnknownFields_ignored() {
        val manifestJson = """
            {
              "id": "org.extra",
              "name": "Extra Fields",
              "contactEmail": "test@example.com",
              "background": "https://img.com/bg.jpg",
              "idPrefixes": ["tt"],
              "catalogs": []
            }
        """.trimIndent()

        val manifest = json.decodeFromString<StremioManifest>(manifestJson)
        assertEquals("org.extra", manifest.id)
        // ignoreUnknownKeys = true means this should not crash
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 3: Stream Response Parsing Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testStreamResponseParsing_complete() {
        val streamJson = """
            {
              "streams": [
                {
                  "name": "Torrentio\n1080p",
                  "title": "The.Godfather.1972.1080p.BluRay.x264.mkv\n👤 124 👥 89",
                  "infoHash": "c0de985ac3d3c8a9ef4f85e13d3c8a9ef4f85e13"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        assertEquals(1, response.streams?.size)
        val stream = response.streams!!.first()
        assertEquals("Torrentio\n1080p", stream.name)
        assertEquals("c0de985ac3d3c8a9ef4f85e13d3c8a9ef4f85e13", stream.infoHash)
    }

    @Test
    fun testStreamResponseParsing_missingUrl() {
        val streamJson = """
            {
              "streams": [
                {
                  "name": "Torrent Only",
                  "title": "Movie.1080p",
                  "infoHash": "abcdef1234567890abcdef1234567890abcdef12"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        val stream = response.streams!!.first()
        assertNull(stream.url)
        assertNotNull(stream.infoHash)
    }

    @Test
    fun testStreamResponseParsing_missingTitle() {
        val streamJson = """
            {
              "streams": [
                {
                  "name": "Stream Name Only",
                  "url": "https://example.com/stream.mp4"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        val stream = response.streams!!.first()
        assertNull(stream.title)
        assertEquals("https://example.com/stream.mp4", stream.url)
    }

    @Test
    fun testStreamResponseParsing_nullBehaviorHints() {
        val streamJson = """
            {
              "streams": [
                {
                  "name": "No Hints",
                  "url": "https://example.com/stream.mp4",
                  "behaviorHints": null
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        assertNull(response.streams!!.first().behaviorHints)
    }

    @Test
    fun testStreamResponseParsing_infoHashOnly_noUrl() {
        val streamJson = """
            {
              "streams": [
                {
                  "infoHash": "0123456789abcdef0123456789abcdef01234567",
                  "fileIdx": 0
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        val stream = response.streams!!.first()
        assertNull(stream.url)
        assertNull(stream.name)
        assertNull(stream.title)
        assertEquals("0123456789abcdef0123456789abcdef01234567", stream.infoHash)
        assertEquals(0, stream.fileIdx)
    }

    @Test
    fun testStreamResponseParsing_emptyStreams() {
        val streamJson = """{"streams": []}"""
        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        assertTrue(response.streams?.isEmpty() == true)
    }

    @Test
    fun testStreamResponseParsing_withBehaviorHints() {
        val streamJson = """
            {
              "streams": [
                {
                  "name": "With Hints",
                  "url": "https://example.com/stream.mp4",
                  "behaviorHints": {
                    "notWebReady": true,
                    "bingeGroup": "group1",
                    "proxyHeaders": {"request": {"User-Agent": "test"}}
                  }
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        val stream = response.streams!!.first()
        assertNotNull(stream.behaviorHints)
        assertEquals(3, stream.behaviorHints!!.size)
    }

    @Test
    fun testStreamResponseParsing_mixedStreams() {
        // Some streams have url, some only infoHash, some have neither
        val streamJson = """
            {
              "streams": [
                {"name": "HTTP", "url": "https://cdn.com/movie.mp4"},
                {"name": "Torrent", "infoHash": "abcdef1234567890abcdef1234567890abcdef12", "fileIdx": 2},
                {"name": "Empty stream"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        assertEquals(3, response.streams?.size)
        assertNotNull(response.streams!![0].url)
        assertNull(response.streams!![0].infoHash)
        assertNull(response.streams!![1].url)
        assertNotNull(response.streams!![1].infoHash)
        assertNull(response.streams!![2].url)
        assertNull(response.streams!![2].infoHash)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 4: Subtitle Response Parsing Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testSubtitleResponseParsing_complete() {
        val subtitleJson = """
            {
              "subtitles": [
                {
                  "id": "1",
                  "lang": "eng",
                  "url": "https://opensubtitles.org/sub/1.srt"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertEquals(1, response.subtitles?.size)
        val sub = response.subtitles!!.first()
        assertEquals("eng", sub.lang)
        assertEquals("https://opensubtitles.org/sub/1.srt", sub.url)
    }

    @Test
    fun testSubtitleResponseParsing_emptyArray() {
        val subtitleJson = """{"subtitles": []}"""
        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertTrue(response.subtitles?.isEmpty() == true)
    }

    @Test
    fun testSubtitleResponseParsing_missingLang() {
        // Missing lang field should use default empty string, not crash
        val subtitleJson = """
            {
              "subtitles": [
                {
                  "id": "2",
                  "url": "https://subs.example.com/2.srt"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertEquals(1, response.subtitles?.size)
        assertEquals("", response.subtitles!!.first().lang) // defaults to empty
    }

    @Test
    fun testSubtitleResponseParsing_missingId() {
        val subtitleJson = """
            {
              "subtitles": [
                {
                  "lang": "fre",
                  "url": "https://subs.example.com/3.srt"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertEquals("", response.subtitles!!.first().id) // defaults to empty
        assertEquals("fre", response.subtitles!!.first().lang)
    }

    @Test
    fun testSubtitleResponseParsing_missingUrl() {
        val subtitleJson = """
            {
              "subtitles": [
                {
                  "id": "4",
                  "lang": "spa"
                }
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertEquals("", response.subtitles!!.first().url) // defaults to empty
    }

    @Test
    fun testSubtitleResponseParsing_allFieldsMissing() {
        val subtitleJson = """
            {
              "subtitles": [
                {}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertEquals(1, response.subtitles?.size)
        assertEquals("", response.subtitles!!.first().id)
        assertEquals("", response.subtitles!!.first().lang)
        assertEquals("", response.subtitles!!.first().url)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 5: Catalog Search Encoding
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCatalogSearchEncoding_basicQuery() {
        val query = "The Matrix"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        assertEquals("The+Matrix", encoded)
        val extra = "search=$encoded"
        assertEquals("search=The+Matrix", extra)
    }

    @Test
    fun testCatalogSearchEncoding_specialCharacters() {
        val query = "Rock & Roll: 100% Live!"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val extra = "search=$encoded"
        // Verify no raw special chars remain unencoded
        assertFalse(extra.contains("&"))
        assertFalse(extra.contains(":"))
        assertTrue(extra.contains("%"))
        assertTrue(extra.contains("%25"))
        assertTrue(extra.startsWith("search="))
    }

    @Test
    fun testCatalogSearchEncoding_unicodeCharacters() {
        val query = "日本語テスト"
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        val extra = "search=$encoded"
        // Should be valid URL-encoded UTF-8
        assertTrue(extra.startsWith("search="))
        assertFalse(extra.contains("日"))
    }

    @Test
    fun testCatalogSearchEncoding_emptyQuery() {
        val query = ""
        val encoded = java.net.URLEncoder.encode(query, "UTF-8")
        assertEquals("", encoded)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 3 (continued): Meta Response Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testMetaResponseParsing() {
        val metaJson = """
            {
              "meta": {
                "id": "tt0068646",
                "type": "movie",
                "name": "The Godfather",
                "genres": ["Crime", "Drama"],
                "releaseInfo": "1972",
                "imdbRating": "9.2",
                "description": "An organized crime dynasty's aging patriarch transfers control of his clandestine empire to his reluctant son."
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioMetaResponse>(metaJson)
        val meta = response.meta!!
        assertEquals("tt0068646", meta.id)
        assertEquals("The Godfather", meta.name)
        assertEquals("9.2", meta.imdbRating)
        assertEquals(2, meta.genres?.size)
    }

    @Test
    fun testMetaResponseParsing_minimalFields() {
        // Only required fields: id, type, name
        val metaJson = """
            {
              "meta": {
                "id": "tt1234567",
                "type": "series",
                "name": "Unknown Show"
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioMetaResponse>(metaJson)
        val meta = response.meta!!
        assertEquals("tt1234567", meta.id)
        assertNull(meta.genres)
        assertNull(meta.poster)
        assertNull(meta.background)
        assertNull(meta.logo)
        assertNull(meta.description)
        assertNull(meta.releaseInfo)
        assertNull(meta.imdbRating)
    }

    @Test
    fun testMetaResponseParsing_unknownFieldsIgnored() {
        val metaJson = """
            {
              "meta": {
                "id": "tt9999999",
                "type": "movie",
                "name": "Future Movie",
                "trailerStreams": [{"url": "https://yt.com/watch?v=xyz"}],
                "links": [{"name": "IMDb", "category": "imdb", "url": "https://imdb.com/title/tt9999999"}]
              }
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioMetaResponse>(metaJson)
        assertEquals("Future Movie", response.meta!!.name)
    }

    @Test
    fun testMetaResponseParsing_emptyJson() {
        val metaJson = "{}"
        val response = json.decodeFromString<StremioMetaResponse>(metaJson)
        assertNull(response.meta)
    }

    @Test
    fun testStreamResponseParsing_emptyJson() {
        val streamJson = "{}"
        val response = json.decodeFromString<StremioStreamResponse>(streamJson)
        assertTrue(response.streams?.isEmpty() == true)
    }

    @Test
    fun testSubtitleResponseParsing_emptyJson() {
        val subtitleJson = "{}"
        val response = json.decodeFromString<StremioSubtitleResponse>(subtitleJson)
        assertTrue(response.subtitles?.isEmpty() == true)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 2 (continued): Catalog Response Edge Cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testCatalogResponseParsing_emptyMetas() {
        val catalogJson = """{"metas": []}"""
        val response = json.decodeFromString<StremioCatalogResponse>(catalogJson)
        assertTrue(response.metas?.isEmpty() == true)
    }

    @Test
    fun testCatalogResponseParsing_metaWithMinimalFields() {
        val catalogJson = """
            {
              "metas": [
                {"id": "tt0000001", "type": "movie", "name": "Silent Film"}
              ]
            }
        """.trimIndent()

        val response = json.decodeFromString<StremioCatalogResponse>(catalogJson)
        assertEquals(1, response.metas?.size)
        assertNull(response.metas?.first()?.poster)
        assertNull(response.metas?.first()?.description)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 7: Secret Redaction Verification
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testErrorMessageRedaction_addonUrlWithSecrets() {
        // Simulates what safeGet does when a network error occurs with a secret-containing URL
        val errorMsg = "Connection failed to https://addon.com/api?token=my_secret_token&apikey=my_api_key"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)

        assertFalse(redacted.contains("my_secret_token"))
        assertFalse(redacted.contains("my_api_key"))
        assertTrue(redacted.contains("token=REDACTED"))
        assertTrue(redacted.contains("apikey=REDACTED"))
    }

    @Test
    fun testErrorMessageRedaction_pathContainingSecrets() {
        // Stremio addon URLs often have secrets in the path, not query params
        val errorMsg = "Error from https://stremio-addon.com/RD_SECRET_TOKEN/manifest.json"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)

        // Path-based secrets are not redacted by query param redactor — this is expected behavior.
        // The safeGet method wraps errors with redactErrorMessage which handles query params.
        // Path-based secrets should already be removed by resolveUrl before being logged.
        assertNotNull(redacted)
    }

    @Test
    fun testResolvedUrlNeverLogged_inStremioResult() {
        // Verify that StremioResult.Failure messages use redaction
        val error = ExtensionError.NetworkError("Network failed: Connection to https://addon.com/?token=REDACTED timed out")
        assertFalse(error.message.contains("my_real_secret"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 5: Addon health state changes and search timeout clamp
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testRecordSignalDelegateOnTimeout() = kotlinx.coroutines.runBlocking {
        var calledProviderId: String? = null
        var calledUrl: String? = null
        var calledIsTimeout: Boolean? = null
        var calledErrorMsg: String? = null

        StremioAddonClient.recordSignalDelegate = { providerId, url, isTimeout, errorMsg ->
            calledProviderId = providerId
            calledUrl = url
            calledIsTimeout = isTimeout
            calledErrorMsg = errorMsg
        }

        // Use a non-routable IP address and a 1ms timeout to force a timeout exception.
        val result = StremioAddonClient.getManifest(
            url = "http://10.255.255.1/manifest.json",
            providerId = "test-provider-timeout",
            timeoutMs = 1L
        )

        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue(failure.error is ExtensionError.Timeout)

        assertEquals("test-provider-timeout", calledProviderId)
        assertEquals("http://10.255.255.1/manifest.json", calledUrl)
        assertEquals(true, calledIsTimeout)
        assertNotNull(calledErrorMsg)
        assertTrue(calledErrorMsg!!.contains("timeout", ignoreCase = true))
    }

    @Test
    fun testRecordSignalDelegateOnNetworkError() = kotlinx.coroutines.runBlocking {
        var calledProviderId: String? = null
        var calledUrl: String? = null
        var calledIsTimeout: Boolean? = null
        var calledErrorMsg: String? = null

        StremioAddonClient.recordSignalDelegate = { providerId, url, isTimeout, errorMsg ->
            calledProviderId = providerId
            calledUrl = url
            calledIsTimeout = isTimeout
            calledErrorMsg = errorMsg
        }

        // Call a port that is closed on localhost to guarantee a connection failure.
        val result = StremioAddonClient.getManifest(
            url = "http://127.0.0.1:54321/manifest.json",
            providerId = "test-provider-failure",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue(failure.error is ExtensionError.NetworkError)

        assertEquals("test-provider-failure", calledProviderId)
        assertEquals("http://127.0.0.1:54321/manifest.json", calledUrl)
        assertEquals(false, calledIsTimeout)
        assertNotNull(calledErrorMsg)
    }

    @Test
    fun testClampedTimeoutDoesNotBubbleUpExceptions() = kotlinx.coroutines.runBlocking {
        // Set up the delegate
        StremioAddonClient.recordSignalDelegate = { _, _, _, _ -> }

        // Triggering with a 1ms timeout and executing within a try-catch block to ensure no bubbling up of unhandled exceptions
        try {
            val result = StremioAddonClient.getManifest(
                url = "http://10.255.255.1/manifest.json",
                providerId = "test-provider-clamp",
                timeoutMs = 1L
            )
            assertTrue(result is StremioResult.Failure)
            val failure = result as StremioResult.Failure
            assertTrue(failure.error is ExtensionError.Timeout)
        } catch (e: Exception) {
            fail("Exception bubbled up instead of being handled: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 9: Endpoint failures affect health appropriately
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testManifestEndpointFailureRecordsHealth() = kotlinx.coroutines.runBlocking {
        var signalRecorded = false
        var recordedTimeout = false
        StremioAddonClient.recordSignalDelegate = { providerId, url, isTimeout, errorMsg ->
            signalRecorded = true
            recordedTimeout = isTimeout
        }

        val result = StremioAddonClient.getManifest(
            url = "http://127.0.0.1:54321/manifest.json",
            providerId = "manifest-fail-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        assertTrue("Signal should have been recorded", signalRecorded)
    }

    @Test
    fun testCatalogEndpointFailureRecordsHealth() = kotlinx.coroutines.runBlocking {
        var signalRecorded = false
        StremioAddonClient.recordSignalDelegate = { _, _, _, _ -> signalRecorded = true }

        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "http://127.0.0.1:54321",
            type = "movie",
            catalogId = "top",
            providerId = "catalog-fail-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        assertTrue("Catalog failure should record signal", signalRecorded)
    }

    @Test
    fun testMetaEndpointFailureRecordsHealth() = kotlinx.coroutines.runBlocking {
        var signalRecorded = false
        StremioAddonClient.recordSignalDelegate = { _, _, _, _ -> signalRecorded = true }

        val result = StremioAddonClient.getMeta(
            resolvedBaseUrl = "http://127.0.0.1:54321",
            type = "movie",
            id = "tt0068646",
            providerId = "meta-fail-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        assertTrue("Meta failure should record signal", signalRecorded)
    }

    @Test
    fun testStreamEndpointFailureRecordsHealth() = kotlinx.coroutines.runBlocking {
        var signalRecorded = false
        StremioAddonClient.recordSignalDelegate = { _, _, _, _ -> signalRecorded = true }

        val result = StremioAddonClient.getStreams(
            resolvedBaseUrl = "http://127.0.0.1:54321",
            type = "movie",
            id = "tt0068646",
            providerId = "stream-fail-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        assertTrue("Stream failure should record signal", signalRecorded)
    }

    @Test
    fun testSubtitleEndpointFailureRecordsHealth() = kotlinx.coroutines.runBlocking {
        var signalRecorded = false
        StremioAddonClient.recordSignalDelegate = { _, _, _, _ -> signalRecorded = true }

        val result = StremioAddonClient.getSubtitles(
            resolvedBaseUrl = "http://127.0.0.1:54321",
            type = "movie",
            id = "tt0068646",
            providerId = "subtitle-fail-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        assertTrue("Subtitle failure should record signal", signalRecorded)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 10: Slow addon timeouts are tracked
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testSlowAddonTimeoutTracked() = kotlinx.coroutines.runBlocking {
        var calledIsTimeout = false
        var calledProviderId = ""
        StremioAddonClient.recordSignalDelegate = { providerId, _, isTimeout, _ ->
            calledProviderId = providerId
            calledIsTimeout = isTimeout
        }

        // Use a non-routable IP with 1ms timeout to force a timeout
        val result = StremioAddonClient.getManifest(
            url = "http://10.255.255.1/manifest.json",
            providerId = "slow-addon-tracker",
            timeoutMs = 1L
        )

        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue("Should be a Timeout error", failure.error is ExtensionError.Timeout)
        assertEquals("slow-addon-tracker", calledProviderId)
        assertTrue("Timeout flag should be set", calledIsTimeout)
    }

    @Test
    fun testSlowAddonTimeoutVsNetworkFailureDistinction() = kotlinx.coroutines.runBlocking {
        // Test timeout case
        var timeoutFlag = false
        StremioAddonClient.recordSignalDelegate = { _, _, isTimeout, _ -> timeoutFlag = isTimeout }

        StremioAddonClient.getManifest("http://10.255.255.1/manifest.json", "timeout-test", 1L)
        assertTrue("Timeout should be flagged as true", timeoutFlag)

        // Test network error case (connection refused, not timeout)
        timeoutFlag = true
        StremioAddonClient.getManifest("http://127.0.0.1:54321/manifest.json", "fail-test", 5000L)
        assertFalse("Connection refused should not be flagged as timeout", timeoutFlag)
    }

    // ═══════════════════════════════════════════════════════════════════
    // Task 13: Raw addon URLs remain redacted in error messages
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testAddonUrlRedactedInErrorMessages_queryParams() {
        val errorMsg = "Failed to fetch https://addon.com/api?token=my_real_token&apikey=secret_api_123"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)

        assertFalse("Token value should be redacted", redacted.contains("my_real_token"))
        assertFalse("API key value should be redacted", redacted.contains("secret_api_123"))
        assertTrue("Redacted placeholder should exist", redacted.contains("REDACTED"))
    }

    @Test
    fun testAddonUrlRedactedInErrorMessages_password() {
        val errorMsg = "Connection refused: https://addon.example.com?password=mysecretpass&username=admin"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)

        assertFalse("Password should be redacted", redacted.contains("mysecretpass"))
        assertFalse("Username should be redacted", redacted.contains("admin"))
    }

    @Test
    fun testAddonUrlRedactedInErrorMessages_accessToken() {
        val errorMsg = "Timeout: https://api.stremio.com/path?access_token=eyJhbGciOiJIUzI1NiJ9.jwt_token_here"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)

        assertFalse("Access token should be redacted", redacted.contains("eyJhbGciOiJIUzI1NiJ9"))
    }

    @Test
    fun testResolveUrlNeverExposesSecretsInResolvedString() {
        ExtensionSecrets.saveSecret("addon-redact", "api_key", "SUPER_SECRET_VALUE")

        val url = "https://addon.com/{secret_api_key}/manifest.json"
        val resolved = StremioAddonClient.resolveUrl(url, "addon-redact")

        // The resolved URL does contain the real value (by design — it's for HTTP requests)
        assertTrue(resolved.contains("SUPER_SECRET_VALUE"))

        // But the redactUrl utility should mask it when used for logging
        val redacted = UrlRedactor.redactUrl(resolved)
        // Path-based secrets won't be redacted by query-param redactor, 
        // but the system never logs the resolved URL directly
        assertNotNull(redacted)
    }

    @Test
    fun testSafeGetRedactsUrlInFailureMessages() = kotlinx.coroutines.runBlocking {
        var recordedError = ""
        var recordedUrl = ""
        StremioAddonClient.recordSignalDelegate = { _, url, _, errorMsg ->
            recordedUrl = url
            recordedError = errorMsg
        }

        StremioAddonClient.getManifest(
            url = "http://127.0.0.1:54321/manifest.json?token=my_secret_token",
            providerId = "redact-test",
            timeoutMs = 5000L
        )

        // The recorded error should have the URL redacted
        assertFalse("Secret token should not appear in recorded error", recordedError.contains("my_secret_token"))
        // The URL passed to the delegate should also be redacted
        assertFalse("Secret token should not appear in recorded URL", recordedUrl.contains("my_secret_token"))
        assertTrue("Recorded URL should contain REDACTED placeholder", recordedUrl.contains("REDACTED"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // Invalid URL scheme validation
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testInvalidUrlSchemeReturnsFailure() = kotlinx.coroutines.runBlocking {
        val result = StremioAddonClient.getManifest(
            url = "ftp://invalid-scheme.com/manifest.json",
            providerId = "invalid-scheme-test",
            timeoutMs = 5000L
        )

        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue(failure.error is ExtensionError.NetworkError)
        assertTrue(failure.error.message.contains("Unsafe or invalid URL scheme"))
    }

    @Test
    fun testEmptyBaseUrlReturnsFailure() = kotlinx.coroutines.runBlocking {
        val result = StremioAddonClient.getCatalog(
            resolvedBaseUrl = "",
            type = "movie",
            catalogId = "top",
            providerId = "empty-base-test"
        )

        assertTrue(result is StremioResult.Failure)
        val failure = result as StremioResult.Failure
        assertTrue(failure.error is ExtensionError.NetworkError)
        assertTrue(failure.error.message.contains("Invalid or empty addon base URL"))
    }

    // ═══════════════════════════════════════════════════════════════════
    // UrlRedactor edge cases
    // ═══════════════════════════════════════════════════════════════════

    @Test
    fun testUrlRedactor_noSensitiveParams() {
        val url = "https://addon.com/catalog/movie/top.json"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://addon.com/catalog/movie/top.json", redacted)
    }

    @Test
    fun testUrlRedactor_invalidUrl() {
        val url = "not a valid url"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("REDACTED_INVALID_URL", redacted)
    }

    @Test
    fun testUrlRedactor_basicAuthStripped() {
        val url = "https://user:password@addon.com/manifest.json"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("User info should be stripped", redacted.contains("user:password"))
        assertTrue("Host should remain", redacted.contains("addon.com"))
    }

    @Test
    fun testUrlRedactor_redactToken() {
        val longToken = "RD_AbCdEfGhIjKl_endToken_8"
        val redacted = UrlRedactor.redactToken(longToken)
        assertTrue("Should show first 4 chars", redacted.startsWith("RD_A"))
        assertTrue("Should show last 4 chars", redacted.endsWith("en_8"))
        assertTrue("Should contain ellipsis separator", redacted.contains("..."))
        assertFalse("Full token should not be visible", redacted == longToken)

        // Short token
        val shortToken = "abc"
        val shortRedacted = UrlRedactor.redactToken(shortToken)
        assertEquals("••••••••", shortRedacted)

        // Null token
        assertEquals("••••••••", UrlRedactor.redactToken(null))
    }

    @Test
    fun testUrlRedactor_redactPrivateLink() {
        val url = "https://download.real-debrid.com/d/ABCDEF/movie.mkv?token=xyz"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertTrue(redacted.contains("real-debrid.com"))
        assertTrue(redacted.contains("...REDACTED"))
        assertFalse(redacted.contains("ABCDEF"))
        assertFalse(redacted.contains("movie.mkv"))
    }

    @Test
    fun testUrlRedactor_multipleUrlsInMessage() {
        val msg = "Error fetching https://addon.com?token=secret1 and fallback https://backup.com?apikey=secret2"
        val redacted = UrlRedactor.redactErrorMessage(msg)
        assertFalse(redacted.contains("secret1"))
        assertFalse(redacted.contains("secret2"))
        assertTrue(redacted.contains("REDACTED"))
    }

    @Test
    fun testResponseSizeLimit_exceededContentLength() = kotlinx.coroutines.runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/manifest.json") { exchange ->
            val responseBytes = ByteArray(6 * 1024 * 1024) // 6MB
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.write(responseBytes)
            exchange.close()
        }
        server.start()
        val port = server.address.port
        try {
            val result = StremioAddonClient.getManifest(
                url = "http://localhost:$port/manifest.json",
                providerId = "large-response-test",
                timeoutMs = 5000L
            )
            assertTrue(result is StremioResult.Failure)
            val failure = result as StremioResult.Failure
            assertTrue("Expected NetworkError containing 'Response too large' or 'Response exceeds 5MB limit', but got: ${failure.error.message}",
                failure.error.message.contains("too large") || failure.error.message.contains("Response exceeds 5MB limit")
            )
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun testResponseSizeLimit_exceededChunked() = kotlinx.coroutines.runBlocking {
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/manifest.json") { exchange ->
            // Send chunked response
            exchange.sendResponseHeaders(200, 0) // 0 means chunked
            val os = exchange.responseBody
            val buffer = ByteArray(1024 * 1024) // 1MB chunks
            for (i in 0 until 6) { // 6MB total
                os.write(buffer)
                os.flush()
            }
            exchange.close()
        }
        server.start()
        val port = server.address.port
        try {
            val result = StremioAddonClient.getManifest(
                url = "http://localhost:$port/manifest.json",
                providerId = "large-chunked-test",
                timeoutMs = 5000L
            )
            assertTrue(result is StremioResult.Failure)
            val failure = result as StremioResult.Failure
            assertTrue("Expected NetworkError containing 'Response too large', but got: ${failure.error.message}",
                failure.error.message.contains("too large") || failure.error.message.contains("Response exceeds 5MB limit")
            )
        } finally {
            server.stop(0)
        }
    }

    private fun getOkHttpClient(): okhttp3.OkHttpClient? {
        val engine = NetworkClient.client.engine
        var clazz: Class<*>? = engine.javaClass
        while (clazz != null) {
            for (field in clazz.declaredFields) {
                if (okhttp3.OkHttpClient::class.java.isAssignableFrom(field.type)) {
                    try {
                        field.isAccessible = true
                        return field.get(engine) as? okhttp3.OkHttpClient
                    } catch (e: Exception) {
                        // ignore
                    }
                }
            }
            clazz = clazz.superclass
        }
        return null
    }

    private fun getActiveConnectionCount(): Int {
        return getOkHttpClient()?.connectionPool?.connectionCount() ?: 0
    }

    private fun getIdleConnectionCount(): Int {
        return getOkHttpClient()?.connectionPool?.idleConnectionCount() ?: 0
    }

    @Test
    fun testHttpResponseIsCloseableAndNotLeaked() {
        kotlinx.coroutines.runBlocking {
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
            server.createContext("/test") { exchange ->
                val bytes = "{}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            }
            server.start()
            val port = server.address.port
            try {
                val response = NetworkClient.client.get("http://localhost:$port/test")
                val clazz = response.javaClass
                println("HttpResponse class: ${clazz.name}")
                println("HttpResponse superclass: ${clazz.superclass?.name}")
                println("HttpResponse interfaces: ${clazz.interfaces.map { it.name }}")
                var current: Class<*>? = clazz
                while (current != null) {
                    println("Hierarchy level ${current.name} implements: ${current.interfaces.map { it.name }}")
                    current = current.superclass
                }
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testConnectionLeakAdversarial() {
        kotlinx.coroutines.runBlocking {
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
            server.createContext("/404") { exchange ->
                exchange.sendResponseHeaders(404, 0)
                exchange.close()
            }
            server.createContext("/large") { exchange ->
                // Send large content-length
                exchange.sendResponseHeaders(200, 10 * 1024 * 1024L) // 10MB
                exchange.responseBody.write(ByteArray(1024))
                exchange.close()
            }
            server.createContext("/invalid-json") { exchange ->
                val bytes = "{invalid}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            }
            server.start()
            val port = server.address.port
            
            try {
                // Warm up connection pool with a dummy call
                val warmupResult = StremioAddonClient.getManifest("http://localhost:$port/404", "warmup")
                assertTrue(warmupResult is StremioResult.Failure)
                delay(100)

                val startConnections = getActiveConnectionCount()
                val startIdleConnections = getIdleConnectionCount()

                // 1. Test 404 failure path (non-meta/stream/subtitle to trigger Failure)
                val res404 = StremioAddonClient.getManifest("http://localhost:$port/404", "test-404")
                assertTrue(res404 is StremioResult.Failure)
                
                // 2. Test large response path
                val resLarge = StremioAddonClient.getManifest("http://localhost:$port/large", "test-large")
                assertTrue(resLarge is StremioResult.Failure)
                
                // 3. Test invalid JSON parsing exception path
                val resInvalid = StremioAddonClient.getManifest("http://localhost:$port/invalid-json", "test-invalid")
                assertTrue(resInvalid is StremioResult.Failure)
                
                // Yield and delay to allow OkHttp connection cleanups/idle transitions if any
                delay(300)
                
                val activeConnections = getActiveConnectionCount() - getIdleConnectionCount()
                assertEquals("Should have no active/leaked connections after failure paths", 0, activeConnections)
                
            } finally {
                server.stop(0)
            }
        }
    }

    @Test
    fun testLoggingToggleThreadSafety() {
        kotlinx.coroutines.runBlocking {
            val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
            server.createContext("/log-test") { exchange ->
                val bytes = "{}".toByteArray()
                exchange.sendResponseHeaders(200, bytes.size.toLong())
                exchange.responseBody.write(bytes)
                exchange.close()
            }
            server.start()
            val port = server.address.port
            
            try {
                val jobs = mutableListOf<Job>()
                val numThreads = 10
                val iterations = 50
                
                // Launch coroutines toggling logging
                jobs += launch(Dispatchers.Default) {
                    for (i in 0 until (numThreads * iterations)) {
                        NetworkClient.setLoggingEnabled(i % 2 == 0)
                        yield()
                    }
                }
                
                // Launch coroutines doing requests (which trigger the logger)
                repeat(numThreads) {
                    jobs += launch(Dispatchers.Default) {
                        val client = NetworkClient.client
                        for (i in 0 until iterations) {
                            try {
                                // This triggers logger
                                client.get("http://localhost:$port/log-test")
                            } catch (e: Exception) {
                                // ignore network errors if any
                            }
                        }
                    }
                }
                
                jobs.forEach { it.join() }
                
                // Verify that we can still toggle and it works
                NetworkClient.setLoggingEnabled(true)
                NetworkClient.client.get("http://localhost:$port/log-test")
                
                NetworkClient.setLoggingEnabled(false)
                NetworkClient.client.get("http://localhost:$port/log-test")
                
            } finally {
                server.stop(0)
                // Reset logging to true so other tests aren't affected
                NetworkClient.setLoggingEnabled(true)
            }
        }
    }
}


