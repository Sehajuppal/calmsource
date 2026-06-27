package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.XtreamAuthResult
import com.example.calmsource.core.model.XtreamCredentialsRef
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamServerInfo
import com.example.calmsource.core.model.XtreamSyncProgress
import com.example.calmsource.core.model.XtreamUserInfo
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.feature.iptv.FakeInMemoryIptvSecureTokenStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Security audit tests for Xtream-related domain models and utilities.
 *
 * Verifies that:
 * - No domain model class leaks credentials via field names
 * - The FakeInMemoryIptvSecureTokenStore behaves correctly
 * - Auth results don't leak passwords in toString()
 * - UrlRedactor properly handles Xtream API and stream URLs
 */
class XtreamSecurityAuditTest {

    @Test
    fun `XtreamProviderConfig does not contain password field`() {
        // Use reflection to verify XtreamProviderConfig has no "password" field
        val fields = XtreamProviderConfig::class.java.declaredFields.map { it.name }
        assertFalse("XtreamProviderConfig must not have password field", fields.contains("password"))
    }

    @Test
    fun `XtreamCredentialsRef does not contain password field`() {
        val fields = XtreamCredentialsRef::class.java.declaredFields.map { it.name }
        assertFalse("XtreamCredentialsRef must not have password field", fields.contains("password"))
    }

    @Test
    fun `XtreamSyncProgress does not contain credentials`() {
        val fields = XtreamSyncProgress::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf("password", "token", "apikey", "secret", "credential")
        for (f in forbidden) {
            assertFalse("XtreamSyncProgress must not have $f field", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `XtreamLiveChannel does not contain password or raw URL`() {
        val fields = XtreamLiveChannel::class.java.declaredFields.map { it.name.lowercase() }
        assertFalse("must not have password", fields.any { it.contains("password") })
        assertFalse("must not have rawUrl", fields.any { it.contains("rawurl") })
    }

    @Test
    fun `XtreamVodItem does not contain credentials`() {
        val fields = XtreamVodItem::class.java.declaredFields.map { it.name.lowercase() }
        val forbidden = listOf("password", "token", "secret", "credential", "rawurl")
        for (f in forbidden) {
            assertFalse("XtreamVodItem must not have $f", fields.any { it.contains(f) })
        }
    }

    @Test
    fun `FakeInMemoryIptvSecureTokenStore saves and clears password correctly`() {
        val store = FakeInMemoryIptvSecureTokenStore()
        store.savePassword("prov-1", "user1", "secret123")
        assertEquals("secret123", store.readPassword("prov-1", "user1"))
        store.clearProvider("prov-1")
        assertNull(store.readPassword("prov-1", "user1"))
    }

    @Test
    fun `password not returned in any XtreamAuthResult field`() {
        val result = XtreamAuthResult(
            isAuthenticated = true,
            userInfo = XtreamUserInfo(username = "user", status = "Active"),
            serverInfo = XtreamServerInfo(url = "example.com", port = 8080)
        )
        val allStrings = result.toString()
        assertFalse("Auth result must not contain password", allStrings.contains("password"))
    }

    @Test
    fun `UrlRedactor redacts Xtream API URLs`() {
        val url = "http://example.com:8080/player_api.php?username=myuser&password=mypass&action=get_live_streams"
        val redacted = UrlRedactor.redactUrl(url)
        assertFalse("Redacted URL must not contain password value", redacted.contains("mypass"))
        assertFalse("Redacted URL must not contain username value", redacted.contains("myuser"))
    }

    @Test
    fun `UrlRedactor redacts Xtream stream URLs`() {
        val url = "http://example.com:8080/live/myuser/mypass/12345.ts"
        val redacted = UrlRedactor.redactPrivateLink(url)
        assertFalse("Redacted stream URL must not contain password", redacted.contains("mypass"))
    }
}
