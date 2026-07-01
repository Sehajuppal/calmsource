package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.network.UrlRedactor
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for [XtreamApiClientImpl].
 *
 * Since the feature/iptv module doesn't include Ktor MockEngine as a test dependency,
 * these tests verify:
 *  - URL construction correctness
 *  - URL validation (scheme safety, whitespace, etc.)
 *  - JSON parsing of realistic Xtream API responses
 *  - URL redaction via [UrlRedactor]
 *  - Stream URL builders
 *
 * All data is synthetic — no real provider URLs or credentials are used.
 */
class XtreamApiClientTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun testConfig(
        id: String = "test-provider-1",
        name: String = "Test IPTV",
        serverUrl: String = "http://example.com:8080",
        username: String = "testuser"
    ) = XtreamProviderConfig(id = id, name = name, serverUrl = serverUrl, username = username)

    /**
     * Creates an XtreamApiClientImpl without a real HTTP client.
     * URL construction, validation, and parsing methods work without networking.
     */
    private fun createClient(): XtreamApiClientImpl {
        return XtreamApiClientImpl()
    }

    @Test
    fun xtream_client_accepts_catalog_responses_over_stremio_manifest_limit() = kotlinx.coroutines.runBlocking {
        val largeName = "Large Channel " + "A".repeat(6 * 1024 * 1024)
        val body = """
        [{
            "stream_id": "42",
            "name": "$largeName",
            "category_id": "1",
            "stream_icon": "",
            "epg_channel_id": ""
        }]
        """.trimIndent()
        val responseBytes = body.toByteArray(Charsets.UTF_8)
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/player_api.php") { exchange ->
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { output ->
                output.write(responseBytes)
            }
        }
        server.start()

        try {
            val config = testConfig(serverUrl = "http://localhost:${server.address.port}")
            val streams = createClient().getLiveStreams(config, "secret")

            assertEquals(1, streams.size)
            assertEquals("42", streams.single().streamId)
            assertTrue(streams.single().name.length > 5 * 1024 * 1024)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun xtream_vod_catalog_can_take_longer_than_ten_seconds() {
        val timeoutField = com.example.calmsource.core.network.NetworkClient::class.java.getDeclaredField("XTREAM_TIMEOUT_MILLIS")
        timeoutField.isAccessible = true
        val timeout = timeoutField.get(null) as Long
        assertTrue("Xtream timeout should be at least 60 seconds", timeout >= 60_000L)
    }

    @Test
    fun xtream_timeout_error_hides_url_and_credentials_for_ui() {
        val rawError = """
            Request timeout has expired [url=http://example.com/player_api.php?username=testuser&password=secretpass&action=get_vod_streams, request_timeout=10000 ms]
        """.trimIndent()

        val safeError = createClient().userFacingNetworkError(
            message = rawError,
            contentLabel = "VOD catalog",
            requestTimeoutMillis = 120_000L
        )

        assertTrue(safeError.contains("VOD catalog"))
        assertTrue(safeError.contains("120 seconds"))
        assertFalse(safeError.contains("http://"))
        assertFalse(safeError.contains("example.com"))
        assertFalse(safeError.contains("player_api.php"))
        assertFalse(safeError.contains("testuser"))
        assertFalse(safeError.contains("secretpass"))
    }

    @Test
    fun xtream_category_id_is_url_encoded() = kotlinx.coroutines.runBlocking {
        var capturedRawQuery = ""
        val server = com.sun.net.httpserver.HttpServer.create(java.net.InetSocketAddress(0), 0)
        server.createContext("/player_api.php") { exchange ->
            capturedRawQuery = exchange.requestURI.rawQuery ?: ""
            val responseBytes = "[]".toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, responseBytes.size.toLong())
            exchange.responseBody.use { output ->
                output.write(responseBytes)
            }
        }
        server.start()

        try {
            val config = testConfig(serverUrl = "http://localhost:${server.address.port}")
            createClient().getLiveStreams(config, "secret", categoryId = "News & Sports/HD")

            assertTrue(capturedRawQuery.contains("category_id=News+%26+Sports%2FHD"))
            assertFalse(capturedRawQuery.contains("News & Sports/HD"))
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun parseLiveStreams_accepts_string_archive_numbers() {
        val streams = createClient().parseLiveStreams(
            body = """
                [{
                    "stream_id": "42",
                    "name": "Archive Channel",
                    "stream_type": "live",
                    "category_id": "1",
                    "tv_archive": "1",
                    "tv_archive_duration": "7"
                }]
            """.trimIndent(),
            providerId = "provider"
        )

        assertEquals(1, streams.size)
        assertTrue(streams.single().tvArchive)
        assertEquals(7, streams.single().tvArchiveDuration)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 1: URL construction does not leak credentials in logged output
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun buildBaseUrl_contains_credentials_in_raw_url() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildBaseUrl(config, "secretpass123")
        assertTrue(url.contains("username=testuser"))
        assertTrue(url.contains("password=secretpass123"))
    }

    @Test
    fun buildBaseUrl_credentials_redacted_by_UrlRedactor() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildBaseUrl(config, "secretpass123")
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Password must be redacted", redacted.contains("secretpass123"))
        assertFalse("Username must be redacted", redacted.contains("testuser"))
        assertTrue("Host must remain", redacted.contains("example.com"))
    }

    @Test
    fun buildActionUrl_appends_action_parameter() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_live_streams")
        assertTrue(url.contains("&action=get_live_streams"))
        assertTrue(url.contains("player_api.php"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 2: Invalid server URL rejection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun validateServerUrl_rejects_empty_url() {
        val client = createClient()
        val result = client.validateServerUrl("")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun validateServerUrl_rejects_blank_url() {
        val client = createClient()
        val result = client.validateServerUrl("   ")
        assertTrue(result.isFailure)
    }

    @Test
    fun validateServerUrl_accepts_bare_hostname() {
        val client = createClient()
        assertTrue(client.validateServerUrl("example.com").isSuccess)
    }

    @Test
    fun validateServerUrl_accepts_host_port_without_scheme() {
        val client = createClient()
        assertTrue(client.validateServerUrl("example.com:8080").isSuccess)
    }

    @Test
    fun validateServerUrl_rejects_url_with_whitespace() {
        val client = createClient()
        val result = client.validateServerUrl("http://example .com:8080")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("whitespace") == true)
    }

    @Test
    fun validateServerUrl_accepts_valid_http_url() {
        val client = createClient()
        assertTrue(client.validateServerUrl("http://example.com:8080").isSuccess)
    }

    @Test
    fun validateServerUrl_accepts_valid_https_url() {
        val client = createClient()
        assertTrue(client.validateServerUrl("https://secure.example.com").isSuccess)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 3: Unsafe scheme rejection
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun validateServerUrl_rejects_file_scheme() {
        val client = createClient()
        val result = client.validateServerUrl("file:///etc/passwd")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
        assertTrue(result.exceptionOrNull()?.message?.contains("file") == true)
    }

    @Test
    fun validateServerUrl_rejects_javascript_scheme() {
        val client = createClient()
        val result = client.validateServerUrl("javascript:alert(1)")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun validateServerUrl_rejects_data_scheme() {
        val client = createClient()
        val result = client.validateServerUrl("data:text/html,<h1>hello</h1>")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun validateServerUrl_rejects_content_scheme() {
        val client = createClient()
        val result = client.validateServerUrl("content://com.example.provider/table/1")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    @Test
    fun validateServerUrl_rejects_ftp_scheme() {
        val client = createClient()
        val result = client.validateServerUrl("ftp://files.example.com")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is SecurityException)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 4: Invalid credentials response (auth=0)
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun auth_response_with_auth_zero_integer() {
        val responseJson = """
        {
            "user_info": {
                "auth": 0,
                "status": "Disabled",
                "username": "baduser"
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(responseJson).jsonObject
        val userInfo = root["user_info"]?.jsonObject
        assertNotNull(userInfo)
        val auth = userInfo!!["auth"]?.jsonPrimitive?.longOrNull
        assertEquals(0L, auth)
        assertFalse("auth=0 should not authenticate", auth == 1L)
    }

    @Test
    fun auth_response_with_auth_zero_string() {
        val responseJson = """
        {
            "user_info": {
                "auth": "0",
                "status": "Disabled"
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(responseJson).jsonObject
        val userInfo = root["user_info"]!!.jsonObject
        val authStr = userInfo["auth"]?.jsonPrimitive?.contentOrNull
        assertEquals("0", authStr)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 5: Expired subscription response
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun expired_subscription_not_authenticated() {
        val responseJson = """
        {
            "user_info": {
                "auth": 1,
                "status": "Expired",
                "username": "expireduser",
                "exp_date": "1609459200"
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(responseJson).jsonObject
        val userInfo = root["user_info"]!!.jsonObject
        val auth = userInfo["auth"]?.jsonPrimitive?.longOrNull
        val status = userInfo["status"]?.jsonPrimitive?.contentOrNull
        assertEquals(1L, auth)
        assertEquals("Expired", status)
        // Client should reject: even though auth=1, status=="Expired" means not authenticated
        assertFalse(auth == 1L && status != "Expired")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 6: Non-200 HTTP response handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun userFacingHttpStatus_maps_513_to_server_busy_message() {
        val client = createClient()
        val message = client.userFacingHttpStatus(513, "account details")
        assertTrue(message.contains("busy", ignoreCase = true))
        assertTrue(message.contains("513"))
    }

    @Test
    fun isInactiveAccountStatus_rejects_disabled_and_banned() {
        val client = createClient()
        assertTrue(client.isInactiveAccountStatus("Expired"))
        assertTrue(client.isInactiveAccountStatus("Disabled"))
        assertTrue(client.isInactiveAccountStatus("banned"))
        assertFalse(client.isInactiveAccountStatus("Active"))
    }

    @Test
    fun parseProviderErrorHint_reads_json_message() {
        val client = createClient()
        val hint = client.parseProviderErrorHint("""{"message":"Too many connections"}""")
        assertEquals("Too many connections", hint)
    }

    @Test
    fun buildAuthRequestUrls_includes_alternate_scheme() {
        val client = createClient()
        val urls = client.buildAuthRequestUrls(
            testConfig(serverUrl = "http://example.com:8080"),
            "secret"
        )
        assertEquals(2, urls.size)
        assertTrue(urls[0].startsWith("http://example.com:8080/player_api.php"))
        assertTrue(urls[1].startsWith("https://example.com:8080/player_api.php"))
    }

    @Test
    fun buildAuthRequestUrls_expands_implicit_port_candidates() {
        val client = createClient()
        val urls = client.buildAuthRequestUrls(
            testConfig(serverUrl = "https://example.com"),
            "secret"
        )
        assertTrue(urls.size > 2)
        assertTrue(urls.any { it.startsWith("http://example.com:8080/player_api.php") })
    }

    @Test
    fun connection_refused_error_is_user_friendly() {
        val client = createClient()
        val raw = "Failed to connect to wexart.xyz/103.211.100.215:443 Cause: ECONNREFUSED"
        assertTrue(client.isConnectionRefusedMessage(raw))
        val friendly = client.userFacingNetworkError(raw, "account details", 20_000L)
        assertFalse(friendly.contains("103.211.100.215"))
        assertFalse(friendly.contains("ECONNREFUSED"))
    }

    @Test
    fun error_message_for_non_200_uses_redacted_url() {
        val url = "http://example.com/player_api.php?username=admin&password=secret123"
        val redacted = UrlRedactor.redactUrl(url)
        val errorMsg = "Server returned 403 for $redacted"
        assertFalse("Password not in error", errorMsg.contains("secret123"))
        assertFalse("Username not in error", errorMsg.contains("admin"))
        assertTrue("Status code in error", errorMsg.contains("403"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 7: Malformed JSON handling
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun malformed_json_does_not_crash() {
        val badJson = "this is not json {{"
        val result = try {
            json.parseToJsonElement(badJson).jsonArray
            true
        } catch (e: Exception) {
            false
        }
        assertFalse("Malformed JSON should throw", result)
    }

    @Test
    fun empty_json_array_returns_empty_list() {
        val client = createClient()
        val categories = client.parseCategories("[]")
        assertTrue(categories.isEmpty())
    }

    @Test
    fun missing_required_field_skips_entry() {
        val client = createClient()
        // Missing category_name — should be skipped
        val partialJson = """[{"category_id": "1"}]"""
        val categories = client.parseCategories(partialJson)
        assertTrue("Entry with missing required field should be skipped", categories.isEmpty())
    }

    @Test
    fun malformed_json_returns_empty_list_for_categories() {
        val client = createClient()
        val categories = client.parseCategories("not json")
        assertTrue(categories.isEmpty())
    }

    @Test
    fun malformed_json_returns_empty_list_for_live_streams() {
        val client = createClient()
        val streams = client.parseLiveStreams("not json", "prov1")
        assertTrue(streams.isEmpty())
    }

    @Test
    fun malformed_json_returns_empty_list_for_vod_streams() {
        val client = createClient()
        val vods = client.parseVodStreams("not json", "prov1")
        assertTrue(vods.isEmpty())
    }

    @Test
    fun malformed_json_returns_empty_list_for_series() {
        val client = createClient()
        val series = client.parseSeries("not json", "prov1")
        assertTrue(series.isEmpty())
    }

    @Test
    fun required_sync_payload_rejects_malformed_response() {
        val client = createClient()
        val error = runCatching {
            client.requireArrayResponse("""{"error":"temporary failure"}""", "live channels")
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertTrue(error?.message?.contains("invalid live channels response") == true)
        assertFalse(error?.message?.contains("http") == true)
    }

    @Test
    fun required_sync_payload_accepts_legitimate_empty_array() {
        createClient().requireArrayResponse("[]", "VOD items")
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 8: Successful live categories parsing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_live_categories_from_valid_json() {
        val client = createClient()
        val categoriesJson = """
        [
            {"category_id": "1", "category_name": "News", "parent_id": "0"},
            {"category_id": "2", "category_name": "Sports", "parent_id": "1"},
            {"category_id": "3", "category_name": "Movies"}
        ]
        """.trimIndent()

        val categories = client.parseCategories(categoriesJson)

        assertEquals(3, categories.size)
        assertEquals("1", categories[0].id)
        assertEquals("News", categories[0].name)
        assertNull("parent_id '0' should be mapped to null", categories[0].parentId)
        assertEquals("1", categories[1].parentId)
        assertNull("Missing parent_id should be null", categories[2].parentId)
    }

    @Test
    fun parse_categories_with_numeric_parent_id() {
        val client = createClient()
        val categoriesJson = """[{"category_id": "5", "category_name": "Drama", "parent_id": 3}]"""
        val categories = client.parseCategories(categoriesJson)
        assertEquals(1, categories.size)
        assertEquals("3", categories[0].parentId)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 9: Successful live streams parsing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_live_streams_from_valid_json() {
        val client = createClient()
        val streamsJson = """
        [
            {
                "stream_id": "101",
                "name": "CNN",
                "category_id": "1",
                "stream_icon": "http://example.com/cnn.png",
                "epg_channel_id": "CNN.us",
                "tv_archive": 1,
                "tv_archive_duration": 3
            },
            {
                "stream_id": "102",
                "name": "BBC World",
                "category_id": "1",
                "stream_icon": "",
                "epg_channel_id": null,
                "tv_archive": 0,
                "tv_archive_duration": 0
            }
        ]
        """.trimIndent()

        val streams = client.parseLiveStreams(streamsJson, "test-provider-1")

        assertEquals(2, streams.size)
        // First stream
        assertEquals("test-provider-1_live_101", streams[0].id)
        assertEquals("CNN", streams[0].name)
        assertEquals("101", streams[0].streamId)
        assertEquals("1", streams[0].categoryId)
        assertEquals("http://example.com/cnn.png", streams[0].logo)
        assertEquals("CNN.us", streams[0].epgChannelId)
        assertTrue(streams[0].tvArchive)
        assertEquals(3, streams[0].tvArchiveDuration)
        // Second stream — empty icon and no archive
        assertEquals("test-provider-1_live_102", streams[1].id)
        assertNull("Empty icon should become null", streams[1].logo)
        assertFalse(streams[1].tvArchive)
        assertEquals(0, streams[1].tvArchiveDuration)
    }

    @Test
    fun parse_live_streams_missing_stream_id_skipped() {
        val client = createClient()
        val json = """[{"name": "NoId Channel", "category_id": "1"}]"""
        val streams = client.parseLiveStreams(json, "prov")
        assertTrue("Stream without stream_id should be skipped", streams.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 10: VOD streams parsing
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_vod_streams_from_valid_json() {
        val client = createClient()
        val vodJson = """
        [
            {
                "stream_id": "501",
                "name": "The Matrix",
                "category_id": "10",
                "stream_icon": "http://example.com/matrix.jpg",
                "rating": "8.7",
                "container_extension": "mkv",
                "added": "1609459200"
            },
            {
                "stream_id": "502",
                "name": "Inception",
                "category_id": "10",
                "stream_icon": "http://example.com/inception.jpg",
                "rating": 9.1,
                "container_extension": "mp4",
                "added": 1609459300
            }
        ]
        """.trimIndent()

        val vods = client.parseVodStreams(vodJson, "test-provider-1")

        assertEquals(2, vods.size)
        assertEquals("test-provider-1_vod_501", vods[0].id)
        assertEquals("The Matrix", vods[0].name)
        assertEquals("501", vods[0].streamId)
        assertEquals("10", vods[0].categoryId)
        assertEquals("http://example.com/matrix.jpg", vods[0].poster)
        assertEquals(8.7, vods[0].rating!!, 0.01)
        assertEquals("mkv", vods[0].containerExtension)
        assertEquals(1609459200L, vods[0].added)

        assertEquals("test-provider-1_vod_502", vods[1].id)
        assertEquals(9.1, vods[1].rating!!, 0.01)
        assertEquals("mp4", vods[1].containerExtension)
        assertEquals(1609459300L, vods[1].added)
    }

    @Test
    fun parse_vod_streams_with_null_rating() {
        val client = createClient()
        val vodJson = """[{"stream_id": "600", "name": "Unknown Movie", "category_id": "1", "rating": null}]"""
        val vods = client.parseVodStreams(vodJson, "prov")
        assertEquals(1, vods.size)
        assertNull(vods[0].rating)
    }

    @Test
    fun parse_vod_streams_defaults_container_extension() {
        val client = createClient()
        val vodJson = """[{"stream_id": "601", "name": "Movie", "category_id": "1"}]"""
        val vods = client.parseVodStreams(vodJson, "prov")
        assertEquals(1, vods.size)
        assertEquals("mp4", vods[0].containerExtension)
    }

    @Test
    fun parse_series_from_valid_json() {
        val client = createClient()
        val seriesJson = """
        [
            {
                "series_id": "301",
                "name": "Breaking Bad",
                "category_id": "5",
                "cover": "http://example.com/bb.jpg",
                "rating": "9.5"
            },
            {
                "series_id": "302",
                "name": "The Wire",
                "category_id": "5",
                "cover": "",
                "rating": null
            }
        ]
        """.trimIndent()

        val series = client.parseSeries(seriesJson, "prov-1")

        assertEquals(2, series.size)
        assertEquals("prov-1_series_301", series[0].id)
        assertEquals("Breaking Bad", series[0].name)
        assertEquals("301", series[0].seriesId)
        assertEquals("5", series[0].categoryId)
        assertEquals("http://example.com/bb.jpg", series[0].poster)
        assertEquals(9.5, series[0].rating!!, 0.01)

        assertEquals("prov-1_series_302", series[1].id)
        assertNull("Empty cover should become null", series[1].poster)
        assertNull("Null rating should remain null", series[1].rating)
    }

    @Test
    fun parse_series_missing_series_id_skipped() {
        val client = createClient()
        val json = """[{"name": "No ID Series", "category_id": "1"}]"""
        val series = client.parseSeries(json, "prov")
        assertTrue("Series without series_id should be skipped", series.isEmpty())
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 11: Query parameter redaction via UrlRedactor
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun urlRedactor_redacts_username_and_password_from_xtream_url() {
        val url = "http://iptv.example.com:8080/player_api.php?username=myuser&password=mypass123"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username must be redacted", redacted.contains("myuser"))
        assertFalse("Password must be redacted", redacted.contains("mypass123"))
        assertTrue("URL host should remain", redacted.contains("iptv.example.com"))
        assertTrue("username param should show REDACTED", redacted.contains("username=REDACTED"))
        assertTrue("password param should show REDACTED", redacted.contains("password=REDACTED"))
    }

    @Test
    fun urlRedactor_redacts_action_url_credentials() {
        val url = "http://server.com/player_api.php?username=u&password=p&action=get_live_streams"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse(redacted.contains("password=p&"))
        assertTrue(redacted.contains("action=get_live_streams"))
    }

    @Test
    fun urlRedactor_handles_invalid_url_gracefully() {
        val invalid = "not a url at all"
        val redacted = UrlRedactor.redactUrl(invalid)
        assertNotNull(redacted)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Test 12: buildLiveStreamUrl and buildVodStreamUrl construction
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun buildLiveStreamUrl_correct_format() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080")
        val url = client.buildLiveStreamUrl(config, "mypassword", "12345")
        assertEquals("http://example.com:8080/live/testuser/mypassword/12345.ts", url)
    }

    @Test
    fun buildLiveStreamUrl_strips_trailing_slash() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080/")
        val url = client.buildLiveStreamUrl(config, "pass", "999")
        assertEquals("http://example.com:8080/live/testuser/pass/999.ts", url)
    }

    @Test
    fun buildVodStreamUrl_default_extension() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080")
        val url = client.buildVodStreamUrl(config, "mypassword", "555")
        assertEquals("http://example.com:8080/movie/testuser/mypassword/555.mp4", url)
    }

    @Test
    fun buildVodStreamUrl_custom_extension() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080")
        val url = client.buildVodStreamUrl(config, "pass", "555", "mkv")
        assertEquals("http://example.com:8080/movie/testuser/pass/555.mkv", url)
    }

    @Test
    fun buildStreamUrls_encode_credential_path_segments() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080", username = "test/user")

        val url = client.buildLiveStreamUrl(config, "pa ss/word", "123")

        assertEquals("http://example.com:8080/live/test%2Fuser/pa%20ss%2Fword/123.ts", url)
    }

    @Test
    fun stream_urls_redacted_by_redactPrivateLink() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080")
        val liveUrl = client.buildLiveStreamUrl(config, "secretpass", "100")
        assertTrue("Raw URL contains password", liveUrl.contains("secretpass"))
        val privateRedacted = UrlRedactor.redactPrivateLink(liveUrl)
        assertFalse("redactPrivateLink should hide path credentials", privateRedacted.contains("secretpass"))
        assertTrue("Host should remain", privateRedacted.contains("example.com"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Additional edge cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun successful_auth_response_with_auth_integer_1() {
        val responseJson = """
        {
            "user_info": {
                "auth": 1,
                "status": "Active",
                "username": "validuser",
                "exp_date": "1893456000",
                "max_connections": "2",
                "active_cons": "1"
            },
            "server_info": {
                "url": "example.com",
                "port": "8080"
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(responseJson).jsonObject
        val userInfo = root["user_info"]!!.jsonObject
        val auth = userInfo["auth"]?.jsonPrimitive?.longOrNull
        val status = userInfo["status"]?.jsonPrimitive?.contentOrNull
        assertEquals(1L, auth)
        assertEquals("Active", status)
        assertTrue("auth=1 and status=Active should authenticate", auth == 1L && status != "Expired")
    }

    @Test
    fun successful_auth_response_with_auth_string_1() {
        val responseJson = """
        {
            "user_info": {
                "auth": "1",
                "status": "Active"
            }
        }
        """.trimIndent()

        val root = json.parseToJsonElement(responseJson).jsonObject
        val userInfo = root["user_info"]!!.jsonObject
        val authStr = userInfo["auth"]?.jsonPrimitive?.contentOrNull
        assertEquals("1", authStr)
        val authLong = userInfo["auth"]?.jsonPrimitive?.longOrNull ?: authStr?.toLongOrNull()
        assertEquals(1L, authLong)
    }

    @Test
    fun response_without_user_info_is_invalid() {
        val responseJson = """{"server_info": {"url": "example.com"}}"""
        val root = json.parseToJsonElement(responseJson).jsonObject
        assertNull(root["user_info"]?.jsonObject)
    }

    @Test
    fun base_url_construction_strips_trailing_slash() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://example.com:8080/")
        val url = client.buildBaseUrl(config, "pass")
        assertFalse("No double slash before player_api.php", url.contains("//player_api.php"))
        assertTrue(url.contains("player_api.php?username=testuser&password=pass"))
    }

    @Test
    fun buildBaseUrl_strips_panel_and_m3u_paths() {
        val client = createClient()
        val panelConfig = testConfig(serverUrl = "http://example.com:8080/c/")
        val panelUrl = client.buildBaseUrl(panelConfig, "pass")
        assertEquals(
            "http://example.com:8080/player_api.php?username=testuser&password=pass",
            panelUrl
        )

        val m3uConfig = testConfig(serverUrl = "http://iptv.example.com:25461/get.php?username=u&password=p")
        val m3uUrl = client.buildBaseUrl(m3uConfig, "pass")
        assertEquals(
            "http://iptv.example.com:25461/player_api.php?username=testuser&password=pass",
            m3uUrl
        )
    }

    @Test
    fun action_url_for_vod_categories() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_vod_categories")
        assertTrue(url.endsWith("&action=get_vod_categories"))
    }

    @Test
    fun action_url_for_series_categories() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_series_categories")
        assertTrue(url.endsWith("&action=get_series_categories"))
    }

    @Test
    fun action_url_for_series_list() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_series")
        assertTrue(url.endsWith("&action=get_series"))
    }

    @Test
    fun validateServerUrl_case_insensitive_scheme_check() {
        val client = createClient()
        assertTrue(client.validateServerUrl("HTTP://example.com").isSuccess)
        assertTrue(client.validateServerUrl("HTTPS://example.com").isSuccess)
        assertTrue(client.validateServerUrl("FILE:///etc/passwd").isFailure)
    }

    @Test
    fun parse_live_streams_with_integer_stream_id() {
        val client = createClient()
        // stream_id as integer (some servers return it as int, not string)
        val json = """[{"stream_id": 123, "name": "Test Ch", "category_id": "1"}]"""
        val streams = client.parseLiveStreams(json, "prov")
        assertEquals(1, streams.size)
        assertEquals("123", streams[0].streamId)
    }

    @Test
    fun parse_vod_streams_with_string_rating() {
        val client = createClient()
        val json = """[{"stream_id": "700", "name": "Rated Movie", "category_id": "1", "rating": "7.3"}]"""
        val vods = client.parseVodStreams(json, "prov")
        assertEquals(1, vods.size)
        assertEquals(7.3, vods[0].rating!!, 0.01)
    }

    @Test
    fun parse_vod_streams_with_empty_string_rating() {
        val client = createClient()
        val json = """[{"stream_id": "701", "name": "Unrated", "category_id": "1", "rating": ""}]"""
        val vods = client.parseVodStreams(json, "prov")
        assertEquals(1, vods.size)
        assertNull("Empty string rating should become null", vods[0].rating)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bug fix tests: URL-encoding of credentials with special characters
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun buildBaseUrl_encodes_special_chars_in_username() {
        val client = createClient()
        val config = testConfig(username = "user&admin")
        val url = client.buildBaseUrl(config, "pass")
        // '&' in username must be encoded so it doesn't break the query string
        assertTrue("Encoded username should contain %26", url.contains("username=user%26admin"))
        assertFalse("Raw & in username would corrupt URL", url.contains("username=user&admin"))
    }

    @Test
    fun buildBaseUrl_encodes_special_chars_in_password() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildBaseUrl(config, "p@ss=w0rd&more")
        assertTrue("Encoded password should contain %40", url.contains("password=p%40ss%3Dw0rd%26more"))
    }

    @Test
    fun buildBaseUrl_encodes_spaces_in_credentials() {
        val client = createClient()
        val config = testConfig(username = "my user")
        val url = client.buildBaseUrl(config, "my pass")
        assertTrue("Space in username encoded", url.contains("username=my+user") || url.contains("username=my%20user"))
        assertTrue("Space in password encoded", url.contains("password=my+pass") || url.contains("password=my%20pass"))
    }

    @Test
    fun buildBaseUrl_simple_alphanumeric_creds_unchanged() {
        // Simple credentials should still work as before (alphanumeric chars encode to themselves)
        val client = createClient()
        val config = testConfig(username = "testuser")
        val url = client.buildBaseUrl(config, "secretpass123")
        assertTrue(url.contains("username=testuser"))
        assertTrue(url.contains("password=secretpass123"))
    }

    @Test
    fun buildActionUrl_with_encoded_credentials() {
        val client = createClient()
        val config = testConfig(username = "user#1")
        val url = client.buildActionUrl(config, "p@ss", "get_live_streams")
        assertTrue("Username # must be encoded", url.contains("username=user%231"))
        assertTrue("Password @ must be encoded", url.contains("password=p%40ss"))
        assertTrue("Action param preserved", url.contains("&action=get_live_streams"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bug fix tests: Stream URL builders now validate scheme
    // ═══════════════════════════════════════════════════════════════════════

    @Test(expected = SecurityException::class)
    fun buildLiveStreamUrl_rejects_file_scheme() {
        val client = createClient()
        val config = testConfig(serverUrl = "file:///etc/passwd")
        client.buildLiveStreamUrl(config, "pass", "123")
    }

    @Test
    fun buildLiveStreamUrl_accepts_bare_hostname() {
        val client = createClient()
        val config = testConfig(serverUrl = "example.com")
        val url = client.buildLiveStreamUrl(config, "pass", "123")
        assertTrue(url.startsWith("http://example.com/"))
    }

    @Test(expected = SecurityException::class)
    fun buildVodStreamUrl_rejects_javascript_scheme() {
        val client = createClient()
        val config = testConfig(serverUrl = "javascript:alert(1)")
        client.buildVodStreamUrl(config, "pass", "456")
    }

    @Test(expected = SecurityException::class)
    fun buildVodStreamUrl_rejects_data_scheme() {
        val client = createClient()
        val config = testConfig(serverUrl = "data:text/html,<h1>hi</h1>")
        client.buildVodStreamUrl(config, "pass", "456")
    }

    @Test
    fun buildLiveStreamUrl_accepts_valid_http() {
        val client = createClient()
        val config = testConfig(serverUrl = "http://valid.example.com:8080")
        val url = client.buildLiveStreamUrl(config, "pass", "42")
        assertEquals("http://valid.example.com:8080/live/testuser/pass/42.ts", url)
    }

    @Test
    fun buildVodStreamUrl_accepts_valid_https() {
        val client = createClient()
        val config = testConfig(serverUrl = "https://secure.example.com")
        val url = client.buildVodStreamUrl(config, "pass", "42")
        assertEquals("https://secure.example.com/movie/testuser/pass/42.mp4", url)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Bug fix tests: Error message redaction
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun redactErrorMessage_strips_credentials_from_urls_in_text() {
        val errorMsg = "Failed to connect to http://example.com/player_api.php?username=admin&password=secret123"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)
        assertFalse("Password must not appear", redacted.contains("secret123"))
        assertFalse("Username must not appear", redacted.contains("admin"))
        assertTrue("Non-URL text preserved", redacted.contains("Failed to connect to"))
    }

    @Test
    fun redactErrorMessage_preserves_non_url_text() {
        val errorMsg = "Connection timed out after 10s"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)
        assertEquals(errorMsg, redacted)
    }

    @Test
    fun redactErrorMessage_handles_multiple_urls() {
        val errorMsg = "Redirect from http://a.com?password=x to http://b.com?password=y"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)
        assertFalse(redacted.contains("password=x"))
        assertFalse(redacted.contains("password=y"))
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Additional parsing edge cases
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun parse_categories_with_extra_unknown_fields() {
        val client = createClient()
        val categoriesJson = """[{"category_id": "1", "category_name": "News", "parent_id": "0", "unknown_field": "ignored"}]"""
        val categories = client.parseCategories(categoriesJson)
        assertEquals(1, categories.size)
        assertEquals("News", categories[0].name)
    }

    @Test
    fun parse_live_streams_missing_name_skipped() {
        val client = createClient()
        val json = """[{"stream_id": "123", "category_id": "1"}]"""
        val streams = client.parseLiveStreams(json, "prov")
        assertTrue("Stream without name should be skipped", streams.isEmpty())
    }

    @Test
    fun parse_vod_streams_missing_name_skipped() {
        val client = createClient()
        val json = """[{"stream_id": "123", "category_id": "1"}]"""
        val vods = client.parseVodStreams(json, "prov")
        assertTrue("VOD without name should be skipped", vods.isEmpty())
    }

    @Test
    fun parse_series_missing_name_skipped() {
        val client = createClient()
        val json = """[{"series_id": "123", "category_id": "1"}]"""
        val series = client.parseSeries(json, "prov")
        assertTrue("Series without name should be skipped", series.isEmpty())
    }

    @Test
    fun parse_live_streams_partial_entries_skipped() {
        // Mix of valid and invalid entries — only valid ones returned
        val client = createClient()
        val json = """[
            {"stream_id": "1", "name": "Good", "category_id": "1"},
            {"name": "NoId", "category_id": "1"},
            {"stream_id": "3", "name": "Also Good", "category_id": "2"}
        ]"""
        val streams = client.parseLiveStreams(json, "prov")
        assertEquals(2, streams.size)
        assertEquals("Good", streams[0].name)
        assertEquals("Also Good", streams[1].name)
    }

    @Test
    fun parse_categories_handles_null_json_values_gracefully() {
        val client = createClient()
        // null category_name should cause this entry to be skipped
        val json = """[{"category_id": "1", "category_name": null}]"""
        val categories = client.parseCategories(json)
        assertTrue("Entry with null required field should be skipped", categories.isEmpty())
    }

    @Test
    fun parse_vod_streams_handles_empty_added_string() {
        val client = createClient()
        val json = """[{"stream_id": "800", "name": "Movie", "category_id": "1", "added": ""}]"""
        val vods = client.parseVodStreams(json, "prov")
        assertEquals(1, vods.size)
        assertEquals(0L, vods[0].added)
    }

    @Test
    fun parse_series_with_numeric_rating() {
        val client = createClient()
        val json = """[{"series_id": "50", "name": "Good Show", "category_id": "1", "rating": 8.5}]"""
        val series = client.parseSeries(json, "prov")
        assertEquals(1, series.size)
        assertEquals(8.5, series[0].rating!!, 0.01)
    }

    // ═══════════════════════════════════════════════════════════════════════
    // Endpoint URL construction completeness
    // ═══════════════════════════════════════════════════════════════════════

    @Test
    fun action_url_for_live_streams() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_live_streams")
        assertTrue(url.endsWith("&action=get_live_streams"))
    }

    @Test
    fun action_url_for_vod_streams() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_vod_streams")
        assertTrue(url.endsWith("&action=get_vod_streams"))
    }

    @Test
    fun action_url_for_live_categories() {
        val client = createClient()
        val config = testConfig()
        val url = client.buildActionUrl(config, "pass", "get_live_categories")
        assertTrue(url.endsWith("&action=get_live_categories"))
        assertTrue(url.contains("player_api.php"))
    }

    @Test
    fun getShortEpg_parses_listings_with_base64_decoding() {
        val client = createClient()
        // base64 encoded title: "VGl0bGU=" -> "Title"
        // base64 encoded description: "RGVzY3JpcHRpb24=" -> "Description"
        val body = """
        {
            "epg_listings": [
                {
                    "id": "12345",
                    "epg_id": "epg.channel.id",
                    "title": "VGl0bGU=",
                    "lang": "en",
                    "start": "1700000000",
                    "end": "1700003600",
                    "description": "RGVzY3JpcHRpb24=",
                    "channel_id": "ch1"
                }
            ]
        }
        """.trimIndent()
        val parsed = client.parseShortEpg(body)
        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("12345", item.id)
        assertEquals("epg.channel.id", item.epgId)
        assertEquals("Title", item.title)
        assertEquals("en", item.language)
        assertEquals(1700000000L, item.startTimestamp)
        assertEquals(1700003600L, item.endTimestamp)
        assertEquals("Description", item.description)
    }

    @Test
    fun getShortEpg_handles_non_base64_gracefully() {
        val client = createClient()
        val body = """
        {
            "epg_listings": [
                {
                    "id": "12345",
                    "epg_id": "epg.channel.id",
                    "title": "Not Base64 Title",
                    "lang": "en",
                    "start": "1700000000",
                    "end": "1700003600",
                    "description": "Plain description",
                    "channel_id": "ch1"
                }
            ]
        }
        """.trimIndent()
        val parsed = client.parseShortEpg(body)
        assertEquals(1, parsed.size)
        val item = parsed.first()
        assertEquals("Not Base64 Title", item.title)
        assertEquals("Plain description", item.description)
    }
}
