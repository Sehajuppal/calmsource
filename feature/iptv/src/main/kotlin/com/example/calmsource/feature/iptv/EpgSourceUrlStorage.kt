package com.example.calmsource.feature.iptv

import com.example.calmsource.core.model.EPGSource
import com.example.calmsource.core.network.UrlRedactor

/** Keeps credential-bearing XMLTV source URLs out of Room. */
internal object EpgSourceUrlStorage {
    private const val SECURE_KEY_PREFIX = "epg_source_url:"

    fun persist(providerId: String, sourceId: String, rawUrl: String) {
        if (UrlRedactor.redactUrl(rawUrl) != rawUrl) {
            XtreamRepository.tokenStore.savePassword(providerId, secureKey(sourceId), rawUrl)
        }
    }

    fun sanitizeForPersistence(rawUrl: String): String = UrlRedactor.redactUrl(rawUrl)

    fun resolve(source: EPGSource): String? {
        if (!source.url.contains("REDACTED", ignoreCase = true)) return source.url
        return XtreamRepository.tokenStore.readPassword(
            source.providerId,
            secureKey(source.id)
        )
    }

    fun clear(providerId: String, sourceId: String) {
        XtreamRepository.tokenStore.deletePassword(providerId, secureKey(sourceId))
    }

    private fun secureKey(sourceId: String): String = SECURE_KEY_PREFIX + sourceId
}
