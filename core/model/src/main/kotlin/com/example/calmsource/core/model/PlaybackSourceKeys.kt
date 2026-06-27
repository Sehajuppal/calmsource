package com.example.calmsource.core.model

/**
 * Stable health key for watch-option → [PlaybackSource] mapping.
 *
 * IPTV anchors to the pseudo-URL hash so the key matches guide playback and survives URL
 * resolution. Extension and debrid streams use the watch-option id, which stays stable across
 * magnet/debrid resolution.
 */
fun stableSourceIdForWatchOption(
    type: SourceType,
    rawUrl: String,
    optionId: String,
): String? = when (type) {
    SourceType.IPTV -> generateSafeSourceId(rawUrl)
    SourceType.EXTENSION, SourceType.DEBRID -> optionId
}
