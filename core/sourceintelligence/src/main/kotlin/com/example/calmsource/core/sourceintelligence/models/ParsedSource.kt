package com.example.calmsource.core.sourceintelligence.models

import com.example.calmsource.core.model.PlaybackSourceType

/**
 * The strongly-typed internal representation of source metadata.
 * Ensures that all data passing through the system conforms to known fields.
 * CRITICAL PRIVACY: The [rawUrl] and [rawFilename] are kept isolated and 
 * must never be exposed in UI or logs directly.
 */
data class ParsedSource(
    val id: String,
    val type: PlaybackSourceType,
    val title: String,
    val quality: String? = null,
    val sizeBytes: Long? = null,
    val seeders: Int? = null,
    val origin: String,
    private val rawUrl: String,
    private val rawFilename: String? = null
) {
    /**
     * Safely redacted URL for display and logging.
     */
    val displayUrl: String
        get() = redactUrl(rawUrl)

    /**
     * Safely redacted filename for UI strings. 
     * Hides the extension and sensitive parts.
     */
    val displayFilename: String
        get() = redactFilename(rawFilename)

    /**
     * Returns the raw URL only when strictly necessary for playback.
     */
    fun getRawUrlUnsafe(): String = rawUrl

    companion object {
        fun redactUrl(url: String): String {
            try {
                val index = url.indexOf("://")
                if (index == -1) return "redacted-url"
                val scheme = url.substring(0, index)
                val rest = url.substring(index + 3)
                
                var endOfHost = rest.indexOf('/')
                val queryIndex = rest.indexOf('?')
                val fragmentIndex = rest.indexOf('#')
                
                if (queryIndex != -1 && (endOfHost == -1 || queryIndex < endOfHost)) {
                    endOfHost = queryIndex
                }
                if (fragmentIndex != -1 && (endOfHost == -1 || fragmentIndex < endOfHost)) {
                    endOfHost = fragmentIndex
                }

                val hostPart = if (endOfHost == -1) rest else rest.substring(0, endOfHost)
                val atIndex = hostPart.lastIndexOf('@')
                val safeHost = if (atIndex == -1) hostPart else hostPart.substring(atIndex + 1)
                
                return "$scheme://$safeHost/..."
            } catch (e: Exception) {
                return "redacted-url"
            }
        }

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
}
