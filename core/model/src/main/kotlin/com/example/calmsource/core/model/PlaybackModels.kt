package com.example.calmsource.core.model

/**
 * DRM scheme used by a protected stream.
 */
enum class DrmScheme(val uuid: String) {
    WIDEVINE("edef8ba9-79d6-4ace-a3c8-27dcd51d21ed"),
    PLAYREADY("9a04f079-9840-4286-ab92-e65be0885f95"),
    CLEARKEY("e2719d58-a985-b3c9-781a-b030af78d30e")
}

/**
 * DRM configuration for a protected playback source.
 */
data class PlaybackDrmConfiguration(
    val scheme: DrmScheme,
    val licenseUri: String,
    val keyRequestHeaders: Map<String, String> = emptyMap()
)

/**
 * Type of the playback source.
 */
enum class PlaybackSourceType {
    IPTV,
    EXTENSION,
    STREMIO,
    SAFE_TEST_STREAM,
    DEBRID_RESOLVED,
    UNKNOWN
}

/**
 * Metadata associated with a playback item.
 */
data class PlaybackItemMetadata(
    val title: String,
    val description: String? = null,
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val durationMs: Long? = null,
    val isLive: Boolean = false,
    val seriesName: String? = null,
    val seasonNumber: Int? = null,
    val episodeNumber: Int? = null,
    val genre: String? = null,
    val containerFormat: String? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null
)

/**
 * Represents a source of playback.
 * CRITICAL PRIVACY: `rawUrl` contains sensitive information (like tokens/auth) and MUST NOT be
 * exposed in UI or logs by default. Use `redactedUrl` or `displayUrl` for UI purposes.
 */
data class PlaybackSource(
    val id: String,
    val type: PlaybackSourceType,
    val title: String,
    val rawUrl: String, // Keep isolated, never persist unless explicitly safe
    val displayUrl: String = redactUrl(rawUrl),
    val metadata: PlaybackItemMetadata? = null,
    val headers: Map<String, String> = emptyMap(),
    val allowInsecureHttp: Boolean = false,
    val drmConfiguration: PlaybackDrmConfiguration? = null,
    /**
     * Stable health/identity key that survives URL resolution. For IPTV the [rawUrl] starts as an
     * `xtream://` pseudo URL and is rewritten to a real `http(s)://` URL at play time; hashing the
     * resolved URL would produce a different key than the repository uses, so callers set this to
     * the channel's pseudo-URL hash to keep health keys consistent across the resolve boundary and
     * across alternate-container fallbacks (bug #5).
     */
    val stableSourceId: String? = null
) {
    val safeSourceId: String
        get() = stableSourceId ?: if (type == PlaybackSourceType.IPTV) {
            generateSafeSourceId(rawUrl)
        } else {
            id
        }
    companion object {
        const val XTREAM_SERIES_EPISODE_SOURCE_PREFIX = "xtream-series-episode|"

        fun redactUrl(url: String): String {
            try {
                val index = url.indexOf("://")
                if (index == -1) return "redacted-url"
                val scheme = url.substring(0, index)
                val rest = url.substring(index + 3)

                // Determine host end, handling IPv6 bracket-wrapped addresses
                val hostEnd = findHostEnd(rest)

                val hostPart = if (hostEnd == -1) rest else rest.substring(0, hostEnd)

                val atIndex = hostPart.lastIndexOf('@')
                val safeHost = if (atIndex == -1) hostPart else hostPart.substring(atIndex + 1)

                return "$scheme://$safeHost/..."
            } catch (e: Exception) {
                return "redacted-url"
            }
        }

        private fun findHostEnd(rest: String): Int {
            // For IPv6 bracket addresses like [::1], treat the bracket + optional :port as the host
            if (rest.startsWith("[")) {
                val close = rest.indexOf(']')
                if (close == -1) return rest.indexOf('/') // malformed, fallback
                // Host ends at the first /, ?, or # after the closing bracket
                val afterBracket = rest.indexOfAny(charArrayOf('/', '?', '#'), close + 1)
                return afterBracket
            }
            // Standard: host ends at first /, ?, or #
            return rest.indexOfAny(charArrayOf('/', '?', '#'))
        }
    }
}

/**
 * Represents a request to play a source.
 */
data class PlaybackRequest(
    val source: PlaybackSource,
    val startPositionMs: Long = 0L,
    val playWhenReady: Boolean = true,
    val userMemoryReference: UserMemoryReference? = null
)

/**
 * Player state.
 */
enum class PlayerState {
    IDLE,
    PREPARING,
    BUFFERING,
    READY,
    PLAYING,
    PAUSED,
    ENDED,
    FAILED
}

/**
 * Diagnostics for playback session.
 */
data class PlaybackDiagnostics(
    val videoResolution: String? = null,
    val videoBitrate: Int? = null,
    val audioBitrate: Int? = null,
    val frameRate: Float? = null,
    val droppedFrames: Int = 0,
    val decoderName: String? = null,
    val bufferHealthMs: Long = 0L
)

/**
 * Categories of Playback Errors.
 */
sealed interface PlaybackError {
    val message: String
    val cause: Throwable?

    data class Network(
        override val message: String = "Network error occurred",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class UnsupportedFormat(
        override val message: String = "Unsupported media format",
        override val cause: Throwable? = null,
        val retryableSources: List<PlaybackSource>? = null
    ) : PlaybackError

    data class Timeout(
        override val message: String = "Playback timed out",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class PermissionRequired(
        override val message: String = "Permission or authentication required",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class SourceUnavailable(
        override val message: String = "Playback source is unavailable",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class DecoderError(
        override val message: String = "Media decoder error",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class Drm(
        override val message: String = "This stream uses DRM that could not be opened on this device",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class ServerRefused(
        override val message: String = "Server refused connection",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class CleartextNotPermitted(
        override val message: String = "Cleartext HTTP traffic is blocked by security policy. You can enable HTTP sources in Settings.",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class TerminalError(
        override val message: String = "Multiple sources failed. Please pick a manual link.",
        override val cause: Throwable? = null
    ) : PlaybackError

    data class Unknown(
        override val message: String = "Unknown playback error",
        override val cause: Throwable? = null
    ) : PlaybackError
}

/**
 * UI State for the Player.
 */
data class PlaybackProgressState(
    val currentPositionMs: Long = 0L,
    val durationMs: Long = 0L,
    val bufferedPositionMs: Long = 0L
)

/**
 * UI State for the Player.
 */
data class PlayerUiState(
    val playerState: PlayerState = PlayerState.IDLE,
    val source: PlaybackSource? = null,
    val volume: Float = 1.0f,
    val isMuted: Boolean = false,
    val playbackSpeed: Float = 1.0f,
    val error: PlaybackError? = null,
    val isControlVisible: Boolean = false,
    val diagnostics: PlaybackDiagnostics = PlaybackDiagnostics(),
    val sessionDiagnostics: PlaybackSessionDiagnostics = PlaybackSessionDiagnostics(),
    val fallbackMessage: String? = null,
    val isTerminal: Boolean = false,
    val isTransitioningSource: Boolean = false
)
