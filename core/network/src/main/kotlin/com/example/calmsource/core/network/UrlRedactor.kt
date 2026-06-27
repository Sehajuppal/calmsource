package com.example.calmsource.core.network

import com.example.calmsource.core.model.maskToken

object UrlRedactor {

    /** Default query parameter names that contain sensitive credentials. */
    private val DEFAULT_PARAMS_TO_REDACT = listOf(
        "token", "apikey", "key", "auth", "password", "pass", "pwd", "secret",
        "access_token", "refresh_token", "api_key", "device_code", "pin",
        "username", "user", "realdebrid", "alldebrid", "premiumize", "debridapi", "code"
    )

    /** Regex matching URLs in free-form text. */
    private val URL_PATTERN = Regex("""(?i)\b(?:https?|xtream|acestream|sop|magnet|pipe):[^\s"'<>\])}]+""")

    fun redactQueryString(query: String, paramsToRedact: List<String> = DEFAULT_PARAMS_TO_REDACT): String {
        val sb = java.lang.StringBuilder()
        var lastIdx = 0
        var idx = 0
        val len = query.length
        while (idx <= len) {
            if (idx == len || query[idx] == '&' || query[idx] == ';') {
                val delimiter = if (idx < len) query[idx].toString() else ""
                val part = query.substring(lastIdx, idx)
                
                val eqIdx = part.indexOf('=')
                val redactedPart = if (eqIdx > 0) {
                    val key = part.substring(0, eqIdx)
                    val value = part.substring(eqIdx + 1)
                    
                    val cleanKey = key.trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                    val decodedKey = try {
                        java.net.URLDecoder.decode(key, "UTF-8").trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                    } catch (e: Exception) {
                        cleanKey
                    }
                    
                    val isSecret = paramsToRedact.any {
                        it.equals(cleanKey, ignoreCase = true) || it.equals(decodedKey, ignoreCase = true)
                    }
                    if (isSecret) {
                        "$key=REDACTED"
                    } else {
                        part
                    }
                } else {
                    part
                }
                sb.append(redactedPart)
                if (delimiter.isNotEmpty()) {
                    sb.append(delimiter)
                }
                lastIdx = idx + 1
            }
            idx++
        }
        return sb.toString()
    }

    fun redactUrl(url: String, paramsToRedact: List<String> = DEFAULT_PARAMS_TO_REDACT): String {
        try {
            val colonIdx = url.indexOf(':')
            if (colonIdx == -1) return "REDACTED_INVALID_URL"
            val scheme = url.substring(0, colonIdx)
            var rest = url.substring(colonIdx + 1)

            // Separate fragment
            val hashIdx = rest.indexOf('#')
            val fragment = if (hashIdx != -1) {
                "#" + redactQueryString(rest.substring(hashIdx + 1), paramsToRedact)
            } else ""
            if (hashIdx != -1) {
                rest = rest.substring(0, hashIdx)
            }

            // Separate query
            val qIdx = rest.indexOf('?')
            val query = if (qIdx != -1) rest.substring(qIdx + 1) else null
            if (qIdx != -1) {
                rest = rest.substring(0, qIdx)
            }

            // Remaining part: authority + path
            val isHierarchical = rest.startsWith("//")
            var authorityAndHost = ""
            var path = ""
            if (isHierarchical) {
                val firstSlashIdx = rest.indexOf('/', 2)
                if (firstSlashIdx != -1) {
                    authorityAndHost = rest.substring(0, firstSlashIdx)
                    path = rest.substring(firstSlashIdx)
                } else {
                    authorityAndHost = rest
                    path = ""
                }
                // Strip basic auth
                val atIdx = authorityAndHost.indexOf('@', 2)
                if (atIdx != -1) {
                    authorityAndHost = "//REDACTED@" + authorityAndHost.substring(atIdx + 1)
                }
            } else {
                authorityAndHost = ""
                path = rest
            }

            val isXtreamAuth = authorityAndHost.endsWith("//live", ignoreCase = true) ||
                    authorityAndHost.endsWith("//movie", ignoreCase = true) ||
                    authorityAndHost.endsWith("//series", ignoreCase = true)

            val redactedPath = redactPathSecrets(path, paramsToRedact, isXtreamAuth)
            val redactedQuery = if (query != null) "?" + redactQueryString(query, paramsToRedact) else ""

            return "$scheme:$authorityAndHost$redactedPath$redactedQuery$fragment"
        } catch (e: Exception) {
            return "REDACTED_INVALID_URL"
        }
    }

