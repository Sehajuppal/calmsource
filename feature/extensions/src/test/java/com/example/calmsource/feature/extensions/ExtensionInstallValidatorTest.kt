package com.example.calmsource.feature.extensions

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtensionInstallValidatorTest {

    @Test
    fun `HTTPS URL is parsed and marked secure`() {
        val result = ExtensionInstallValidator.validate("https://my-addon.com/manifest.json")
        assertTrue("Expected Valid result", result is ExtensionInstallValidator.ValidationResult.Valid)
        
        val valid = result as ExtensionInstallValidator.ValidationResult.Valid
        assertTrue("HTTPS should be secure", valid.isSecure)
        assertTrue("No warnings should be present for HTTPS", valid.warnings.isEmpty())
        assertEquals("https", valid.uri.scheme)
    }

    @Test
    fun `HTTP URL is parsed but triggers a warning`() {
        val result = ExtensionInstallValidator.validate("http://insecure-addon.com/manifest.json", allowCleartext = true)
        assertTrue("Expected Valid result", result is ExtensionInstallValidator.ValidationResult.Valid)
        
        val valid = result as ExtensionInstallValidator.ValidationResult.Valid
        assertTrue("HTTP should NOT be secure", !valid.isSecure)
        assertTrue("Warnings should be present for HTTP", valid.warnings.isNotEmpty())
        assertEquals("http", valid.uri.scheme)
    }

    @Test
    fun `HTTP URL is blocked when allowCleartext is false`() {
        val result = ExtensionInstallValidator.validate("http://insecure-addon.com/manifest.json", allowCleartext = false)
        assertTrue("Expected Invalid result", result is ExtensionInstallValidator.ValidationResult.Invalid)
        val invalid = result as ExtensionInstallValidator.ValidationResult.Invalid
        assertTrue(invalid.error.message.contains("Cleartext HTTP traffic is blocked by security policy"))
    }

    @Test
    fun `Unsafe schemes are rejected`() {
        val unsafeUrls = listOf(
            "file:///etc/passwd",
            "javascript:alert(1)",
            "data:text/plain;base64,SGVsbG8sIFdvcmxkIQ==",
            "content://com.android.providers.media.documents",
            "ftp://files.example.com/manifest.json"
        )
        
        for (url in unsafeUrls) {
            val result = ExtensionInstallValidator.validate(url)
            assertTrue("Expected Invalid result for $url", result is ExtensionInstallValidator.ValidationResult.Invalid)
            
            val invalid = result as ExtensionInstallValidator.ValidationResult.Invalid
            assertTrue(invalid.error.message.contains("Unsupported scheme"))
        }
    }

    @Test
    fun `Malformed URLs are rejected`() {
        val malformed = listOf(
            "", // Blank
            "   ", // Whitespace
            "not-a-url", // Missing scheme
            "http://[::1" // Invalid URI syntax
        )

        for (url in malformed) {
            val result = ExtensionInstallValidator.validate(url)
            assertTrue("Expected Invalid result for '$url'", result is ExtensionInstallValidator.ValidationResult.Invalid)
        }
    }

    @Test
    fun `Private network URLs are blocked by default`() {
        val privateUrls = listOf(
            "http://localhost:8080/manifest.json",
            "http://127.0.0.1/manifest.json",
            "http://10.0.2.2/manifest.json",
            "http://192.168.1.5/manifest.json",
            "http://172.16.0.1/manifest.json",
            "http://169.254.1.1/manifest.json"
        )
        for (url in privateUrls) {
            val result = ExtensionInstallValidator.validate(url)
            assertTrue("Expected Invalid result for $url", result is ExtensionInstallValidator.ValidationResult.Invalid)
            
            val invalid = result as ExtensionInstallValidator.ValidationResult.Invalid
            assertTrue(invalid.error.message.contains("Localhost and private network URLs are not allowed"))
        }
    }

    @Test
    fun `Private network URLs are allowed when isDebug is true`() {
        val result = ExtensionInstallValidator.validate("http://192.168.1.5/manifest.json", isDebug = true, allowCleartext = true)
        assertTrue("Expected Valid result", result is ExtensionInstallValidator.ValidationResult.Valid)
    }

    @Test
    fun `pipe delimiters are preserved in requestUrl for HTTP fetch`() {
        val url = "https://torrentio.strem.fun/realdebrid=KEY|qualityfilter=hdr/manifest.json"
        val result = ExtensionInstallValidator.validate(url)
        assertTrue(result is ExtensionInstallValidator.ValidationResult.Valid)
        val valid = result as ExtensionInstallValidator.ValidationResult.Valid
        assertEquals(url, valid.requestUrl)
        assertTrue(valid.uri.toString().contains("%7C"))
    }
}
