package com.example.calmsource.feature.iptv.xtream

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class XtreamServerUrlNormalizerTest {
    @Test
    fun normalizePortalUrl_strips_c_panel_path() {
        assertEquals(
            "http://example.com:8080",
            XtreamServerUrlNormalizer.normalizePortalUrl("http://example.com:8080/c/")
        )
    }

    @Test
    fun normalizePortalUrl_strips_get_php_and_query() {
        assertEquals(
            "http://iptv.example.com:25461",
            XtreamServerUrlNormalizer.normalizePortalUrl("http://iptv.example.com:25461/get.php?username=u&password=p")
        )
    }

    @Test
    fun normalizePortalUrl_strips_player_api_path() {
        assertEquals(
            "http://example.com:8080",
            XtreamServerUrlNormalizer.normalizePortalUrl("http://example.com:8080/player_api.php")
        )
    }

    @Test
    fun normalizePortalUrl_rejects_invalid_url() {
        assertNull(XtreamServerUrlNormalizer.normalizePortalUrl("not-a-url"))
    }

    @Test
    fun normalizePortalUrl_preserves_subdirectory_install_path() {
        assertEquals(
            "http://example.com:8080/panel",
            XtreamServerUrlNormalizer.normalizePortalUrl("http://example.com:8080/panel/c/")
        )
        assertEquals(
            "https://iptv.example.com/stalker_portal",
            XtreamServerUrlNormalizer.normalizePortalUrl("https://iptv.example.com/stalker_portal/get.php?username=u")
        )
    }

    @Test
    fun canonicalizeFromServerInfo_uses_panel_host_when_hosts_match() {
        assertEquals(
            "http://cdn.example.com:8080/panel",
            XtreamServerUrlNormalizer.canonicalizeFromServerInfo(
                normalizedUserUrl = "http://cdn.example.com:25461/panel",
                serverUrl = "cdn.example.com",
                port = 8080,
                httpsPort = 8443,
                serverProtocol = "http"
            )
        )
    }

    @Test
    fun resolveStoredPortalUrl_keeps_user_host_when_server_info_cdn_differs() {
        assertEquals(
            "http://portal.example.com:8080/panel",
            XtreamServerUrlNormalizer.resolveStoredPortalUrl(
                normalizedUserUrl = "http://portal.example.com:25461/panel",
                serverUrl = "iptv.example.com",
                port = 8080,
                httpsPort = 8443,
                serverProtocol = "http"
            )
        )
    }

    @Test
    fun preprocessPortalInput_adds_http_for_host_port() {
        assertEquals(
            "http://example.com:8080",
            XtreamServerUrlNormalizer.preprocessPortalInput("example.com:8080")
        )
    }

    @Test
    fun preprocessPortalInput_strips_user_info() {
        assertEquals(
            "http://example.com:8080",
            XtreamServerUrlNormalizer.preprocessPortalInput("http://user:pass@example.com:8080")
        )
    }

    @Test
    fun alternateSchemeUrl_flips_http_and_https() {
        assertEquals(
            "https://example.com:8080/panel",
            XtreamServerUrlNormalizer.alternateSchemeUrl("http://example.com:8080/panel")
        )
    }

    @Test
    fun canonicalizeFromServerInfo_prefers_https_port() {
        assertEquals(
            "https://iptv.example.com:8443",
            XtreamServerUrlNormalizer.canonicalizeFromServerInfo(
                normalizedUserUrl = "http://iptv.example.com:8080",
                serverUrl = "iptv.example.com",
                port = 8080,
                httpsPort = 8443,
                serverProtocol = "https"
            )
        )
    }
}
