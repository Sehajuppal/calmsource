package com.example.calmsource.core.model

/**
 * Represents the fundamental type of a playback track.
 */
enum class TrackType {
    VIDEO,
    AUDIO,
    SUBTITLE,
    UNKNOWN
}

/**
 * Foundation model for generic playback track information.
 */
data class PlaybackTrackInfo(
    val id: String,
    val name: String,
    val type: TrackType,
    val language: String? = null,
    val isSelected: Boolean = false
)

/**
 * Represents a subtitle track, potentially from a third-party source like Stremio extensions.
 */
data class PlaybackSubtitleTrack(
    val id: String,
    val name: String,
    val language: String? = null,
    val url: String? = null,
    val isSelected: Boolean = false,
    
    // Safety flag for extension subtitles to prevent blind auto-loading
    val isSafeToLoad: Boolean = false
) {
    /**
     * Redacted view to prevent logging of sensitive URLs.
     */
    override fun toString(): String {
        return "PlaybackSubtitleTrack(id='$id', name='$name', language=$language, " +
               "url=${if (url != null) "***REDACTED***" else "null"}, " +
               "isSelected=$isSelected, isSafeToLoad=$isSafeToLoad)"
    }
}

/**
 * Represents an audio track in a playback session.
 */
data class PlaybackAudioTrack(
    val id: String,
    val name: String,
    val language: String? = null,
    val channels: Int? = null,
    val isSelected: Boolean = false
)

/**
 * Represents video quality information (e.g., 1080p, 4K).
 */
data class PlaybackQualityInfo(
    val id: String,
    val name: String,
    val width: Int? = null,
    val height: Int? = null,
    val bitrate: Int? = null,
    val isSelected: Boolean = false
)