    fun redactPathSecrets(path: String, paramsToRedact: List<String> = DEFAULT_PARAMS_TO_REDACT, isXtreamAuth: Boolean = false): String {
        val xtreamPathRedacted = redactXtreamPath(path, isXtreamAuth)

        // Then: handle key=value patterns in path segments (Stremio addon URLs)
        val segments = xtreamPathRedacted.split("/")
        val redactedSegments = segments.map { segment ->
            if (segment.contains("=")) {
                // This segment contains key=value pairs (e.g., "api_key=secret&lang=en")
                val pairs = segment.split("&", ";")
                val redactedPairs = pairs.map { pair ->
                    val eqIdx = pair.indexOf('=')
                    if (eqIdx > 0) {
                        val key = pair.substring(0, eqIdx)
                        val isSecret = paramsToRedact.any { it.equals(key, ignoreCase = true) }
                        if (isSecret) {
                            "$key=REDACTED"
                        } else {
                            pair
                        }
                    } else {
                        pair
                    }
                }
                redactedPairs.joinToString("&")
            } else {
                segment
            }
        }
        return redactedSegments.joinToString("/")
    }

    private fun redactXtreamPath(path: String, isXtreamAuth: Boolean): String {
        val delimiterRegex = Regex("(?i)/|%2F")
        val segments = path.split(delimiterRegex).toMutableList()
        val delimiters = delimiterRegex.findAll(path).map { it.value }.toList()
        val nonEmptyIndices = segments.indices.filter { segments[it].isNotEmpty() }

        if (isXtreamAuth) {
            if (nonEmptyIndices.isNotEmpty()) {
                segments[nonEmptyIndices[0]] = "REDACTED"
            }
            if (nonEmptyIndices.size > 1) {
                segments[nonEmptyIndices[1]] = "REDACTED"
            }
        } else if (looksLikeXtreamCredentialPath(segments, nonEmptyIndices)) {
            val targetIndexInNonEmpty = nonEmptyIndices.indexOfFirst { idx ->
                val segment = segments[idx]
                segment.equals("live", ignoreCase = true) ||
                segment.equals("movie", ignoreCase = true) ||
                segment.equals("series", ignoreCase = true)
            }
            if (targetIndexInNonEmpty != -1) {
                val precedingNonEmpty = nonEmptyIndices.take(targetIndexInNonEmpty).map { segments[it] }
                val hasPrecedingStremio = precedingNonEmpty.any { segment ->
                    segment.equals("catalog", ignoreCase = true) ||
                    segment.equals("meta", ignoreCase = true) ||
                    segment.equals("stream", ignoreCase = true) ||
                    segment.equals("subtitles", ignoreCase = true)
                }
                if (!hasPrecedingStremio) {
                    val remainingSegments = nonEmptyIndices.size - (targetIndexInNonEmpty + 1)
                    if (remainingSegments >= 3) {
                        if (targetIndexInNonEmpty + 1 < nonEmptyIndices.size) {
                            segments[nonEmptyIndices[targetIndexInNonEmpty + 1]] = "REDACTED"
                        }
                        if (targetIndexInNonEmpty + 2 < nonEmptyIndices.size) {
                            segments[nonEmptyIndices[targetIndexInNonEmpty + 2]] = "REDACTED"
                        }
                    }
                }
            }
        }

        val sb = StringBuilder()
        for (i in segments.indices) {
            sb.append(segments[i])
            if (i < delimiters.size) {
                sb.append(delimiters[i])
            }
        }
        return sb.toString()
    }

    /**
     * Generic URLs are only treated as Xtream paths when the terminal stream id has
     * Xtream's numeric shape. This avoids mangling ordinary CDN paths such as
     * /live/news/720p/index.m3u8 solely because they contain a "live" segment.
     */
    private fun looksLikeXtreamCredentialPath(
        segments: List<String>,
        nonEmptyIndices: List<Int>
    ): Boolean {
        val terminal = nonEmptyIndices.lastOrNull()?.let(segments::get) ?: return false
        val streamId = terminal.substringBefore('.').substringBefore('?')
        if (streamId.isEmpty() || !streamId.all(Char::isDigit)) return false
        return nonEmptyIndices.any { index ->
            segments[index].equals("live", ignoreCase = true) ||
                segments[index].equals("movie", ignoreCase = true) ||
                segments[index].equals("series", ignoreCase = true)
        }
    }

    fun redactToken(value: String?): String {
        if (value.isNullOrBlank()) return "••••••••"
        return value.maskToken()
    }

