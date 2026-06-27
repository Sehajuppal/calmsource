package com.example.calmsource.feature.search

import com.example.calmsource.core.model.UserMemoryPrivacy

data class SearchMemorySignals(
    val recentQueries: List<String> = emptyList(),
    val favoriteMediaIds: Set<String> = emptySet(),
    val historyMediaIds: Set<String> = emptySet()
)

fun interface SearchSignalSink {
    suspend fun recordCompletedQuery(query: String)
}

fun interface SearchMemorySnapshot {
    suspend fun load(): SearchMemorySignals
}

object NoOpSearchSignalSink : SearchSignalSink {
    override suspend fun recordCompletedQuery(query: String) = Unit
}

object EmptySearchMemorySnapshot : SearchMemorySnapshot {
    override suspend fun load(): SearchMemorySignals = SearchMemorySignals()
}

private val unsafeSearchScheme = Regex(
    "(?i)\\b(?:https?|ftp|file|content|rtsp|rtmp|udp|stremio|magnet|acestream|xtream|javascript|data|vbscript):"
)
private val manifestAddress = Regex("(?i)(?:^|[/\\\\])manifest(?:\\.json)?(?:$|[?#])|\\bmanifest\\.json\\b")

internal fun sanitizeCompletedSearchQuery(query: String): String? {
    val sanitized = UserMemoryPrivacy.sanitizeSearchQuery(query) ?: return null
    if (unsafeSearchScheme.containsMatchIn(sanitized)) return null
    if (manifestAddress.containsMatchIn(sanitized)) return null
    return sanitized
}

internal fun SearchMemorySignals.sanitized(): SearchMemorySignals {
    return copy(
        recentQueries = recentQueries.mapNotNull(::sanitizeCompletedSearchQuery).distinct(),
        favoriteMediaIds = favoriteMediaIds.filterTo(linkedSetOf(), ::isSafeMemoryId),
        historyMediaIds = historyMediaIds.filterTo(linkedSetOf(), ::isSafeMemoryId)
    )
}

private fun isSafeMemoryId(value: String): Boolean {
    return runCatching {
        UserMemoryPrivacy.requireSafeIdentifier(value)
    }.isSuccess
}
