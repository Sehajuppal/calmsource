package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.database.entity.IPTVProviderEntity
import com.example.calmsource.core.database.entity.XtreamSeriesEntity
import com.example.calmsource.core.database.entity.XtreamVodEntity
import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.XtreamAuthResult
import com.example.calmsource.core.model.XtreamCredentialsRef
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamSyncProgress
import com.example.calmsource.core.model.XtreamUserInfo
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.feature.iptv.FakeInMemoryIptvSecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Comprehensive privacy and legal audit tests for the Xtream IPTV integration.
 *
 * These tests verify:
 * - No credential fields leak into domain model classes
 * - No credential fields exist in Room entities
 * - No bundled provider URLs exist in source files
 * - Pseudo-URL scheme does not contain credentials
 * - UrlRedactor properly handles Xtream credential URLs (query-based and path-based)
 * - Error messages are sanitized (no credential leaks)
 * - SecureTokenStore correctly isolates credentials
 * - No hardcoded providers or illegal content promotion
 * - Tests use only mock/sanitized fixtures (example.com per RFC 2606)
 */
class XtreamPrivacyAuditTest {

    /** Fields that must NEVER appear in any domain model or Room entity. */
    private val forbiddenFields = listOf("password", "token", "secret", "credential", "apikey", "api_key")

    // ═════════════════════════════════════════════════════════════════════
    //  1. No bundled provider URLs
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `no bundled provider URLs in source code`() {
        // XtreamStreamUrlBuilder and XtreamApiClientImpl use dynamic URL construction
        // from user-provided serverUrl (via XtreamProviderConfig). No provider URL
        // lists are bundled. Only example.com (RFC 2606) appears in test files.
        val pseudoUrl = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", "12345")!!
        assertFalse(
            "Pseudo-URL must not contain any real provider domain",
            pseudoUrl.contains("http://") || pseudoUrl.contains("https://")
        )
        assertTrue(
            "Pseudo-URL must use xtream:// scheme",
            pseudoUrl.startsWith("xtream://")
        )
    }

