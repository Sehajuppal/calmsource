package com.example.calmsource.core.playback

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.PlaybackRequest
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSourceType

/**
 * Resolves a [PlaybackRequest] by translating pseudo-URLs (xtream://, magnet:)
 * via the provided resolver callbacks, then returns the resolved request.
 *
 * Returns null if magnet resolution is required but impossible (e.g. no debrid account),
 * which should be surfaced as a [com.example.calmsource.core.model.PlaybackError.PermissionRequired].
 */
suspend fun resolvePlaybackRequest(
    request: PlaybackRequest,
    resolveXtream: suspend (PlaybackSource) -> String?,
    resolveMagnet: suspend (PlaybackSource) -> String?,
    resolveIptv: suspend (PlaybackSource) -> String? = { null }
): PlaybackRequest? {
    val newSource = resolveSourceUrl(request.source, resolveXtream, resolveMagnet, resolveIptv) ?: return null
    return if (newSource.rawUrl != request.source.rawUrl) {
        request.copy(source = newSource)
    } else {
        request
    }
}

suspend fun resolveSourceUrl(
    source: PlaybackSource,
    resolveXtream: suspend (PlaybackSource) -> String?,
    resolveMagnet: suspend (PlaybackSource) -> String?,
    resolveIptv: suspend (PlaybackSource) -> String? = { null }
): PlaybackSource? {
    var resolved = source

    if (resolved.rawUrl.startsWith("xtream://", ignoreCase = true)) {
        val url = resolveXtream(resolved)
        if (url != null && !url.startsWith("xtream://", ignoreCase = true)) {
            resolved = resolved.copy(rawUrl = url)
        } else {
            return null
        }
    }

    if (resolved.type == PlaybackSourceType.IPTV && containsRedactedMaterial(resolved.rawUrl)) {
        val url = resolveIptv(resolved)
        if (url != null &&
            !containsRedactedMaterial(url) &&
            !url.startsWith("xtream://", ignoreCase = true)
        ) {
            resolved = resolved.copy(rawUrl = url)
        } else {
            return null
        }
    }

    if (resolved.rawUrl.startsWith("magnet:", ignoreCase = true)) {
        val url = resolveMagnet(resolved)
        if (url != null) {
            resolved = resolved.copy(rawUrl = url)
        } else {
            return null
        }
    }

    return resolved
}

fun isResolvedPlaybackUrlInvalid(request: PlaybackRequest?): Boolean {
    if (request == null) return true
    val url = request.source.rawUrl.trim()
    return url.isEmpty() ||
        url.startsWith("xtream://", ignoreCase = true) ||
        containsRedactedMaterial(url)
}

private fun containsRedactedMaterial(url: String): Boolean =
    url.contains("REDACTED", ignoreCase = true)

/**
 * After a stream-level fallback, track the active source identity in UI state but keep the
 * original pseudo URL and headers so credentials are not held in the Compose layer (#14).
 */
fun mergeFallbackIdentityPreservingPseudoUrl(
    activeRequest: PlaybackRequest,
    resolvedFallbackSource: PlaybackSource,
): PlaybackRequest {
    if (resolvedFallbackSource.id == activeRequest.source.id) return activeRequest
    return activeRequest.copy(
        source = resolvedFallbackSource.copy(
            rawUrl = activeRequest.source.rawUrl,
            headers = activeRequest.source.headers
        )
    )
}

suspend fun resolvePlaybackFallbacks(
    fallbackCandidates: List<PlaybackSource>,
    skipIds: Set<String>,
    resolveXtream: suspend (PlaybackSource) -> String?,
    resolveMagnet: suspend (PlaybackSource) -> String?,
    resolveIptv: suspend (PlaybackSource) -> String? = { null },
): List<PlaybackSource> {
    return fallbackCandidates.mapNotNull { source ->
        if (source.id in skipIds) return@mapNotNull null
        val resolved = resolveSourceUrl(source, resolveXtream, resolveMagnet, resolveIptv)
        if (resolved != null &&
            resolved.rawUrl.trim().isNotEmpty() &&
            !resolved.rawUrl.startsWith("xtream://", ignoreCase = true) &&
            !containsRedactedMaterial(resolved.rawUrl)
        ) {
            resolved
        } else {
            null
        }
    }
}

fun selectAutoLiveFallbackCandidates(
    explicitFallbacks: List<PlaybackSource>,
    currentSourceId: String,
    findChannel: (String) -> IPTVChannel?,
    buildLiveFallbackSources: (IPTVChannel) -> List<PlaybackSource>,
): List<PlaybackSource> {
    if (explicitFallbacks.isNotEmpty()) return explicitFallbacks
    val baseChannelId = currentSourceId.substringBefore("-alt-")
    val liveChannel = findChannel(baseChannelId) ?: return emptyList()
    if (liveChannel.isVod) return emptyList()
    return buildLiveFallbackSources(liveChannel)
}
