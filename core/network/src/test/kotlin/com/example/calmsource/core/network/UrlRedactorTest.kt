package com.example.calmsource.core.network

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRedactorTest {

    // ── Original tests ───────────────────────────────────────────────

    @Test
    fun testUrlRedaction() {
        val url = "https://example.com/api?token=12345&other=abc"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?token=REDACTED&other=abc", redacted)
    }

    @Test
    fun testApiKeyRedaction() {
        val url = "https://example.com/api?apikey=secret_key&other=abc"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?apikey=REDACTED&other=abc", redacted)
    }

    @Test
    fun testNoQueryParameters() {
        val url = "https://example.com/api"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api", redacted)
    }

    @Test
    fun testMultipleRedactions() {
        val url = "https://example.com/api?token=123&apikey=456&safe=789"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?token=REDACTED&apikey=REDACTED&safe=789", redacted)
    }

    @Test
    fun testInvalidUrl() {
        val url = "not_a_valid_url // ?? token=123"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("REDACTED_INVALID_URL", redacted)
    }

    // ── Token redaction ──────────────────────────────────────────────

    @Test
    fun testRedactToken_longToken() {
        val token = "RD_ACCESS_TOKEN_abc12345"
        val redacted = UrlRedactor.redactToken(token)
        assertEquals("RD_A...2345", redacted)
    }

    @Test
    fun testRedactToken_shortToken() {
        val token = "abc"
        val redacted = UrlRedactor.redactToken(token)
        assertEquals("••••••••", redacted)
    }

    @Test
    fun testRedactToken_exactlyEightChars() {
        val token = "12345678"
        val redacted = UrlRedactor.redactToken(token)
        assertEquals("••••••••", redacted)
    }

    @Test
    fun testRedactToken_nullToken() {
        val redacted = UrlRedactor.redactToken(null)
        assertEquals("••••••••", redacted)
    }

    @Test
    fun testRedactToken_blankToken() {
        val redacted = UrlRedactor.redactToken("   ")
        assertEquals("••••••••", redacted)
    }

    // ── Private link redaction ───────────────────────────────────────

    @Test
    fun testRedactPrivateLink_normalUrl() {
        val url = "https://download.real-debrid.com/d/ABCDEF123/movie.2024.1080p.mkv?token=secret123"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertEquals("https://download.real-debrid.com/...REDACTED", redacted)
    }

    @Test
    fun testRedactPrivateLink_withPort() {
        val url = "https://cdn.premiumize.me:8080/files/download/abcd1234.mp4"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertEquals("https://cdn.premiumize.me:8080/...REDACTED", redacted)
    }

    @Test
    fun testRedactPrivateLink_invalidUrl() {
        val url = "not a valid url at all"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertEquals("REDACTED_PRIVATE_LINK", redacted)
    }

    // ── Error message redaction ──────────────────────────────────────

    @Test
    fun testRedactErrorMessage_withSensitiveUrl() {
        val message = "Failed to connect to https://api.real-debrid.com/oauth/token?token=mysecret123&other=value — connection timed out"
        val redacted = UrlRedactor.redactErrorMessage(message)
        assertEquals(
            "Failed to connect to https://api.real-debrid.com/oauth/token?token=REDACTED&other=value — connection timed out",
            redacted
        )
    }

    @Test
    fun testRedactErrorMessage_noUrls() {
        val message = "Something went wrong with the request"
        val redacted = UrlRedactor.redactErrorMessage(message)
        assertEquals("Something went wrong with the request", redacted)
    }

    @Test
    fun testRedactErrorMessage_multipleUrls() {
        val message = "Error at https://a.com/x?token=aaa and also https://b.com/y?password=bbb end"
        val redacted = UrlRedactor.redactErrorMessage(message)
        assertEquals(
            "Error at https://a.com/x?token=REDACTED and also https://b.com/y?password=REDACTED end",
            redacted
        )
    }

    // ── New query param redaction (access_token, refresh_token, device_code) ──

    @Test
    fun testAccessTokenParamRedaction() {
        val url = "https://api.example.com/auth?access_token=eyJhbGciOiJIUzI1NiJ9&scope=read"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://api.example.com/auth?access_token=REDACTED&scope=read", redacted)
    }

    @Test
    fun testRefreshTokenParamRedaction() {
        val url = "https://api.example.com/refresh?refresh_token=rt_longvalue123&client_id=app1"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://api.example.com/refresh?refresh_token=REDACTED&client_id=app1", redacted)
    }

    @Test
    fun testDeviceCodeParamRedaction() {
        val url = "https://api.real-debrid.com/oauth/device?device_code=abc123xyz&client_id=X245"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://api.real-debrid.com/oauth/device?device_code=REDACTED&client_id=X245", redacted)
    }

    @Test
    fun testPinParamRedaction() {
        val url = "https://alldebrid.com/pin/check?pin=1234&agent=calmsource"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://alldebrid.com/pin/check?pin=REDACTED&agent=calmsource", redacted)
    }

    // ── Combined / integration redactions ────────────────────────────

    @Test
    fun testCombinedRedaction_errorMessageWithMultipleSensitiveParams() {
        val message = "Request failed: https://api.rd.com/v2/resolve?access_token=longtoken123&refresh_token=reftok456&safe=ok returned 401"
        val redacted = UrlRedactor.redactErrorMessage(message)
        assertEquals(
            "Request failed: https://api.rd.com/v2/resolve?access_token=REDACTED&refresh_token=REDACTED&safe=ok returned 401",
            redacted
        )
    }

    @Test
    fun testCombinedRedaction_tokenAndPrivateLink() {
        // Demonstrate that redactToken and redactPrivateLink work independently
        val token = "RD_ACCESS_TOKEN_abcdefgh"
        val link = "https://download.real-debrid.com/d/XYZ/file.mkv?token=secret"

        val maskedToken = UrlRedactor.redactToken(token)
        val maskedLink = UrlRedactor.redactPrivateLink(link)

        assertEquals("RD_A...efgh", maskedToken)
        assertEquals("https://download.real-debrid.com/...REDACTED", maskedLink)
    }

    // ── Audit & Verification Tests ───────────────────────────────────

    @Test
    fun testQueryParameterRedaction_caseInsensitiveAndDuplicates() {
        val url1 = "https://example.com/api?TOKEN=12345&ApIKeY=secret123&other=abc"
        val redacted1 = UrlRedactor.redactUrl(url1)
        assertEquals("https://example.com/api?TOKEN=REDACTED&ApIKeY=REDACTED&other=abc", redacted1)

        val url2 = "https://example.com/api?token=123&token=456&key=789"
        val redacted2 = UrlRedactor.redactUrl(url2)
        assertEquals("https://example.com/api?token=REDACTED&token=REDACTED&key=REDACTED", redacted2)
    }

    @Test
    fun testBearerTokenRedaction_nineOrMoreChars() {
        // Shorter than 16 characters are fully masked
        val token8 = "12345678"
        assertEquals("••••••••", UrlRedactor.redactToken(token8))

        val token9 = "123456789"
        assertEquals("••••••••", UrlRedactor.redactToken(token9))

        val token15 = "RD_ACCESS_TOKEN"
        assertEquals("••••••••", UrlRedactor.redactToken(token15))

        // Tokens >= 16 characters are partially masked showing first/last 4 characters
        val token16 = "RD_ACCESS_TOKEN_"
        assertEquals("RD_A...KEN_", UrlRedactor.redactToken(token16))

        val token24 = "RD_ACCESS_TOKEN_abcdefgh"
        assertEquals("RD_A...efgh", UrlRedactor.redactToken(token24))
    }

    @Test
    fun testBearerTokenRedaction_underEightChars() {
        // Less than 8 characters are fully masked
        val token7 = "1234567"
        assertEquals("••••••••", UrlRedactor.redactToken(token7))

        val token1 = "x"
        assertEquals("••••••••", UrlRedactor.redactToken(token1))
    }

    @Test
    fun testBearerTokenRedaction_nullOrBlank() {
        assertEquals("••••••••", UrlRedactor.redactToken(null))
        assertEquals("••••••••", UrlRedactor.redactToken(""))
        assertEquals("••••••••", UrlRedactor.redactToken("   "))
    }

    @Test
    fun testApiKeyRedaction_audit() {
        // API keys in query parameters
        val url = "https://example.com/search?api_key=my_api_key_12345&query=test"
        assertEquals("https://example.com/search?api_key=REDACTED&query=test", UrlRedactor.redactUrl(url))

        // API keys in DebridTokenSet
        val tokenSet = com.example.calmsource.core.model.DebridTokenSet(apiKey = "PM_API_KEY_SECRET_987654321")
        val debugString = tokenSet.toString()
        assertFalse(debugString.contains("PM_API_KEY_SECRET_987654321"))
        assertTrue(debugString.contains("PM_A...4321"))
    }

    @Test
    fun testPrivateLinkRedaction() {
        // Only keep scheme + host + "/...REDACTED"
        val url = "http://download.real-debrid.com/d/ABCDEF/movie.mkv?token=123"
        assertEquals("http://download.real-debrid.com/...REDACTED", UrlRedactor.redactPrivateLink(url))

        val urlWithPort = "https://private.cdn.me:8443/dl/file.mp4"
        assertEquals("https://private.cdn.me:8443/...REDACTED", UrlRedactor.redactPrivateLink(urlWithPort))
    }

    @Test
    fun testResolvedUrlRedaction() {
        val resolvedUrl = "https://real-debrid.com/d/unrestricted-stream-link.mkv"
        val redacted = UrlRedactor.redactPrivateLink(resolvedUrl)
        assertEquals("https://real-debrid.com/...REDACTED", redacted)
    }

    @Test
    fun testErrorMessageRedactionForDebridErrors() {
        // Verify message containing resolved url gets its query parameters redacted (doesn't leak private token/etc.)
        val errorMsg = "Could not download file from https://download.real-debrid.com/d/ABC?token=supersecretkey"
        val redacted = UrlRedactor.redactErrorMessage(errorMsg)
        assertEquals("Could not download file from https://download.real-debrid.com/d/ABC?token=REDACTED", redacted)
    }

    @Test
    fun testDebugStringRedaction() {
        // DebridTokenSet toString()
        val tokenSet = com.example.calmsource.core.model.DebridTokenSet(
            accessToken = "RD_ACCESS_TOKEN_123456789",
            refreshToken = "RD_REFRESH_TOKEN_987654321",
            apiKey = "PM_API_KEY_12345678"
        )
        val tsStr = tokenSet.toString()
        assertFalse(tsStr.contains("RD_ACCESS_TOKEN_123456789"))
        assertFalse(tsStr.contains("RD_REFRESH_TOKEN_987654321"))
        assertFalse(tsStr.contains("PM_API_KEY_12345678"))
        assertTrue(tsStr.contains("RD_A...6789"))
        assertTrue(tsStr.contains("RD_R...4321"))
        assertTrue(tsStr.contains("PM_A...5678"))

        // ExtensionProvider toString() redacts URL secrets
        val provider = com.example.calmsource.core.model.ExtensionProvider(
            id = "ext-id",
            name = "Test Extension",
            url = "https://my-ext.com/manifest.json?token=mysecrettoken&apikey=xyz"
        )
        val provStr = provider.toString()
        assertFalse(provStr.contains("mysecrettoken"))
        assertFalse(provStr.contains("xyz"))
        assertTrue(provStr.contains("token=***"))
        assertTrue(provStr.contains("apikey=***"))
    }

    @Test
    fun testRawLinkHiddenByDefault() {
        val source = com.example.calmsource.core.model.StreamSource(
            id = "src-debrid-123",
            name = "Inception.2010.1080p.mkv",
            url = "https://download.real-debrid.com/d/ABCDEF/Inception.mkv?token=secret",
            extensionId = "deb-rd",
            resolution = "1080p",
            language = "English"
        )
        val label = com.example.calmsource.core.model.WatchOptionResolver.getReadableLabel(
            source,
            com.example.calmsource.core.model.SourceType.DEBRID
        )
        // Verify the user-facing label does not leak the raw URL
        assertFalse(label.contains("https://"))
        assertFalse(label.contains("unrestricted"))
        assertFalse(label.contains("secret"))
        assertFalse(label.contains("download.real-debrid.com"))
    }

    // ── Username query parameter redaction ───────────────────────────

    @Test
    fun testUsernameParamRedaction() {
        val url = "https://xtream-server.com/player_api.php?username=admin123&password=secret456&action=get_live_streams"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals(
            "https://xtream-server.com/player_api.php?username=REDACTED&password=REDACTED&action=get_live_streams",
            redacted
        )
    }

    @Test
    fun testUsernameOnlyRedaction() {
        val url = "https://example.com/api?username=myuser&safe=value"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?username=REDACTED&safe=value", redacted)
    }

    // ── UserInfo (basic auth) stripping ──────────────────────────────

    @Test
    fun testUserInfoStripped_basicAuth() {
        val url = "http://admin:s3cret@xtream.server.com:8080/get.php?password=xyz"
        val redacted = UrlRedactor.redactUrl(url)
        // userInfo should be stripped AND password param redacted
        assertFalse(redacted.contains("admin"))
        assertFalse(redacted.contains("s3cret"))
        assertTrue(redacted.contains("xtream.server.com:8080"))
        assertTrue(redacted.contains("password=REDACTED"))
    }

    @Test
    fun testUserInfoStripped_noQuery() {
        val url = "http://user:pass@example.com/path/to/file.m3u8"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse(redacted.contains("user:pass"))
        assertTrue(redacted.contains("http://REDACTED@example.com"))
    }

    // ── URL-encoded characters ───────────────────────────────────────

    @Test
    fun testUrlEncodedToken() {
        val url = "https://example.com/api?token=abc%3D%3Dxyz&other=val"
        val redacted = UrlRedactor.redactUrl(url)
        assertTrue(redacted.contains("token=REDACTED"))
        assertFalse(redacted.contains("abc"))
    }

    // ── Port number handling ─────────────────────────────────────────

    @Test
    fun testUrlWithVariousPorts() {
        val url1 = "http://server.com:25461/player_api.php?username=user1&password=pass1"
        val redacted1 = UrlRedactor.redactUrl(url1)
        assertTrue(redacted1.contains("server.com:25461"))
        assertTrue(redacted1.contains("username=REDACTED"))
        assertTrue(redacted1.contains("password=REDACTED"))

        val url2 = "https://cdn.example.com:443/stream?token=abc123"
        val redacted2 = UrlRedactor.redactUrl(url2)
        assertTrue(redacted2.contains("token=REDACTED"))
    }

    // ── Xtream-style full URL in error messages ─────────────────────

    @Test
    fun testErrorMessageWithXtreamUrl() {
        val msg = "Connection failed: http://xtream.tv:8080/player_api.php?username=admin&password=secret returned 403"
        val redacted = UrlRedactor.redactErrorMessage(msg)
        assertFalse(redacted.contains("admin"))
        assertFalse(redacted.contains("secret"))
        assertTrue(redacted.contains("username=REDACTED"))
        assertTrue(redacted.contains("password=REDACTED"))
    }

    // ── Private link with userInfo ───────────────────────────────────

    @Test
    fun testRedactPrivateLink_withUserInfo() {
        val url = "http://user:pass@private.cdn.com:9090/download/file.mkv"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertFalse(redacted.contains("user:pass"))
        assertTrue(redacted.contains("private.cdn.com:9090"))
        assertEquals("http://private.cdn.com:9090/...REDACTED", redacted)
    }

    // ── Path-based secret redaction ──────────────────────────────────

    @Test
    fun testRedactPathSecrets_stremioConfigPath() {
        val path = "/api_key=my_secret_value&lang=en/manifest.json"
        val redacted = UrlRedactor.redactPathSecrets(path)
        assertEquals("/api_key=REDACTED&lang=en/manifest.json", redacted)
    }

    @Test
    fun testRedactPathSecrets_multipleSecrets() {
        val path = "/token=abc123&password=secret456&safe=value/manifest.json"
        val redacted = UrlRedactor.redactPathSecrets(path)
        assertTrue(redacted.contains("token=REDACTED"))
        assertTrue(redacted.contains("password=REDACTED"))
        assertTrue(redacted.contains("safe=value"))
    }

    @Test
    fun testRedactPathSecrets_noSecrets() {
        val path = "/catalog/movie/top.json"
        val redacted = UrlRedactor.redactPathSecrets(path)
        assertEquals("/catalog/movie/top.json", redacted)
    }

    @Test
    fun testRedactUrl_pathAndQuerySecrets() {
        val url = "https://addon.com/token=path_secret/manifest.json?apikey=query_secret"
        val redacted = UrlRedactor.redactUrl(url)
        assertTrue("Path token should be redacted", redacted.contains("token=REDACTED"))
        assertTrue("Query apikey should be redacted", redacted.contains("apikey=REDACTED"))
        assertFalse("Path secret value should not appear", redacted.contains("path_secret"))
        assertFalse("Query secret value should not appear", redacted.contains("query_secret"))
    }

    @Test
    fun testRedactUrl_stremioAddonWithPathConfig() {
        val url = "https://stremio-addon.com/api_key=SUPER_SECRET_123&lang=en/manifest.json"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Secret should not appear", redacted.contains("SUPER_SECRET_123"))
        assertTrue("api_key should be redacted in path", redacted.contains("api_key=REDACTED"))
        assertTrue("lang should be preserved", redacted.contains("lang=en"))
    }

    @Test
    fun testRedactUrl_redactsPassAliasesAndXtreamPathCredentials() {
        val queryUrl = "https://example.com/player_api.php?user=a&pass=secret&pwd=secret2"
        val queryRedacted = UrlRedactor.redactUrl(queryUrl)
        assertFalse(queryRedacted.contains("secret"))
        assertFalse(queryRedacted.contains("secret2"))
        assertTrue(queryRedacted.contains("pass=REDACTED"))
        assertTrue(queryRedacted.contains("pwd=REDACTED"))

        val streamUrl = "http://xtream.example/live/user1/pass1/123.ts"
        val streamRedacted = UrlRedactor.redactUrl(streamUrl)
        assertFalse(streamRedacted.contains("user1"))
        assertFalse(streamRedacted.contains("pass1"))
        assertTrue(streamRedacted.contains("/live/REDACTED/REDACTED/123.ts"))
    }

    @Test
    fun testRedactFilename() {
        assertEquals("movie.[REDACTED]", UrlRedactor.redactFilename("movie.mkv"))
        assertEquals("super_secret_release.[REDACTED]", UrlRedactor.redactFilename("super_secret_release.mp4"))
        assertEquals("no_extension.[REDACTED]", UrlRedactor.redactFilename("no_extension"))
        assertEquals("Unknown File", UrlRedactor.redactFilename(null))
        assertEquals("Unknown File", UrlRedactor.redactFilename(""))
    }

    @Test
    fun testCustomSubpathXtreamUrl() {
        val url = "http://server.com/api/v1/live/user/pass/1.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("http://server.com/api/v1/live/REDACTED/REDACTED/1.ts", redacted)
    }

    @Test
    fun testConfigMapParsingWithDoubleSlashes() {
        val url = "https://host/config=1//manifest.json"
        val configMap = StremioAddonClient.parseConfigFromUrl(url)
        assertEquals("1", configMap["config"])
    }

    @Test
    fun testExceptionMessageRedactionScrubbingLeakedJsonCredentials() {
        val requestUrl = "https://server.com/api?username=myuser&password=mypassword123"
        val message = "Error: Authentication failed for user myuser with password mypassword123"
        val redacted = UrlRedactor.redactErrorMessage(message, requestUrl)
        assertEquals("Error: Authentication failed for user REDACTED with password REDACTED", redacted)
    }
}
