package com.example.calmsource.core.playback

import androidx.media3.common.MimeTypes

/**
 * Utility for resolving stream formats and building retry sequences when
 * ExoPlayer cannot automatically identify a media container.
 */
object StreamFormatFallback {

    /**
     * Builds a sequence of MIME types to try for a given URI when the initial
     * attempt (usually auto-detection) fails.
     */
    fun buildMimeRetrySequence(uri: String, containerFormat: String? = null): List<String?> {
        val baseSequence = buildMimeRetrySequenceForUri(uri)
        val formatHint = containerFormat?.trim()?.lowercase()?.removePrefix(".")
        val hintedMime = formatHint?.let { mimeTypeForContainerHint(it) } ?: return baseSequence
        return (listOf(hintedMime) + baseSequence).distinct()
    }

    internal fun mimeTypeForContainerHint(hint: String): String? = when (hint) {
        "m3u8", "hls" -> MimeTypes.APPLICATION_M3U8
        "ts", "mpegts" -> MimeTypes.VIDEO_MP2T
        "mpd", "dash" -> MimeTypes.APPLICATION_MPD
        "mkv", "matroska" -> MimeTypes.VIDEO_MATROSKA
        "webm" -> MimeTypes.VIDEO_WEBM
        "mp4" -> MimeTypes.VIDEO_MP4
        else -> null
    }

    private fun buildMimeRetrySequenceForUri(uri: String): List<String?> {
        val cleanPath = uri.substringBefore('?').substringBefore('#').lowercase()
        val query = uri.substringAfter('?', "").lowercase()

        val isHlsLikely = cleanPath.endsWith(".m3u8") || query.contains("m3u8") || query.contains("type=apple")
        val isTsLikely = cleanPath.endsWith(".ts") || query.contains("output=ts") || query.contains("format=ts")
        val isDashLikely = cleanPath.endsWith(".mpd") || query.contains(".mpd")
        val isMkvLikely = cleanPath.endsWith(".mkv") || query.contains("format=mkv")
        val isWebmLikely = cleanPath.endsWith(".webm")

        val initialMime = when {
            isHlsLikely -> MimeTypes.APPLICATION_M3U8
            isTsLikely -> MimeTypes.VIDEO_MP2T
            isDashLikely -> MimeTypes.APPLICATION_MPD
            isMkvLikely -> MimeTypes.VIDEO_MATROSKA
            isWebmLikely -> MimeTypes.VIDEO_WEBM
            cleanPath.endsWith(".mp4") -> MimeTypes.VIDEO_MP4
            else -> null
        }

        val sequence = mutableListOf<String?>()

        when {
            isHlsLikely -> {
                sequence.add(MimeTypes.APPLICATION_M3U8)
                sequence.add(MimeTypes.VIDEO_MP4)
            }
            isTsLikely -> {
                sequence.add(MimeTypes.VIDEO_MP2T)
                sequence.add(MimeTypes.APPLICATION_M3U8)
            }
            isDashLikely -> {
                sequence.add(MimeTypes.APPLICATION_MPD)
                sequence.add(MimeTypes.VIDEO_MP4)
            }
            isMkvLikely -> {
                sequence.add(MimeTypes.VIDEO_MATROSKA)
                sequence.add(MimeTypes.VIDEO_MP4)
                sequence.add(MimeTypes.APPLICATION_M3U8)
            }
            isWebmLikely -> {
                sequence.add(MimeTypes.VIDEO_WEBM)
                sequence.add(MimeTypes.VIDEO_MP4)
            }
            cleanPath.endsWith(".mp4") -> {
                sequence.add(MimeTypes.APPLICATION_M3U8)
            }
            else -> {
                // Extensionless debrid/CDN/IPTV links often need explicit container hints. Prefer
                // streaming containers first (m3u8 → ts), then progressive mp4, and mkv last — this
                // matches how most IPTV/debrid endpoints serve extensionless URLs and avoids paying
                // the cost of a Matroska probe on what is usually an HLS/TS or MP4 stream.
                sequence.add(MimeTypes.APPLICATION_M3U8)
                sequence.add(MimeTypes.VIDEO_MP2T)
                sequence.add(MimeTypes.VIDEO_MP4)
                sequence.add(MimeTypes.VIDEO_MATROSKA)
            }
        }

        return sequence.distinct().filter { it != initialMime }
    }
}
