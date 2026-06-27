package com.example.calmsource.feature.iptv.xtream

/**
 * Normalizes user-entered Xtream portal URLs to `scheme://host[:port][/optional-base-path]`.
 *
 * Users often paste M3U portal links (`/get.php?...`), panel paths (`/c/`), or
 * full `player_api.php` URLs. API calls must target `{base}/player_api.php` only.
 *
 * Some providers install Xtream under a subdirectory (e.g. `/panel/`). Stripping
 * every path segment breaks those portals — only known suffixes are removed.
 */
object XtreamServerUrlNormalizer {
    private val HOST_PORT_PATTERN = Regex("""^[a-zA-Z0-9.\-\[\]:]+:\d+$""")

    /**
     * Prepares raw user input before validation/normalization: trims, adds `http://`
     * when the user pasted `host:port`, and strips embedded `user:pass@` from the URL.
     */
    fun preprocessPortalInput(serverUrl: String): String {
        var trimmed = serverUrl.trim()
        if (trimmed.isBlank()) return trimmed
        trimmed = stripUserInfo(trimmed)
        if (!trimmed.lowercase().startsWith("http://") && !trimmed.lowercase().startsWith("https://")) {
            if (HOST_PORT_PATTERN.matches(trimmed)) {
                trimmed = "http://$trimmed"
            }
        }
        return trimmed
    }

    internal fun stripUserInfo(serverUrl: String): String {
        return try {
            val uri = java.net.URI(serverUrl.trim())
            if (uri.userInfo.isNullOrBlank()) return serverUrl.trim()
            val port = uri.port
            val authority = if (port > 0) {
                "${uri.host}:$port"
            } else {
                uri.host
            }
            val rebuilt = java.net.URI(uri.scheme, authority, uri.path, uri.query, uri.fragment)
            rebuilt.toString()
        } catch (_: Exception) {
            serverUrl.trim()
        }
    }

    fun alternateSchemeUrl(normalizedUrl: String): String? {
        return when {
            normalizedUrl.startsWith("http://", ignoreCase = true) ->
                "https://${normalizedUrl.removePrefix("http://").removePrefix("HTTP://")}"
            normalizedUrl.startsWith("https://", ignoreCase = true) ->
                "http://${normalizedUrl.removePrefix("https://").removePrefix("HTTPS://")}"
            else -> null
        }
    }

    /**
     * Picks the portal URL persisted after auth. Many panels return an internal CDN/stream
     * hostname in `server_info.url` that does not resolve for API calls — always keep the
     * user-entered portal host unless it matches the panel-reported host.
     */
    fun resolveStoredPortalUrl(
        normalizedUserUrl: String,
        serverUrl: String,
        port: Int,
        httpsPort: Int,
        serverProtocol: String
    ): String {
        val userHost = extractHost(normalizedUserUrl) ?: return normalizedUserUrl
        val infoHost = serverUrl.trim()
        val hostForCatalog = when {
            infoHost.isBlank() -> userHost
            hostsEquivalent(userHost, infoHost) -> infoHost
            else -> userHost
        }
        return canonicalizeFromServerInfo(
            normalizedUserUrl = normalizedUserUrl,
            serverUrl = hostForCatalog,
            port = port,
            httpsPort = httpsPort,
            serverProtocol = serverProtocol
        )
    }

    internal fun extractHost(normalizedUrl: String): String? {
        return try {
            java.net.URI(normalizedUrl.trim()).host?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    internal fun hostsEquivalent(userHost: String, serverInfoHost: String): Boolean {
        val user = userHost.trim().lowercase()
        val info = serverInfoHost.trim().lowercase()
        if (user.isBlank() || info.isBlank()) return false
        return user == info || user.endsWith(".$info") || info.endsWith(".$user")
    }

    /**
     * Applies port/protocol from [server_info] onto the chosen portal host while preserving
     * any subdirectory path (e.g. `/panel`) from the normalized user URL.
     */
    fun canonicalizeFromServerInfo(
        normalizedUserUrl: String,
        serverUrl: String,
        port: Int,
        httpsPort: Int,
        serverProtocol: String
    ): String {
        val host = serverUrl.trim()
        if (host.isBlank()) return normalizedUserUrl
        val protocol = when (serverProtocol.trim().lowercase()) {
            "https" -> "https"
            else -> "http"
        }
        val resolvedPort = when {
            protocol == "https" && httpsPort > 0 -> httpsPort
            port > 0 -> port
            else -> -1
        }
        val isDefaultPort = (protocol == "http" && resolvedPort == 80) ||
            (protocol == "https" && resolvedPort == 443)
        val authority = if (resolvedPort > 0 && !isDefaultPort) {
            "$protocol://$host:$resolvedPort"
        } else {
            "$protocol://$host"
        }
        val userPath = extractSubdirectoryPath(normalizedUserUrl) ?: return authority
        return authority + userPath
    }

    internal fun extractSubdirectoryPath(normalizedUserUrl: String): String? {
        return try {
            val path = java.net.URI(normalizedUserUrl.trim()).path?.trim().orEmpty()
            when {
                path.isBlank() || path == "/" -> null
                else -> path.removeSuffix("/")
            }
        } catch (_: Exception) {
            null
        }
    }

    fun normalizePortalUrl(serverUrl: String): String? {
        if (serverUrl.isBlank()) return null
        return try {
            val uri = java.net.URI(serverUrl.trim())
            val scheme = uri.scheme ?: return null
            val host = uri.host ?: return null
            val port = uri.port
            val isDefaultPort = (scheme.equals("http", ignoreCase = true) && port == 80) ||
                (scheme.equals("https", ignoreCase = true) && port == 443)
            val authority = if (port > 0 && !isDefaultPort) {
                "$scheme://$host:$port"
            } else {
                "$scheme://$host"
            }
            var path = (uri.path ?: "").trim()
            if (path.isEmpty() || path == "/") {
                return authority
            }
            path = path.removeSuffix("/")
            path = path.removeSuffix("/player_api.php")
            path = path.removeSuffix("/portal.php")
            path = path.removeSuffix("/index.php")
            path = path.removeSuffix("/get.php")
            if (path.endsWith("/c")) {
                path = path.removeSuffix("/c").removeSuffix("/")
            }
            if (path.isBlank() || path == "/") {
                authority
            } else {
                authority + path
            }
        } catch (_: Exception) {
            null
        }
    }
}
