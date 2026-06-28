package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.feature.iptv.xtream.XtreamStreamUrlBuilder

/**
 * Shared Xtream pseudo-URL resolution for mobile and TV player screens.
 */
object IptvXtreamPlaybackResolver {

    /**
     * Resolves an xtream:// pseudo URL to a real playback URL.
     *
     * @param surfaceError when true (primary source), structured resolution errors are pushed to
     *   [IPTVRepository.playbackResolutionError] so the player UI can show an actionable message
     *   ("credentials missing", "server URL invalid", …). Fallback/alt sources pass false so a
     *   failing alternate does not overwrite the primary source's error.
     */
    suspend fun resolveSourceUrl(source: PlaybackSource, surfaceError: Boolean = true): String? {
        val baseChannelId = source.id.substringBefore("-alt-")
        val existingChannel = IPTVRepository.findChannelForPlayback(baseChannelId)
        if (existingChannel == null && IPTVRepository.findPlaybackChannel(baseChannelId) != null) {
            if (surfaceError) {
                IPTVRepository.reportPlaybackResolutionError("This channel is unavailable.")
            }
            return null
        }
        val providerId = resolveProviderId(source, existingChannel)
        if (providerId == null) {
            if (surfaceError) {
                IPTVRepository.reportPlaybackResolutionError(
                    "Could not determine the IPTV provider for this channel — try re-syncing the provider."
                )
            }
            return null
        }
        val isLive = source.metadata?.isLive == true
        val channel = existingChannel ?: IPTVChannel(
            id = baseChannelId,
            tvgId = null,
            tvgName = null,
            tvgLogo = null,
            name = source.title,
            streamUrl = source.rawUrl,
            providerId = providerId,
            groupTitle = if (isLive) "Live" else "VOD",
            // Carry the Xtream content type + container so resolvePlaybackUrlOrError builds the right
            // endpoint. Without these a synthetic VOD/series channel defaults to the live `.ts`
            // endpoint and the movie/episode never plays (#12).
            rawAttributes = buildSyntheticXtreamAttributes(source, isLive)
        )
        val liveFormat = source.metadata?.containerFormat?.takeIf { source.metadata?.isLive == true }
        val result = IPTVRepository.resolvePlaybackUrlOrError(channel, liveOutputFormat = liveFormat)
        if (result.error != null || result.url.startsWith("xtream://", ignoreCase = true)) {
            if (surfaceError) {
                IPTVRepository.reportPlaybackResolutionError(
                    result.error ?: "Could not resolve this channel for playback."
                )
            }
            return null
        }
        return result.url
    }

    /**
     * Derives the `xtream_content_type` / `container_extension` attributes for a synthetic channel
     * from the [PlaybackSource] metadata + id so [IPTVRepository.resolvePlaybackUrlOrError] picks the
     * correct Xtream endpoint (live `/live/.../.ts`, VOD `/movie/...`, or series `/series/...`).
     */
    internal fun buildSyntheticXtreamAttributes(
        source: PlaybackSource,
        isLive: Boolean
    ): Map<String, String> {
        val contentType = when {
            isLive -> "live"
            source.id.startsWith(PlaybackSource.XTREAM_SERIES_EPISODE_SOURCE_PREFIX) ||
                source.id.contains("_series_") -> "series"
            else -> "vod"
        }
        val containerExtension = source.metadata?.containerFormat
            ?.trim()
            ?.lowercase()
            ?.removePrefix(".")
            ?.takeIf { it.isNotBlank() }
        return buildMap {
            put("xtream_content_type", contentType)
            if (containerExtension != null) put("container_extension", containerExtension)
        }
    }

    internal fun resolveProviderId(source: PlaybackSource, existingChannel: IPTVChannel?): String? {
        XtreamStreamUrlBuilder.extractProviderId(source.rawUrl)?.let { return it }
        val sourceId = source.id
        val extractedFromId = when {
            sourceId.startsWith("xtream-series-episode|") -> {
                sourceId.removePrefix("xtream-series-episode|").substringBefore("|")
            }
            sourceId.contains("_live_") -> sourceId.substringBefore("_live_")
            sourceId.contains("_vod_") -> sourceId.substringBefore("_vod_")
            sourceId.contains("_series_") -> sourceId.substringBefore("_series_")
            else -> null
        }
        return extractedFromId?.takeIf { it.isNotBlank() }
            ?: existingChannel?.providerId?.takeIf { it.isNotBlank() }
    }
}