    fun redactPrivateLink(url: String): String {
        return try {
            val colonIdx = url.indexOf(':')
            if (colonIdx == -1) return "REDACTED_PRIVATE_LINK"
            val scheme = url.substring(0, colonIdx)
            var rest = url.substring(colonIdx + 1)
            
            if (rest.startsWith("//")) {
                val nextSlash = rest.indexOf('/', 2)
                var authorityAndHost = if (nextSlash != -1) rest.substring(0, nextSlash) else rest
                
                // Strip basic auth
                val atIdx = authorityAndHost.indexOf('@', 2)
                if (atIdx != -1) {
                    authorityAndHost = "//" + authorityAndHost.substring(atIdx + 1)
                }
                "$scheme:$authorityAndHost/...REDACTED"
            } else {
                "$scheme:/...REDACTED"
            }
        } catch (e: Exception) {
            "REDACTED_PRIVATE_LINK"
        }
    }

    fun extractSecretsFromUrl(url: String, paramsToRedact: List<String> = DEFAULT_PARAMS_TO_REDACT): List<String> {
        val secrets = mutableListOf<String>()
        try {
            val colonIdx = url.indexOf(':')
            if (colonIdx == -1) return emptyList()
            var rest = url.substring(colonIdx + 1)
            
            // Remove fragment
            val hashIdx = rest.indexOf('#')
            if (hashIdx != -1) {
                rest = rest.substring(0, hashIdx)
            }
            
            // Extract query
            val qIdx = rest.indexOf('?')
            val query = if (qIdx != -1) rest.substring(qIdx + 1) else null
            if (qIdx != -1) {
                rest = rest.substring(0, qIdx)
            }
            
            // Extract secrets from query
            if (query != null) {
                var lastIdx = 0
                var idx = 0
                val len = query.length
                while (idx <= len) {
                    if (idx == len || query[idx] == '&' || query[idx] == ';') {
                        val part = query.substring(lastIdx, idx)
                        val eqIdx = part.indexOf('=')
                        if (eqIdx > 0) {
                            val key = part.substring(0, eqIdx)
                            val value = part.substring(eqIdx + 1)
                            
                            val cleanKey = key.trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                            val decodedKey = try {
                                java.net.URLDecoder.decode(key, "UTF-8").trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                            } catch (e: Exception) {
                                cleanKey
                            }
                            
                            if (paramsToRedact.any { it.equals(cleanKey, ignoreCase = true) || it.equals(decodedKey, ignoreCase = true) } && value.isNotBlank() && value != "REDACTED") {
                                secrets.add(value)
                                try { secrets.add(java.net.URLDecoder.decode(value, "UTF-8")) } catch(e: Exception) {}
                                try { secrets.add(java.net.URLEncoder.encode(value, "UTF-8")) } catch(e: Exception) {}
                            }
                        }
                        lastIdx = idx + 1
                    }
                    idx++
                }
            }
            
            // Extract secrets from path and userInfo
            val isHierarchical = rest.startsWith("//")
            var authorityAndHost = ""
            var path = ""
            if (isHierarchical) {
                val firstSlashIdx = rest.indexOf('/', 2)
                if (firstSlashIdx != -1) {
                    authorityAndHost = rest.substring(0, firstSlashIdx)
                    path = rest.substring(firstSlashIdx)
                } else {
                    authorityAndHost = rest
                    path = ""
                }
                
                // Extract from basic auth
                val atIdx = authorityAndHost.indexOf('@', 2)
                if (atIdx != -1) {
                    val userInfo = authorityAndHost.substring(2, atIdx)
                    userInfo.split(":", limit = 2).forEach { part ->
                        if (part.isNotBlank() && part != "REDACTED") {
                            secrets.add(part)
                            try { secrets.add(java.net.URLDecoder.decode(part, "UTF-8")) } catch(e: Exception) {}
                            try { secrets.add(java.net.URLEncoder.encode(part, "UTF-8")) } catch(e: Exception) {}
                        }
                    }
                }
            } else {
                path = rest
            }
            
            // Extract from path
            val pathDelimiterRegex = Regex("(?i)/|%2F")
            val segments = path.split(pathDelimiterRegex)
            val nonEmptyIndices = segments.indices.filter { segments[it].isNotEmpty() }
            val isXtreamAuth = authorityAndHost.endsWith("//live", ignoreCase = true) ||
                    authorityAndHost.endsWith("//movie", ignoreCase = true) ||
                    authorityAndHost.endsWith("//series", ignoreCase = true)
            
            if (isXtreamAuth) {
                if (nonEmptyIndices.isNotEmpty()) {
                    val username = segments[nonEmptyIndices[0]]
                    if (username.isNotBlank() && username != "REDACTED") {
                        secrets.add(username)
                        try { secrets.add(java.net.URLDecoder.decode(username, "UTF-8")) } catch(e: Exception) {}
                        try { secrets.add(java.net.URLEncoder.encode(username, "UTF-8")) } catch(e: Exception) {}
                    }
                }
                if (nonEmptyIndices.size > 1) {
                    val password = segments[nonEmptyIndices[1]]
                    if (password.isNotBlank() && password != "REDACTED") {
                        secrets.add(password)
                        try { secrets.add(java.net.URLDecoder.decode(password, "UTF-8")) } catch(e: Exception) {}
                        try { secrets.add(java.net.URLEncoder.encode(password, "UTF-8")) } catch(e: Exception) {}
                    }
                }
            } else {
                val targetIndexInNonEmpty = nonEmptyIndices.indexOfFirst { idx ->
                    val segment = segments[idx]
                    segment.equals("live", ignoreCase = true) ||
                    segment.equals("movie", ignoreCase = true) ||
                    segment.equals("series", ignoreCase = true)
                }
                if (targetIndexInNonEmpty != -1) {
                    val precedingNonEmpty = nonEmptyIndices.take(targetIndexInNonEmpty).map { segments[it] }
                    val hasPrecedingStremio = precedingNonEmpty.any { segment ->
                        segment.equals("catalog", ignoreCase = true) ||
                        segment.equals("meta", ignoreCase = true) ||
                        segment.equals("stream", ignoreCase = true) ||
                        segment.equals("subtitles", ignoreCase = true)
                    }
                    if (!hasPrecedingStremio) {
                        if (targetIndexInNonEmpty + 1 < nonEmptyIndices.size) {
                            val username = segments[nonEmptyIndices[targetIndexInNonEmpty + 1]]
                            if (username.isNotBlank() && username != "REDACTED") {
                                secrets.add(username)
                                try { secrets.add(java.net.URLDecoder.decode(username, "UTF-8")) } catch(e: Exception) {}
                                try { secrets.add(java.net.URLEncoder.encode(username, "UTF-8")) } catch(e: Exception) {}
                            }
                        }
                        if (targetIndexInNonEmpty + 2 < nonEmptyIndices.size) {
                            val password = segments[nonEmptyIndices[targetIndexInNonEmpty + 2]]
                            if (password.isNotBlank() && password != "REDACTED") {
                                secrets.add(password)
                                try { secrets.add(java.net.URLDecoder.decode(password, "UTF-8")) } catch(e: Exception) {}
                                try { secrets.add(java.net.URLEncoder.encode(password, "UTF-8")) } catch(e: Exception) {}
                            }
                        }
                    }
                }
            }
            
            // Also scan path segments for key=value patterns
            segments.forEach { segment ->
                if (segment.contains("=")) {
                    segment.split("&", ";").forEach { pair ->
                        val eqIdx = pair.indexOf('=')
                        if (eqIdx > 0) {
                            val key = pair.substring(0, eqIdx)
                            val value = pair.substring(eqIdx + 1)
                            
                            val cleanKey = key.trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                            val decodedKey = try {
                                java.net.URLDecoder.decode(key, "UTF-8").trim().replace(" ", "").replace("%20", "", ignoreCase = true).replace("+", "")
                            } catch (e: Exception) {
                                cleanKey
                            }
                            
                            if (paramsToRedact.any { it.equals(cleanKey, ignoreCase = true) || it.equals(decodedKey, ignoreCase = true) } && value.isNotBlank() && value != "REDACTED") {
                                secrets.add(value)
                                try { secrets.add(java.net.URLDecoder.decode(value, "UTF-8")) } catch(e: Exception) {}
                                try { secrets.add(java.net.URLEncoder.encode(value, "UTF-8")) } catch(e: Exception) {}
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore
        }
        return secrets.distinct()
    }

    fun redactErrorMessage(message: String, requestUrl: String? = null): String {
        var redacted = URL_PATTERN.replace(message) { matchResult ->
            redactUrl(matchResult.value)
        }
        if (requestUrl != null) {
            val secrets = extractSecretsFromUrl(requestUrl)
            for (secret in secrets) {
                redacted = redacted.replace(secret, "REDACTED")
            }
        }
        return redacted
    }

    /**
     * Safely redacted filename for UI strings.
     * Hides the extension and sensitive parts.
     */
    fun redactFilename(filename: String?): String {
        if (filename.isNullOrBlank()) return "Unknown File"
        val lastDot = filename.lastIndexOf('.')
        return if (lastDot != -1 && lastDot > 0) {
            filename.substring(0, lastDot) + ".[REDACTED]"
        } else {
            "$filename.[REDACTED]"
        }
    }
}
