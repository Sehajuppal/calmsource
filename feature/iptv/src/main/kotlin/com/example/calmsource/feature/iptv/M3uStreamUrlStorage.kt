package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.network.UrlRedactor

/**
 * Keeps sensitive M3U channel stream URLs out of Room while preserving playback.
 *
 * Credential-bearing URLs are redacted for persistence and the full URL is stored in
 * [IptvSecureTokenStore] keyed by provider + channel id (never logged).
 */
internal object M3uStreamUrlStorage {
    private const val SECURE_USERNAME_PREFIX = "m3u_stream:"

    fun containsSensitiveMaterial(url: String): Boolean {
        if (url.isBlank()) return false
        return UrlRedactor.redactUrl(url) != url
    }

    fun sanitizeForPersistence(url: String): String = UrlRedactor.redactUrl(url)

    fun persistSecureUrl(providerId: String, channelId: String, rawUrl: String) {
        if (!containsSensitiveMaterial(rawUrl)) return
        XtreamRepository.tokenStore.savePassword(providerId, secureUsername(channelId), rawUrl)
    }

    fun clearSecureUrl(providerId: String, channelId: String) {
        XtreamRepository.tokenStore.deletePassword(providerId, secureUsername(channelId))
    }

    /**
     * Resolves the real playback URL for a channel.
     * Returns null if the stored URL contains REDACTED credentials and the secure store
     * has lost the original URL (e.g. after reinstall or data loss). Callers should
     * surface a user-facing "Re-enter your IPTV credentials" error in this case.
     */
    fun resolvePlaybackUrl(channel: IPTVChannel): String? {
        val stored = channel.streamUrl
        if (!stored.contains("REDACTED", ignoreCase = true)) {
            return stored
        }
        return XtreamRepository.tokenStore.readPassword(channel.providerId, secureUsername(channel.id))
    }

    private fun secureUsername(channelId: String): String = SECURE_USERNAME_PREFIX + channelId
}
