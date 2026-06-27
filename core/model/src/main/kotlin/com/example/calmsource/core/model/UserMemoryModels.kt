package com.example.calmsource.core.model

import java.util.Locale

enum class UserMemoryContentType {
    MOVIE,
    SHOW,
    EPISODE,
    VOD,
    LIVE_CHANNEL
}

data class UserMemoryReference(
    val itemKey: String,
    val contentType: UserMemoryContentType,
    val title: String,
    val subtitle: String? = null,
    val providerId: String? = null,
    val sourceId: String? = null
)

fun MediaItem.toUserMemoryReference(): UserMemoryReference {
    val safeSourceId = runCatching {
        UserMemoryPrivacy.requireSafeIdentifier(id, "mediaId")
    }.getOrNull()
    return UserMemoryReference(
        itemKey = "media-${generateSafeSourceId(id)}",
        contentType = if (type == MediaType.SHOW) {
            UserMemoryContentType.SHOW
        } else {
            UserMemoryContentType.MOVIE
        },
        title = title,
        sourceId = safeSourceId
    )
}

fun IPTVChannel.toUserMemoryReference(): UserMemoryReference {
    val identity = "$providerId:${tvgId ?: id}"
    return UserMemoryReference(
        itemKey = "channel-${generateSafeSourceId(identity)}",
        contentType = if (isVod) {
            UserMemoryContentType.VOD
        } else {
            UserMemoryContentType.LIVE_CHANNEL
        },
        title = name,
        subtitle = groupTitle,
        providerId = runCatching {
            UserMemoryPrivacy.requireSafeIdentifier(providerId, "providerId")
        }.getOrNull(),
        sourceId = runCatching {
            UserMemoryPrivacy.requireSafeIdentifier(id, "channelId")
        }.getOrNull()
    )
}

fun UserMemoryReference.toMediaItem(): MediaItem {
    return MediaItem(
        id = sourceId ?: itemKey,
        title = title,
        type = if (contentType == UserMemoryContentType.SHOW) {
            MediaType.SHOW
        } else {
            MediaType.MOVIE
        }
    )
}

data class ContinueWatchingItem(
    val reference: UserMemoryReference,
    val progressMs: Long,
    val durationMs: Long,
    val updatedAt: Long
)

data class FavoriteItem(
    val reference: UserMemoryReference,
    val createdAt: Long,
    val updatedAt: Long
)

data class WatchHistoryItem(
    val reference: UserMemoryReference,
    val firstWatchedAt: Long,
    val lastWatchedAt: Long,
    val watchCount: Long,
    val progressMs: Long,
    val durationMs: Long
)

data class RecentChannelItem(
    val reference: UserMemoryReference,
    val lastWatchedAt: Long,
    val watchCount: Long
)

data class SearchHistoryItem(
    val query: String,
    val lastSearchedAt: Long,
    val searchCount: Long
)

enum class UserPreferenceSignalType {
    CONTENT_TYPE,
    PROVIDER,
    SOURCE,
    GENRE,
    SEARCH_RESULT_SELECTION
}

data class UserPreferenceSignal(
    val signalType: UserPreferenceSignalType,
    val signalKey: String,
    val count: Long,
    val lastSignaledAt: Long
)

/**
 * Central validation for values permitted in user-memory persistence.
 *
 * User-memory records may contain display metadata and opaque IDs, but never a
 * URL, credential, authorization value, manifest address, or resolved link.
 */
object UserMemoryPrivacy {
    private const val MAX_IDENTIFIER_LENGTH = 256
    private const val MAX_DISPLAY_TEXT_LENGTH = 300
    private const val MAX_SEARCH_QUERY_LENGTH = 120

