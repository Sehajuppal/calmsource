package com.example.calmsource.core.playback

import com.example.calmsource.core.model.PlaybackSource

/**
 * Shared URL/credential sanitization utilities for the playback module.
 *
 * Centralises the regex patterns used by [PlaybackManager], [ExoPlayerBackend],
 * and [StreamRaceManager] so they are compiled once and reused.
 */
internal object PlaybackSanitizer {
    val URL_REGEX = """(?:https?|xtream)://[^\s"'<>]+""".toRegex()
    val XTREAM_CREDENTIAL_PATH_REGEX = """/(live|movie|series)/[^/]+/[^\/]+/""".toRegex()
    val URI_SCHEME_REGEX = "^([a-zA-Z][a-zA-Z0-9+.-]*):".toRegex()

    fun sanitize(message: String?): String {
        if (message == null) return ""
        return message
            .replace(URL_REGEX) { matchResult ->
                PlaybackSource.redactUrl(matchResult.value)
            }
            .replace(XTREAM_CREDENTIAL_PATH_REGEX) { "/${it.groupValues[1]}/[redacted]/[redacted]/" }
    }

    fun sanitizeCause(throwable: Throwable): Throwable {
        val message = throwable.message ?: return throwable
        val sanitized = sanitize(message)
        return if (sanitized != message) Exception(sanitized, throwable.cause) else throwable
    }
}