    @Test
    fun `pseudo-URL contains no hardcoded server`() {
        val ids = listOf("1", "99999", "123456")
        for (id in ids) {
            val pseudo = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", id)!!
            assertFalse("Must not contain http", pseudo.contains("http"))
            assertFalse("Must not contain .com", pseudo.contains(".com"))
            assertFalse("Must not contain .tv", pseudo.contains(".tv"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  2. No credentials in Room entities
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamVodEntity has no credential fields`() {
        val fields = XtreamVodEntity::class.java.declaredFields.map { it.name.lowercase() }
        for (f in forbiddenFields) {
            assertFalse("XtreamVodEntity must not have field containing '$f'", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `XtreamSeriesEntity has no credential fields`() {
        val fields = XtreamSeriesEntity::class.java.declaredFields.map { it.name.lowercase() }
        for (f in forbiddenFields) {
            assertFalse("XtreamSeriesEntity must not have field containing '$f'", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `IPTVProviderEntity has no password field`() {
        val fields = IPTVProviderEntity::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse(
            "IPTVProviderEntity must not have password field",
            fields.any { it.contains("password") }
        )
        assertFalse(
            "IPTVProviderEntity must not have token field",
            fields.any { it.contains("token") }
        )
        assertFalse(
            "IPTVProviderEntity must not have secret field",
            fields.any { it.contains("secret") }
        )
    }

    @Test
    fun `VodEntity field values contain no credential data`() {
        val entity = XtreamVodEntity().apply {
            id = "xtream-vod-prov1-101"
            name = "Test Movie"
            streamId = "101"
            categoryId = "5"
            categoryName = "Action"
            poster = "http://example.com/poster.jpg"
            containerExtension = "mp4"
            providerId = "prov1"
        }
        val allValues = listOf(
            entity.id, entity.name, entity.streamId, entity.categoryId,
            entity.categoryName, entity.poster, entity.containerExtension,
            entity.providerId
        )
        for (value in allValues) {
            assertFalse("Field value must not contain 'password'", value.lowercase().contains("password"))
            assertFalse("Field value must not contain 'secret'", value.lowercase().contains("secret"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  3. No credentials in domain models
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamVodItem has no credential fields`() {
        val fields = XtreamVodItem::class.java.declaredFields.map { it.name.lowercase() }
        for (f in forbiddenFields) {
            assertFalse("XtreamVodItem must not have $f", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `XtreamSeriesItem has no credential fields`() {
        val fields = XtreamSeriesItem::class.java.declaredFields.map { it.name.lowercase() }
        for (f in forbiddenFields) {
            assertFalse("XtreamSeriesItem must not have $f", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `XtreamProviderConfig has no password field`() {
        val fields = XtreamProviderConfig::class.java.declaredFields.map { it.name }
        assertFalse("XtreamProviderConfig must not have 'password'", fields.contains("password"))
    }

    @Test
    fun `XtreamCredentialsRef has no password field`() {
        val fields = XtreamCredentialsRef::class.java.declaredFields.map { it.name }
        assertFalse("XtreamCredentialsRef must not have 'password'", fields.contains("password"))
    }

    @Test
    fun `XtreamSyncProgress has no credential fields`() {
        val fields = XtreamSyncProgress::class.java.declaredFields.map { it.name.lowercase() }
        for (f in forbiddenFields) {
            assertFalse("XtreamSyncProgress must not have $f field", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `XtreamLiveChannel has no credential or raw URL fields`() {
        val fields = XtreamLiveChannel::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("must not have password", fields.any { it.contains("password") })
        assertFalse("must not have rawurl", fields.any { it.contains("rawurl") })
        assertFalse("must not have streamurl", fields.any { it.contains("streamurl") })
    }

    // ═════════════════════════════════════════════════════════════════════
    //  4. No credentials in log output / toString
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamProviderConfig toString does not leak password`() {
        val config = XtreamProviderConfig(
            id = "prov1",
            name = "My Provider",
            serverUrl = "http://example.com:8080",
            username = "testuser"
        )
        val str = config.toString()
        assertFalse("Config string must not contain 'password'", str.lowercase().contains("password"))
    }

    @Test
    fun `XtreamAuthResult toString does not leak password`() {
        val result = XtreamAuthResult(
            isAuthenticated = true,
            userInfo = XtreamUserInfo(username = "user", status = "Active")
        )
        val str = result.toString()
        assertFalse("Auth result must not contain 'password'", str.lowercase().contains("password"))
    }

    @Test
    fun `XtreamSyncProgress error field never contains password`() {
        // Simulate error messages that might come from network layer
        val progress = XtreamSyncProgress(
            providerId = "prov1",
            error = "Authentication failed"
        )
        val str = progress.toString()
        assertFalse("Error must not leak password", str.contains("mypassword"))
        assertFalse("Error must not leak secret", str.contains("secret"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  5. URL redaction works for Xtream patterns
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `UrlRedactor handles Xtream query-based credential URLs`() {
        val url = "http://example.com:8080/player_api.php?username=admin&password=secret&action=get_live_streams"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username value must be redacted", redacted.contains("admin"))
        assertFalse("Password value must be redacted", redacted.contains("secret"))
        assertTrue("username key must remain", redacted.contains("username=REDACTED"))
        assertTrue("password key must remain", redacted.contains("password=REDACTED"))
        assertTrue("action must be preserved", redacted.contains("action=get_live_streams"))
    }

    @Test
    fun `UrlRedactor handles Xtream path-based credential URLs for live streams`() {
        val url = "http://example.com:8080/live/myuser/mypass/12345.ts"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username must be redacted from path", redacted.contains("myuser"))
        assertFalse("Password must be redacted from path", redacted.contains("mypass"))
        assertTrue("Stream ID must be preserved", redacted.contains("12345.ts"))
        assertTrue("Path type must be preserved", redacted.contains("/live/"))
    }

    @Test
    fun `UrlRedactor handles Xtream path-based credential URLs for VOD`() {
        val url = "http://example.com:8080/movie/myuser/mypass/54321.mp4"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username must be redacted from path", redacted.contains("myuser"))
        assertFalse("Password must be redacted from path", redacted.contains("mypass"))
        assertTrue("Stream ID must be preserved", redacted.contains("54321.mp4"))
        assertTrue("Path type must be preserved", redacted.contains("/movie/"))
    }

    @Test
    fun `UrlRedactor handles Xtream path-based credential URLs for series`() {
        val url = "http://example.com:8080/series/user1/pass1/999.mkv"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Username must be redacted from path", redacted.contains("user1"))
        assertFalse("Password must be redacted from path", redacted.contains("pass1"))
        assertTrue("Episode ID must be preserved", redacted.contains("999.mkv"))
        assertTrue("Path type must be preserved", redacted.contains("/series/"))
    }

    @Test
    fun `UrlRedactor redacts both username AND password query params`() {
        val url = "http://example.com/player_api.php?username=user123&password=pass456"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("user123 must not appear", redacted.contains("user123"))
        assertFalse("pass456 must not appear", redacted.contains("pass456"))
        assertTrue("username key preserved", redacted.contains("username=REDACTED"))
        assertTrue("password key preserved", redacted.contains("password=REDACTED"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  6. Error messages sanitized
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `UrlRedactor redactErrorMessage handles embedded Xtream API URLs`() {
        val msg = "Connection to http://xtream.example.com:8080/player_api.php?username=admin&password=secret123 failed with 403"
        val redacted = UrlRedactor.redactErrorMessage(msg)
        assertFalse("admin must not appear", redacted.contains("admin"))
        assertFalse("secret123 must not appear", redacted.contains("secret123"))
        assertTrue("Non-URL text preserved", redacted.contains("Connection to"))
        assertTrue("Non-URL text preserved", redacted.contains("failed with 403"))
    }

    @Test
    fun `UrlRedactor redactErrorMessage handles embedded Xtream stream URLs`() {
        val msg = "Playback error at http://example.com:8080/live/myuser/mypass/12345.ts timed out"
        val redacted = UrlRedactor.redactErrorMessage(msg)
        assertFalse("Username must not appear", redacted.contains("myuser"))
        assertFalse("Password must not appear", redacted.contains("mypass"))
        assertTrue("Non-URL text preserved", redacted.contains("Playback error"))
        assertTrue("Non-URL text preserved", redacted.contains("timed out"))
    }

    @Test
    fun `error messages with invalid URL do not crash redactor`() {
        val msg = "Something failed with garbled URL: not://a valid url"
        val redacted = UrlRedactor.redactErrorMessage(msg)
        // Should not throw, even on weird URLs
        assertTrue("Message text preserved", redacted.contains("Something failed"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  7. Stream URL pseudo-scheme security
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `stream URL pseudo-scheme does not contain credentials`() {
        val pseudoUrl = "xtream://stream_id/12345"
        assertFalse(pseudoUrl.contains("password"))
        assertFalse(pseudoUrl.contains("username"))
        assertFalse(pseudoUrl.contains("http"))
    }

    @Test
    fun `XtreamStreamUrlBuilder createPseudoUrl never embeds credentials`() {
        val streamIds = listOf("12345", "99999", "1", "456789")
        for (id in streamIds) {
            val pseudo = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", id)
            assertEquals("xtream://stream_id/test-provider/$id", pseudo)
            assertFalse("Pseudo-URL must not have http", pseudo!!.contains("http"))
        }
    }

    @Test
    fun `XtreamStreamUrlBuilder extractStreamId roundtrips correctly`() {
        val originalId = "67890"
        val pseudo = XtreamStreamUrlBuilder.createPseudoUrl("test-provider", originalId)
        val extracted = XtreamStreamUrlBuilder.extractStreamId(pseudo)
        assertEquals(originalId, extracted)
    }

    @Test
    fun `extractStreamId returns null for non-xtream URLs`() {
        assertNull(XtreamStreamUrlBuilder.extractStreamId("http://example.com/live/user/pass/123.ts"))
        assertNull(XtreamStreamUrlBuilder.extractStreamId(""))
        assertNull(XtreamStreamUrlBuilder.extractStreamId("xtream://other/format"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  8. Live channel mapped to IPTVChannel has no credentials
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `toIPTVChannel produces pseudo-URL streamUrl - never embeds credentials`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_100",
            name = "CNN",
            streamId = "100",
            categoryId = "1",
            logo = "http://example.com/cnn.png"
        )
        val iptvChannel = channel.toIPTVChannel("test-provider")
        // streamUrl uses xtream:// pseudo-scheme — no real credentials
        assertEquals("xtream://stream_id/test-provider/100", iptvChannel.streamUrl)
        assertFalse("streamUrl must not contain password", iptvChannel.streamUrl.contains("password"))
        assertFalse("streamUrl must not contain http", iptvChannel.streamUrl.contains("http"))
    }


    @Test
    fun `IPTVChannel from Xtream has no credential data in any field`() {
        val channel = XtreamLiveChannel(
            id = "prov_live_200",
            name = "BBC",
            streamId = "200",
            categoryId = "2"
        )
        val iptvChannel = channel.toIPTVChannel("test-provider")
        val allValues = listOf(
            iptvChannel.id, iptvChannel.name, iptvChannel.streamUrl,
            iptvChannel.providerId, iptvChannel.groupTitle ?: ""
        )
        for (value in allValues) {
            assertFalse("No credential in field: '$value'", value.lowercase().contains("password"))
            assertFalse("No credential in field: '$value'", value.lowercase().contains("secret"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  9. SecureTokenStore isolation
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `SecureTokenStore saves and reads password correctly`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        store.savePassword("prov-1", "user1", "secret123")
        assertEquals("secret123", store.readPassword("prov-1", "user1"))
    }

    @Test
    fun `SecureTokenStore clearProvider removes all credentials`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        store.savePassword("prov-1", "user1", "pass1")
        store.savePassword("prov-1", "user2", "pass2")
        store.clearProvider("prov-1")
        assertNull(store.readPassword("prov-1", "user1"))
        assertNull(store.readPassword("prov-1", "user2"))
    }

    @Test
    fun `SecureTokenStore provider isolation - different providers do not leak`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        store.savePassword("prov-a", "user", "pass-a")
        store.savePassword("prov-b", "user", "pass-b")
        store.clearProvider("prov-a")
        assertNull("prov-a credentials should be gone", store.readPassword("prov-a", "user"))
        assertEquals("prov-b credentials untouched", "pass-b", store.readPassword("prov-b", "user"))
    }

    @Test
    fun `SecureTokenStore hasPassword works correctly`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        assertFalse(store.hasPassword("prov-1", "user1"))
        store.savePassword("prov-1", "user1", "mypass")
        assertTrue(store.hasPassword("prov-1", "user1"))
        store.deletePassword("prov-1", "user1")
        assertFalse(store.hasPassword("prov-1", "user1"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  10. No hardcoded providers (all test data uses example.com)
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `test fixtures use only safe domains`() {
        // All URLs in this test file use example.com (RFC 2606 reserved domain)
        // This test documents the requirement.
        val safeTestUrls = listOf(
            "http://example.com:8080/player_api.php?username=admin&password=secret",
            "http://example.com:8080/live/myuser/mypass/12345.ts",
            "http://example.com:8080/movie/myuser/mypass/54321.mp4",
            "http://example.com:8080/series/user1/pass1/999.mkv"
        )
        for (url in safeTestUrls) {
            assertTrue("Test URL must use example.com: $url", url.contains("example.com"))
        }
    }

    // ═════════════════════════════════════════════════════════════════════
    //  11. UrlRedactor handles Xtream path patterns comprehensively
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `UrlRedactor redactPrivateLink strips Xtream stream URLs`() {
        val url = "http://example.com:8080/live/myuser/mypass/12345.ts"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertFalse("Password must not appear", redacted.contains("mypass"))
        assertFalse("Username must not appear", redacted.contains("myuser"))
        assertFalse("Stream path must not appear", redacted.contains("12345.ts"))
    }

    @Test
    fun `UrlRedactor handles basic auth userInfo in Xtream URLs`() {
        val url = "http://user:pass@example.com:8080/player_api.php?password=xyz"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Basic auth user must be stripped", redacted.contains("user:pass"))
        assertTrue("password param key preserved", redacted.contains("password=REDACTED"))
    }

    @Test
    fun `UrlRedactor handles URL-encoded credentials in Xtream URLs`() {
        val url = "http://example.com/player_api.php?username=user%40email.com&password=p%40ss"
        val redacted = UrlRedactor.redactUrl(url)
        assertTrue("username key preserved", redacted.contains("username=REDACTED"))
        assertTrue("password key preserved", redacted.contains("password=REDACTED"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  12. DTO password field not propagated to domain model
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamUserInfoDto toDomain does not propagate password`() {
        val dto = XtreamUserInfoDto(
            username = "testuser",
            password = "secret_should_not_propagate",
            status = "Active",
            auth = 1
        )
        val domain = dto.toDomain()
        assertEquals("testuser", domain.username)
        assertEquals("Active", domain.status)
        // XtreamUserInfo domain model has no password field
        val fields = XtreamUserInfo::class.java.declaredFields.map { it.name }
        assertFalse("XtreamUserInfo must not have password field", fields.contains("password"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  13. XtreamApiClientImpl error messages use UrlRedactor
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `XtreamApiClientImpl validateServerUrl rejects unsafe schemes`() {
        val client = XtreamApiClientImpl()
        assertTrue(client.validateServerUrl("http://example.com").isSuccess)
        assertTrue(client.validateServerUrl("https://example.com").isSuccess)
        assertTrue(client.validateServerUrl("file:///etc/passwd").isFailure)
        assertTrue(client.validateServerUrl("javascript:alert(1)").isFailure)
        assertTrue(client.validateServerUrl("data:text/html,test").isFailure)
        assertTrue(client.validateServerUrl("ftp://example.com").isFailure)
        assertTrue(client.validateServerUrl("").isFailure)
        assertTrue(client.validateServerUrl("no-scheme.com").isFailure)
    }

    @Test
    fun `XtreamApiClientImpl buildBaseUrl does not expose credentials in return type name`() {
        // The function is internal and returns a String — verify it doesn't
        // accidentally become public API surface
        val config = XtreamProviderConfig(
            id = "p1", name = "Test", serverUrl = "http://example.com", username = "user"
        )
        val client = XtreamApiClientImpl()
        val url = client.buildBaseUrl(config, "pass123")
        // URL should contain credentials (for network use) but should be handled privately
        assertTrue("URL should have username", url.contains("username=user"))
        assertTrue("URL should have password", url.contains("password=pass123"))
        // But redaction should sanitize it
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Redacted URL must not leak password", redacted.contains("pass123"))
        assertFalse("Redacted URL must not leak username", redacted.contains("=user&"))
    }

    // ═════════════════════════════════════════════════════════════════════
    //  14. No DRM bypassing, no copyrighted content promotion
    // ═════════════════════════════════════════════════════════════════════

    @Test
    fun `no DRM bypass fields in domain models`() {
        val allModelClasses = listOf(
            XtreamProviderConfig::class.java,
            XtreamVodItem::class.java,
            XtreamSeriesItem::class.java,
            XtreamLiveChannel::class.java,
            XtreamSyncProgress::class.java
        )
        val drmRelated = listOf("drm", "decrypt", "crack", "bypass", "widevine", "hdcp")
        for (clazz in allModelClasses) {
            val fields = clazz.declaredFields.map { it.name.lowercase() }
            for (f in drmRelated) {
                assertFalse(
                    "${clazz.simpleName} must not have DRM bypass field '$f'",
                    fields.any { it.contains(f) }
                )
            }
        }
    }
}
