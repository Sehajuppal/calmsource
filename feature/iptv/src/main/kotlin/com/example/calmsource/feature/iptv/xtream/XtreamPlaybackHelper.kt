package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.IPTVChannel
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.feature.iptv.IptvSecureTokenStore

/**
 * Helper that resolves Xtream channel playback URLs at play time.
 *
 * The resolved URL contains credentials and must:
 * - NEVER be logged
 * - NEVER be persisted
 * - Be redacted via UrlRedactor before any display/logging
 */
object XtreamPlaybackHelper {

    /**
     * Resolve a playback URL for an Xtream live channel.
     * Returns null if the channel is not an Xtream channel, the provider
     * cannot be found, or credentials are not available.
     *
     * @param channel The IPTV channel to resolve
     * @param secureTokenStore The secure store for retrieving passwords
     * @param providers The list of known IPTV providers
     * @return The fully-resolved live playback URL, or null
     */
    fun resolveLivePlaybackUrl(
        channel: IPTVChannel,
        secureTokenStore: IptvSecureTokenStore,
        providers: List<IPTVProvider>
    ): String? {
        val streamId = XtreamStreamUrlBuilder.extractStreamId(channel.streamUrl) ?: return null
        val provider = providers.find { it.id == channel.providerId } ?: return null
        if (provider.type != IPTVProviderType.XTREAM) return null

        // Use serverUrl if available; fall back to extracting base URL from playlistUrl.
        // playlistUrl may contain query params with credentials (e.g. ?username=x&password=y),
        // so we must extract only the scheme+host+port portion.
        val serverUrl = provider.serverUrl.takeIf { it.isNotBlank() }
            ?: extractBaseUrl(provider.playlistUrl)
            ?: return null

        val username = provider.username ?: return null
        val password = secureTokenStore.readPassword(provider.id, username) ?: return null

        return XtreamStreamUrlBuilder.buildLiveUrl(serverUrl, username, password, streamId)
    }

    /**
     * Check if a channel is an Xtream channel (uses xtream:// pseudo-URL).
     */
    fun isXtreamChannel(channel: IPTVChannel): Boolean {
        return channel.streamUrl.startsWith("xtream://")
    }

    /**
     * Extract base URL (scheme + host + port) from a full URL.
     * Returns null if the URL cannot be parsed or is blank.
     *
     * Example: "http://example.com:8080/get.php?user=x" → "http://example.com:8080"
     */
    internal fun extractBaseUrl(url: String): String? {
        return XtreamServerUrlNormalizer.normalizePortalUrl(url)
    }
}

