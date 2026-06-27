package com.example.calmsource.core.model

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

sealed interface CalmSourceDeepLink {
    data class Details(
        val mediaItem: MediaItem,
        val startPositionMs: Long = 0L
    ) : CalmSourceDeepLink

    data class Channel(val channelId: String) : CalmSourceDeepLink
    data class Search(val query: String) : CalmSourceDeepLink

    companion object {
        fun parse(rawUri: String?): CalmSourceDeepLink? {
            if (rawUri.isNullOrBlank() || rawUri.length > MAX_URI_LENGTH) return null
            val uri = runCatching { URI(rawUri) }.getOrNull() ?: return null
            if (!uri.scheme.equals(SCHEME, ignoreCase = true)) return null
            val host = uri.host?.lowercase() ?: return null
            val pathValue = uri.path
                ?.trim('/')
                ?.takeIf { it.isNotBlank() }
                ?.let(::decode)
                ?: return null
            if (!isSafeRouteValue(pathValue)) return null
            val query = parseQuery(uri.rawQuery)

            return when (host) {
                "details" -> {
                    val title = query["title"]?.takeIf { it.isNotBlank() } ?: pathValue
                    val type = when (query["type"]?.lowercase()) {
                        "show", "series" -> MediaType.SHOW
                        else -> MediaType.MOVIE
                    }
                    Details(
                        mediaItem = MediaItem(
                            id = pathValue,
                            title = title.take(MAX_TITLE_LENGTH),
                            type = type
                        ),
                        startPositionMs = query["positionMs"]
                            ?.toLongOrNull()
                            ?.coerceIn(0L, MAX_POSITION_MS)
                            ?: 0L
                    )
                }
                "channel" -> Channel(pathValue)
                "search" -> Search(pathValue.take(MAX_SEARCH_LENGTH))
                else -> null
            }
        }

        fun detailsUri(
            reference: UserMemoryReference,
            positionMs: Long = 0L
        ): String {
            val sourceId = reference.sourceId ?: reference.itemKey
            val type = if (reference.contentType == UserMemoryContentType.SHOW) "show" else "movie"
            return "$SCHEME://details/${encode(sourceId)}" +
                "?title=${encode(reference.title.take(MAX_TITLE_LENGTH))}" +
                "&type=$type" +
                "&positionMs=${positionMs.coerceIn(0L, MAX_POSITION_MS)}"
        }

        fun channelUri(channelId: String): String = "$SCHEME://channel/${encode(channelId)}"

        private fun parseQuery(rawQuery: String?): Map<String, String> {
            if (rawQuery.isNullOrBlank()) return emptyMap()
            return rawQuery.split('&')
                .asSequence()
                .take(12)
                .mapNotNull { pair ->
                    val separator = pair.indexOf('=')
                    if (separator <= 0) return@mapNotNull null
                    decode(pair.substring(0, separator)) to decode(pair.substring(separator + 1))
                }
                .toMap()
        }

        private fun isSafeRouteValue(value: String): Boolean {
            return value.length in 1..160 &&
                !value.contains('/') &&
                !value.contains('\\') &&
                !value.contains("://") &&
                !value.contains('\u0000')
        }

        private fun encode(value: String): String {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20")
        }

        private fun decode(value: String): String {
            return runCatching {
                URLDecoder.decode(value, StandardCharsets.UTF_8.name())
            }.getOrDefault(value)
        }

        private const val SCHEME = "calmsource"
        private const val MAX_URI_LENGTH = 2_048
        private const val MAX_TITLE_LENGTH = 160
        private const val MAX_SEARCH_LENGTH = 120
        private const val MAX_POSITION_MS = 30L * 24L * 60L * 60L * 1000L
    }
}