    private val controlCharacters = Regex("[\\p{Cc}\\p{Cf}]")
    private val whitespace = Regex("\\s+")
    private val urlScheme = Regex("(?i)\\b[a-z][a-z0-9+.-]{1,20}://")
    private val encodedUrlScheme = Regex("(?i)%3a(?:%2f){2}")
    private val domainValue = Regex("(?i)\\b(?:[a-z0-9-]+\\.)+[a-z]{2,}(?:[/?:#]|$)")
    private val identifierSeparator = Regex("[/\\\\?#@]")
    private val secretAssignment = Regex(
        "(?i)\\b(?:access[_-]?token|refresh[_-]?token|api[_-]?key|apikey|auth|" +
            "authorization|password|passwd|secret|client[_-]?secret|username)\\s*[:=]"
    )
    private val bearerValue = Regex("(?i)\\bbearer\\s+[a-z0-9._~+/=-]+")
    private val jwtValue = Regex("\\b[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\b")
    private val emailValue = Regex("(?i)\\b[^\\s@]+@[^\\s@]+\\.[a-z]{2,}\\b")
    private val ipv4Value = Regex("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")
    private val opaqueSecretValue = Regex(
        "\\b(?=[A-Za-z0-9_-]{24,}\\b)(?=[A-Za-z0-9_-]*[A-Za-z])" +
            "(?=[A-Za-z0-9_-]*\\d)[A-Za-z0-9_-]+\\b"
    )

    fun sanitizeReference(reference: UserMemoryReference): UserMemoryReference {
        return reference.copy(
            itemKey = requireSafeIdentifier(reference.itemKey, "itemKey"),
            title = requireSafeDisplayText(reference.title, "title"),
            subtitle = reference.subtitle?.let { requireSafeDisplayText(it, "subtitle") },
            providerId = reference.providerId?.let { requireSafeIdentifier(it, "providerId") },
            sourceId = reference.sourceId?.let { requireSafeIdentifier(it, "sourceId") }
        )
    }

    fun requireSafeIdentifier(value: String, fieldName: String = "identifier"): String {
        val cleaned = clean(value, MAX_IDENTIFIER_LENGTH)
        require(cleaned.isNotEmpty()) { "$fieldName must not be blank" }
        require(!looksSensitiveIdentifier(cleaned)) {
            "$fieldName contains private or URL-like data"
        }
        return cleaned
    }

    fun requireSafeDisplayText(value: String, fieldName: String = "text"): String {
        val cleaned = clean(value, MAX_DISPLAY_TEXT_LENGTH)
        require(cleaned.isNotEmpty()) { "$fieldName must not be blank" }
        require(!looksSensitive(cleaned)) { "$fieldName contains private or URL-like data" }
        return cleaned
    }

    fun sanitizeSearchQuery(query: String): String? {
        val cleaned = clean(query, MAX_SEARCH_QUERY_LENGTH)
        if (cleaned.isEmpty() || looksSensitive(cleaned)) return null
        return cleaned
    }

    fun normalizeSearchQuery(query: String): String {
        return query.lowercase(Locale.ROOT)
    }

    fun looksSensitive(value: String): Boolean {
        return urlScheme.containsMatchIn(value) ||
            encodedUrlScheme.containsMatchIn(value) ||
            domainValue.containsMatchIn(value) ||
            secretAssignment.containsMatchIn(value) ||
            bearerValue.containsMatchIn(value) ||
            jwtValue.containsMatchIn(value) ||
            emailValue.containsMatchIn(value) ||
            ipv4Value.containsMatchIn(value) ||
            opaqueSecretValue.containsMatchIn(value)
    }

    private fun looksSensitiveIdentifier(value: String): Boolean {
        return value.any(Char::isWhitespace) ||
            identifierSeparator.containsMatchIn(value) ||
            urlScheme.containsMatchIn(value) ||
            encodedUrlScheme.containsMatchIn(value) ||
            secretAssignment.containsMatchIn(value) ||
            bearerValue.containsMatchIn(value) ||
            jwtValue.containsMatchIn(value) ||
            emailValue.containsMatchIn(value)
    }

    private fun clean(value: String, maxLength: Int): String {
        return value
            .replace(controlCharacters, " ")
            .replace(whitespace, " ")
            .trim()
            .take(maxLength)
    }
}
