package com.example.calmsource.core.network

import com.example.calmsource.core.model.maskToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlRedactorSecurityTest {

    // ==========================================
    // 1. Semicolon Separators
    // ==========================================

    @Test
    fun testSemicolonQuerySeparatorRedaction() {
        val url = "https://example.com/api?other=val;token=secret"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?other=val;token=REDACTED", redacted)
    }

    @Test
    fun testSemicolonInPathSegmentKeys() {
        val path = "/token=abc123;password=secret456;safe=value/manifest.json"
        val redacted = UrlRedactor.redactPathSecrets(path)
        assertTrue(redacted.contains("token=REDACTED"))
        assertTrue(redacted.contains("password=REDACTED"))
        assertTrue(redacted.contains("safe=value"))
    }

    // ==========================================
    // 2. URL-Encoding Variations
    // ==========================================

    @Test
    fun testMixedCaseUrlEncodedKey() {
        val url = "https://example.com/api?t%4Fk%45n=secret" // "tOkEn"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?t%4Fk%45n=REDACTED", redacted)
    }

    @Test
    fun testDoubleUrlEncodedKey() {
        // Double encoding: %2574decodes once to %74, which decodes to 't'
        val url = "https://example.com/api?%2574oken=secret"
        val redacted = UrlRedactor.redactUrl(url)
        println("Double encoded query parameter result: $redacted")
    }

    @Test
    fun testUrlEncodedPathSeparatorBypass() {
        // High risk: using %2F instead of / in Xtream path to bypass redaction
        val url = "http://server.com/live%2Fuser1%2Fpass1%2F123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        // Verifying if the credentials are leaked (they shouldn't be)
        assertFalse("Credentials leaked due to URL-encoded path separator", redacted.contains("user1"))
        assertFalse("Credentials leaked due to URL-encoded path separator", redacted.contains("pass1"))
        assertTrue("URL-encoded Xtream path should be redacted", redacted.contains("REDACTED"))
    }

    // ==========================================
    // 3. Space and Whitespace in Queries
    // ==========================================

    @Test
    fun testSpacesAndEncodedSpacesQuery() {
        val url1 = "https://example.com/api?token =secret"
        val redacted1 = UrlRedactor.redactUrl(url1)
        assertEquals("https://example.com/api?token =REDACTED", redacted1)

        val url2 = "https://example.com/api?token%20=secret"
        val redacted2 = UrlRedactor.redactUrl(url2)
        assertEquals("https://example.com/api?token%20=REDACTED", redacted2)
    }

    @Test
    fun testSpaceInMiddleOfKey() {
        val url = "https://example.com/api?to%20ken=secret"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("https://example.com/api?to%20ken=REDACTED", redacted)
    }

    @Test
    fun testWhitespaceControlCharactersInKey() {
        // Control characters: %0A (LF), %0D (CR), %09 (TAB)
        val urlLF = "https://example.com/api?token%0A=secret"
        val urlCR = "https://example.com/api?token%0D=secret"
        val urlTab = "https://example.com/api?token%09=secret"

        assertFalse("Token leaked with LF", UrlRedactor.redactUrl(urlLF).contains("secret"))
        assertFalse("Token leaked with CR", UrlRedactor.redactUrl(urlCR).contains("secret"))
        assertFalse("Token leaked with TAB", UrlRedactor.redactUrl(urlTab).contains("secret"))
    }

    @Test
    fun testPlusAsSpaceInKey() {
        val url = "https://example.com/api?token+name=secret"
        val redacted = UrlRedactor.redactUrl(url)
        println("Plus as space result: $redacted")
    }

    // ==========================================
    // 4. Opaque URLs
    // ==========================================

    @Test
    fun testOpaqueUriRedaction() {
        // Opaque magnet link with token
        val magnetUrl = "magnet:?xt=urn:btih:xyz&token=secret"
        val redactedMagnet = UrlRedactor.redactUrl(magnetUrl)
        assertEquals("magnet:?xt=urn:btih:xyz&token=REDACTED", redactedMagnet)

        // Opaque xtream link
        val xtreamOpaque = "xtream:live/username/password/123.ts"
        val redactedXtream = UrlRedactor.redactUrl(xtreamOpaque)
        assertEquals("xtream:live/REDACTED/REDACTED/123.ts", redactedXtream)
    }

    @Test
    fun testOpaqueUrlMalformed() {
        val url = "acestream://invalid_host:abc?token=secret"
        val redacted = UrlRedactor.redactUrl(url)
        assertTrue(redacted.contains("token=REDACTED"))
    }

    // ==========================================
    // 5. Xtream Path Variations
    // ==========================================

    @Test
    fun testXtreamHostRedaction() {
        val url = "xtream://live/username/password/123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("xtream://live/REDACTED/REDACTED/123.ts", redacted)
    }

    @Test
    fun testXtreamPathConsecutiveSlashesBypass() {
        // Path variation: live//username/password/123.ts
        val url = "http://server.com/live//username/password/123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username leaked in consecutive slash URL", redacted.contains("username"))
        assertFalse("Password leaked in consecutive slash URL", redacted.contains("password"))
        assertTrue("Consecutive slash URL should be redacted", redacted.contains("REDACTED"))
    }

    @Test
    fun testXtreamPathMultipleSlashesAfterLive() {
        val url = "http://server.com/live/username//password/123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Password leaked in username//password URL", redacted.contains("password"))
    }

    @Test
    fun testXtreamPathPrefixVariation() {
        val url = "http://server.com/api/v1/live/username/password/123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertEquals("http://server.com/api/v1/live/REDACTED/REDACTED/123.ts", redacted)
    }

    @Test
    fun testXtreamPathMultipleCommandKeywords() {
        val url = "http://server.com/live/username/password/live/other/pass/123.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username leaked with multiple commands", redacted.contains("username"))
        assertFalse("Password leaked with multiple commands", redacted.contains("password"))
    }

    @Test
    fun `normal live CDN path is not mistaken for Xtream credentials`() {
        val url = "https://cdn.example.com/live/news/720p/index.m3u8"
        assertEquals(url, UrlRedactor.redactUrl(url))
    }

    @Test
    fun `sensitive fragment parameters are redacted`() {
        val redacted = UrlRedactor.redactUrl(
            "https://example.com/callback#access_token=top-secret&state=safe"
        )
        assertFalse(redacted.contains("top-secret"))
        assertTrue(redacted.endsWith("#access_token=REDACTED&state=safe"))
    }

    // ==========================================
    // 6. JSON / Error Parsing Exceptions
    // ==========================================

    @Test
    fun testScrubbingLeakedJsonCredentials() {
        val requestUrl = "https://server.com/api?username=myuser&password=mypassword123"
        val message = "Error: Authentication failed for user myuser with password mypassword123"
        val redacted = UrlRedactor.redactErrorMessage(message, requestUrl)
        assertEquals("Error: Authentication failed for user REDACTED with password REDACTED", redacted)
    }

    @Test
    fun testScrubbingWithMalformedRequestUrl() {
        val requestUrl = "not_a_valid_url"
        val message = "Error occurred on request not_a_valid_url"
        val redacted = UrlRedactor.redactErrorMessage(message, requestUrl)
        println("Malformed request URL scrubbing result: $redacted")
    }

    // ==========================================
    // 7. TokenMasking Safety Checks
    // ==========================================

    @Test
    fun testTokenMaskingLengths() {
        // Less than 16 characters must show as "••••••••"
        assertEquals("••••••••", "123456789012345".maskToken())
        assertEquals("••••••••", "short".maskToken())
        assertEquals("••••••••", "".maskToken())

        // 16 or more characters must mask middle
        assertEquals("1234...3456", "1234567890123456".maskToken())
        assertEquals("abcd...wxyz", "abcdefghijklmnopqrstuvwxyz".maskToken())
    }

    @Test
    fun testTokenMaskingWhitespaceOnly() {
        // 16 spaces
        val spaces16 = " ".repeat(16)
        assertEquals("    ...    ", spaces16.maskToken())
    }
}
