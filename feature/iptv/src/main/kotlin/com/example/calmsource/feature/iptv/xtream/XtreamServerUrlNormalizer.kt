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
    internal val HOST_PORT_PATTERN = Regex("""^[a-zA-Z0-9.\-\[\]:]+:\d+$""")

    /** Common Xtream panel ports when the user omits a port or uses only 80/443. */
    private val COMMON_XTREAM_PORTS = listOf(8080, 25461, 25463, 8880, 8000, 2095, 80, 443)

    private val java.net.URI.robustHost: String?
        get() {
            val h = this.host
            if (h != null) return h
            val auth = this.authority ?: return null
            val temp = if (auth.contains('@')) auth.substringAfterLast('@') else auth
            return if (temp.startsWith("[")) {
                temp.substringBefore(']') + "]"
            } else {
                temp.substringBefore(':')
            }.trim().takeIf { it.isNotEmpty() }
        }

    private val java.net.URI.robustPort: Int
        get() {
            val p = this.port
            if (p != -1) return p
            val auth = this.authority ?: return -1
            val temp = if (auth.contains('@')) auth.substringAfterLast('@') else auth
            val portString = if (temp.startsWith("[")) {
                temp.substringAfter(']', "")
            } else {
                if (temp.contains(':')) temp.substringAfter(':', "") else ""
            }
            val cleanPortString = if (portString.startsWith(":")) portString.substring(1) else portString
            return cleanPortString.toIntOrNull() ?: -1
        }

    /**
     * Prepares raw user input before validation/normalization: trims, adds `http://`
     * when the user pasted `host:port` or a bare hostname, and strips embedded `user:pass@`.
     */
    fun preprocessPortalInput(serverUrl: String): String {
        var trimmed = serverUrl.trim()
        if (trimmed.isBlank()) return trimmed
        trimmed = stripUserInfo(trimmed)
        if (!trimmed.lowercase().startsWith("http://") && !trimmed.lowercase().startsWith("https://")) {
            val colonIndex = trimmed.indexOf(':')
            val slashIndex = trimmed.indexOf('/')
            val hasNonHttpScheme = colonIndex >= 0 &&
                (slashIndex < 0 || colonIndex < slashIndex) &&
                !HOST_PORT_PATTERN.matches(trimmed)
            if (!hasNonHttpScheme && (HOST_PORT_PATTERN.matches(trimmed) || !trimmed.contains("/"))) {
                trimmed = "http://$trimmed"
            }
        }
        return trimmed
    }

    internal fun stripUserInfo(serverUrl: String): String {
        val trimmed = serverUrl.trim()
        val schemeSeparator = "://"
        val schemeIndex = trimmed.indexOf(schemeSeparator)
        val scheme = if (schemeIndex != -1) trimmed.substring(0, schemeIndex + schemeSeparator.length) else ""
        val rest = if (schemeIndex != -1) trimmed.substring(schemeIndex + schemeSeparator.length) else trimmed
        
        val authorityEnd = rest.indexOfFirst { it == '/' || it == '?' || it == '#' }
        val authority = if (authorityEnd != -1) rest.substring(0, authorityEnd) else rest
        val pathAndQuery = if (authorityEnd != -1) rest.substring(authorityEnd) else ""
        
        if (!authority.contains('@')) return trimmed
        
        val hostPort = authority.substringAfterLast('@')
        return scheme + hostPort + pathAndQuery
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
        val normalizedUser = normalizePortalUrl(normalizedUserUrl) ?: normalizedUserUrl.trim()
        if (hasExplicitNonDefaultPort(normalizedUser)) {
            // User supplied a working host:port — never override with server_info https/443.
            return normalizedUser
        }
        val userHost = extractHost(normalizedUser) ?: return normalizedUser
        val infoHost = serverUrl.trim()
        val hostForCatalog = when {
            infoHost.isBlank() -> userHost
            hostsEquivalent(userHost, infoHost) -> infoHost
            else -> userHost
        }
        return canonicalizeFromServerInfo(
            normalizedUserUrl = normalizedUser,
            serverUrl = hostForCatalog,
            port = port,
            httpsPort = httpsPort,
            serverProtocol = serverProtocol
        )
    }

    /**
     * Expands a portal base URL into auth candidates: primary, alternate scheme, then common
     * Xtream ports when the URL uses implicit 80/443 or no port.
     */
    fun expandAuthBaseUrls(normalizedBaseUrl: String): List<String> {
        val normalized = normalizePortalUrl(normalizedBaseUrl)
            ?: normalizedBaseUrl.trim().trimEnd('/').substringBefore('?')
        val result = linkedSetOf<String>()
        result.add(normalized)
        alternateSchemeUrl(normalized)?.let { result.add(it) }

        val uri = runCatching { java.net.URI(normalized) }.getOrNull() ?: return result.toList()
        val host = uri.robustHost?.trim()?.takeIf { it.isNotBlank() } ?: return result.toList()
        if (!usesDefaultOrImplicitPort(uri)) return result.toList()

        val pathSuffix = extractSubdirectoryPath(normalized).orEmpty()
        for (port in COMMON_XTREAM_PORTS) {
            if (uri.robustPort == port) continue
            result.add("http://$host:$port$pathSuffix")
            if (port == 443 || port == 25463) {
                result.add("https://$host:$port$pathSuffix")
            }
        }
        return result.toList()
    }

    internal fun hasExplicitNonDefaultPort(normalizedUrl: String): Boolean {
        val uri = runCatching { java.net.URI(normalizedUrl.trim()) }.getOrNull() ?: return false
        val port = uri.robustPort
        if (port <= 0) return false
        val scheme = uri.scheme?.lowercase().orEmpty()
        return !((scheme == "http" && port == 80) || (scheme == "https" && port == 443))
    }

    internal fun usesDefaultOrImplicitPort(uri: java.net.URI): Boolean {
        val port = uri.robustPort
        if (port <= 0) return true
        val scheme = uri.scheme?.lowercase().orEmpty()
        return (scheme == "http" && port == 80) || (scheme == "https" && port == 443)
    }

    internal fun extractHost(normalizedUrl: String): String? {
        return try {
            java.net.URI(normalizedUrl.trim()).robustHost?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    internal fun hostsEquivalent(userHost: String, serverInfoHost: String): Boolean {
        val user = userHost.trim().lowercase()
        val infoRaw = serverInfoHost.trim().lowercase()
        val info = (runCatching { java.net.URI(infoRaw) }.getOrNull()?.robustHost ?: infoRaw).lowercase()
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
        val hostRaw = serverUrl.trim()
        val host = runCatching { java.net.URI(hostRaw) }.getOrNull()?.robustHost ?: hostRaw
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
            val host = uri.robustHost ?: return null
            val port = uri.robustPort
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
