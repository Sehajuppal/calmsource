package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionError
import java.net.URI
import java.net.URISyntaxException

/**
 * Validates extension URLs before attempting to load their manifests.
 *
 * Security boundaries enforced:
 * - Scheme must be HTTP or HTTPS.
 * - Explicitly rejects file://, javascript:, content:, data:, ftp:, etc.
 * - Rejects private IP ranges and localhost (unless specifically permitted via debug flags).
 * - Identifies if a URL is insecure (HTTP) to trigger warnings.
 * - Normalizes URLs by trimming and ensuring trailing slash paths if needed.
 */
object ExtensionInstallValidator {

    /**
     * Normalizes an extension manifest URL by trimming whitespace.
     */
    fun normalizeUrl(url: String): String {
        var trimmed = url.trim()
        if (trimmed.startsWith("stremio://", ignoreCase = true)) {
            trimmed = "https://" + trimmed.substring("stremio://".length)
        }
        val withoutFragment = trimmed.substringBefore("#")
        val pathPart = withoutFragment.substringBefore("?")
        if (
            (pathPart.startsWith("http://", ignoreCase = true) ||
                pathPart.startsWith("https://", ignoreCase = true)) &&
            !pathPart.endsWith(".json", ignoreCase = true)
        ) {
            val query = trimmed.substringAfter("?", missingDelimiterValue = "")
            trimmed = pathPart.trimEnd('/') + "/manifest.json" +
                if (query.isNotEmpty() && query != trimmed) "?$query" else ""
        }
        return trimmed
    }

    /**
     * Validates an extension URL for structural and security requirements.
     *
     * @param url The normalized URL string
     * @param isDebug Whether debug mode is enabled, allowing local/private URLs
     * @param allowCleartext Whether cleartext HTTP sources are permitted by user preference
     * @return ValidationResult containing the parsed URI, whether it's secure, and any warnings/errors.
     */
    fun validate(url: String, isDebug: Boolean = false, allowCleartext: Boolean = false): ValidationResult {
        if (url.isBlank()) {
            return ValidationResult.Invalid(ExtensionError.InvalidManifest("URL cannot be blank"))
        }

        val uri: URI
        try {
            // Stremio addon URLs commonly use pipe characters in path segments
            // (e.g. language=hindi|debridoptions=...). java.net.URI rejects these,
            // so encode them before parsing. The raw URL is used for HTTP requests.
            uri = URI(url.replace("|", "%7C"))
        } catch (e: URISyntaxException) {
            return ValidationResult.Invalid(ExtensionError.InvalidManifest("Invalid URL format"))
        }

        val scheme = uri.scheme?.lowercase()
        if (scheme != "http" && scheme != "https") {
            return ValidationResult.Invalid(
                ExtensionError.InvalidManifest("Unsupported scheme: $scheme. Only HTTP and HTTPS are allowed.")
            )
        }

        val userInfo = uri.userInfo
        if (userInfo != null && userInfo.isNotBlank()) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidManifest("Addon URLs must not contain embedded credentials (user:password@host).")
            )
        }

        val host = uri.host ?: ""
        if (!isDebug && isLocalOrPrivateIp(host)) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidManifest("Localhost and private network URLs are not allowed.")
            )
        }

        val isSecure = scheme == "https"
        if (!isSecure && !allowCleartext) {
            return ValidationResult.Invalid(
                ExtensionError.InvalidManifest("Cleartext HTTP traffic is blocked by security policy. You can enable HTTP sources in Settings.")
            )
        }

        val warnings = if (!isSecure) {
            listOf("Non-HTTPS extension sources are insecure. Proceed with caution.")
        } else {
            emptyList()
        }

        return ValidationResult.Valid(uri = uri, requestUrl = url, isSecure = isSecure, warnings = warnings)
    }

    /**
     * Checks if the host resolves to a localhost, private network, or link-local address.
     * Matches typical loopback (127.0.0.0/8), local (localhost), emulator (10.0.2.2),
     * and RFC 1918 private IPv4 spaces.
     */
    private fun isLocalOrPrivateIp(host: String): Boolean {
        val lowerHost = host.trim('[', ']').lowercase()
        if (lowerHost == "localhost" || lowerHost == "127.0.0.1" || lowerHost == "::1" || lowerHost == "10.0.2.2") {
            return true
        }

        // Basic IPv4 private range checks
        val ipParts = lowerHost.split(".")
        if (ipParts.size == 4) {
            val p1 = ipParts[0].toIntOrNull()
            val p2 = ipParts[1].toIntOrNull()
            if (p1 != null && p2 != null) {
                if (p1 == 10) return true // 10.x.x.x
                if (p1 == 172 && p2 in 16..31) return true // 172.16.x.x - 172.31.x.x
                if (p1 == 192 && p2 == 168) return true // 192.168.x.x
                if (p1 == 169 && p2 == 254) return true // 169.254.x.x
            }
        }
        return false
    }

    sealed interface ValidationResult {
        data class Valid(
            val uri: URI,
            /** Original normalized URL with literal pipe delimiters for HTTP requests. */
            val requestUrl: String,
            val isSecure: Boolean,
            val warnings: List<String>
        ) : ValidationResult

        data class Invalid(
            val error: ExtensionError
        ) : ValidationResult
    }
}
