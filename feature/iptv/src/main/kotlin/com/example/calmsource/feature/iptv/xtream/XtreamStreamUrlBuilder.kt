package com.example.calmsource.feature.iptv.xtream

import java.net.URLEncoder

/**
 * Builds Xtream stream playback URLs at playback time.
 *
 * URLs contain credentials (username/password in path) and must:
 * - NEVER be logged
 * - NEVER be persisted in Room
 * - NEVER be displayed in UI
 * - Be redacted via UrlRedactor before any display/logging
 */
object XtreamStreamUrlBuilder {
    private val STREAM_ID = Regex("[a-zA-Z0-9_.-]+")

    /** URL-encode a credential path segment safely. */
    private fun encodePathSegment(value: String): String =
        URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8.name()).replace("+", "%20")

    /**
     * Normalizes a portal URL for stream path construction (may include a subdirectory base path).
     */
    private fun normalizeBaseUrl(serverUrl: String): String {
        return XtreamServerUrlNormalizer.normalizePortalUrl(serverUrl)
            ?: serverUrl.trim().trimEnd('/').substringBefore('?')
    }

    /**
     * Build live stream URL: {serverUrl}/live/{username}/{password}/{streamId}.{outputFormat}
     *
     * @param serverUrl The base server URL (e.g. http://example.com:8080)
     * @param username The Xtream username
     * @param password The Xtream password (from SecureTokenStore)
     * @param streamId The stream ID from the Xtream API
     * @param outputFormat Optional format (ts, m3u8). Default: ts
     */
    fun buildLiveUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        outputFormat: String = "ts"
    ): String? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || streamId.isBlank()) return null
        if (!STREAM_ID.matches(streamId)) return null
        val base = normalizeBaseUrl(serverUrl)
        if (base.isEmpty()) return null
        return "$base/live/${encodePathSegment(username)}/${encodePathSegment(password)}/$streamId.$outputFormat"
    }

    /**
     * Preferred live output formats for playback fallback (primary first).
     */
    fun liveOutputFormatCandidates(primaryFormat: String = "ts"): List<String> {
        return when (normalizeLiveOutputFormat(primaryFormat)) {
            "m3u8" -> listOf("m3u8", "ts")
            else -> listOf("ts", "m3u8")
        }
    }

    private fun normalizeLiveOutputFormat(format: String): String? {
        return when (format.trim().lowercase().removePrefix(".")) {
            "m3u8", "hls" -> "m3u8"
            "ts", "mpegts" -> "ts"
            else -> null
        }
    }

    /**
     * Build VOD stream URL: {serverUrl}/movie/{username}/{password}/{streamId}.{extension}
     */
    fun buildVodUrl(
        serverUrl: String,
        username: String,
        password: String,
        streamId: String,
        containerExtension: String = "mp4"
    ): String? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || streamId.isBlank()) return null
        if (!STREAM_ID.matches(streamId)) return null
        val base = normalizeBaseUrl(serverUrl)
        if (base.isEmpty()) return null
        return "$base/movie/${encodePathSegment(username)}/${encodePathSegment(password)}/$streamId.$containerExtension"
    }

    /**
     * Build series episode URL: {serverUrl}/series/{username}/{password}/{episodeId}.{extension}
     */
    fun buildSeriesUrl(
        serverUrl: String,
        username: String,
        password: String,
        episodeId: String,
        containerExtension: String = "mp4"
    ): String? {
        if (serverUrl.isBlank() || username.isBlank() || password.isBlank() || episodeId.isBlank()) return null
        if (!STREAM_ID.matches(episodeId)) return null
        val base = normalizeBaseUrl(serverUrl)
        if (base.isEmpty()) return null
        return "$base/series/${encodePathSegment(username)}/${encodePathSegment(password)}/$episodeId.$containerExtension"
    }

    /**
     * Extract the raw suffix from an xtream:// pseudo-URL (everything after xtream://stream_id/).
     * Returns the suffix, or null if not an Xtream pseudo-URL.
     */
    internal fun extractRawSuffix(pseudoUrl: String?): String? {
        if (pseudoUrl == null || !pseudoUrl.startsWith("xtream://stream_id/")) return null
        return pseudoUrl.removePrefix("xtream://stream_id/").ifBlank { null }
    }

    /**
     * Extract stream ID from an xtream:// pseudo-URL stored in IPTVChannel.streamUrl.
     * Handles both formats:
     *   - New: xtream://stream_id/{providerId}/{streamId}
     *   - Legacy: xtream://stream_id/{streamId}
     * Returns the stream ID string, or null if not an Xtream pseudo-URL or the id is invalid.
     */
    fun extractStreamId(pseudoUrl: String?): String? {
        val suffix = extractRawSuffix(pseudoUrl) ?: return null
        val parts = suffix.split("/").filter { it.isNotEmpty() }
        // New format: providerId/streamId
        val candidate = parts.lastOrNull() ?: return null
        return candidate.takeIf { STREAM_ID.matches(it) }
    }

    /**
     * Extract the provider ID from an xtream:// pseudo-URL.
     * New format: xtream://stream_id/{providerId}/{streamId} → returns providerId
     * Legacy format: xtream://stream_id/{streamId} → returns null
     */
    fun extractProviderId(pseudoUrl: String?): String? {
        val suffix = extractRawSuffix(pseudoUrl) ?: return null
        val parts = suffix.split("/").filter { it.isNotEmpty() }
        return if (parts.size >= 2) parts[parts.size - 2].takeIf { it.isNotBlank() } else null
    }

    /**
     * Create the xtream:// pseudo-URL to store in IPTVChannel.streamUrl.
     * Format: xtream://stream_id/{providerId}/{streamId}
     * This is a safe placeholder that does NOT contain credentials.
     * Including the providerId ensures unique safeSourceIds across different providers.
     * Returns null if streamId is invalid (malformed API response).
     */
    fun createPseudoUrl(providerId: String, streamId: String): String? {
        if (!STREAM_ID.matches(streamId)) return null
        if (providerId.isBlank()) return null
        return "xtream://stream_id/$providerId/$streamId"
    }
}
