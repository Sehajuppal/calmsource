package com.example.calmsource.core.sourceintelligence.parsers

import com.example.calmsource.core.model.PlaybackSourceType
import com.example.calmsource.core.sourceintelligence.models.ParsedSource
import java.net.URI
import java.util.UUID
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Normalizes metadata from distinct API providers, scrapers, and extensions.
 * Transforms varying structures into a uniform ParsedSource model.
 */
interface SourceParser {
    /**
     * Parses a raw metadata string into a safe, uniform model.
     * Must drop unknown fields and sanitize inputs to prevent arbitrary injection.
     */
    fun parse(rawPayload: String, origin: String): List<ParsedSource>
}

/**
 * A conservative fallback parser for legacy pipe-delimited source payloads.
 *
 * Structured provider payloads such as Stremio JSON are intentionally rejected here
 * rather than guessed at. Provider-specific JSON parsing should live in a dedicated
 * parser that understands that schema.
 */
class DefaultSourceParser(
    private val allowedOrigins: Set<String> = DEFAULT_ALLOWED_ORIGINS
) : SourceParser {

    companion object {
        val DEFAULT_ALLOWED_ORIGINS = setOf(
            "local_iptv",
            "official_stremio_addon",
            "verified_debrid"
        )
        private val SECRETS_REDACT_REGEX = Regex("(?i)(apikey|token|key|secret)=[^&\\s]+")

        /** URL schemes that are allowed for source playback. */
        private val SUPPORTED_URL_SCHEMES = setOf(
            "http",
            "https",
            "magnet",
            "xtream",
            "acestream",
            "sop"
        )

        /** URL schemes that are explicitly blocked for security. These are checked
         *  before [SUPPORTED_URL_SCHEMES] so the allowlist serves as a second line
         *  of defense against future refactoring errors. */
        private val BLOCKED_URL_SCHEMES = setOf(
            "javascript",
            "data",
            "file",
            "blob"
        )

        private val json = Json {
            ignoreUnknownKeys = true
            isLenient = true
        }
    }

    override fun parse(rawPayload: String, origin: String): List<ParsedSource> {
        if (!isOriginLegal(origin)) return emptyList()

        val trimmedPayload = rawPayload.trimStart()
        if (trimmedPayload.startsWith("{") || trimmedPayload.startsWith("[")) {
            return parseStremioJson(trimmedPayload, origin)
        }

        return rawPayload
            .lineSequence()
            .mapNotNull { line ->
                if (line.isBlank()) return@mapNotNull null
                val parts = line.split("|", limit = 3)
                if (parts.size < 2) return@mapNotNull null
                val title = parts[0].trim()
                val url = parts[1].trim()
                if (title.isBlank() || !isSupportedUrl(url)) return@mapNotNull null

                ParsedSource(
                    id = UUID.randomUUID().toString(),
                    type = PlaybackSourceType.EXTENSION,
                    title = sanitizeString(title),
                    origin = origin,
                    rawUrl = url,
                    rawFilename = null
                )
            }
            .toList()
    }

    private fun sanitizeString(input: String): String {
        return input.replace(SECRETS_REDACT_REGEX, "[REDACTED]")
    }

    private fun isOriginLegal(origin: String): Boolean {
        return origin in allowedOrigins
    }

    private fun parseStremioJson(rawPayload: String, origin: String): List<ParsedSource> {
        val root = runCatching { json.parseToJsonElement(rawPayload) }.getOrNull() ?: return emptyList()
        val streams = when (root) {
            is JsonObject -> root["streams"]?.jsonArrayOrNull()
            is JsonArray -> root
            else -> null
        } ?: return emptyList()

        return streams.mapNotNull { element ->
            val stream = element.jsonObjectOrNull() ?: return@mapNotNull null
            val title = stream.stringField("title")
                ?: stream.stringField("name")
                ?: stream.objectField("behaviorHints")?.stringField("filename")
                ?: return@mapNotNull null
            val rawUrl = stream.stringField("url")
                ?: stream.stringField("externalUrl")
                ?: stream.stringField("infoHash")?.let { "magnet:?xt=urn:btih:$it" }
                ?: return@mapNotNull null
            if (!isSupportedUrl(rawUrl)) return@mapNotNull null

            ParsedSource(
                id = UUID.randomUUID().toString(),
                type = PlaybackSourceType.EXTENSION,
                title = sanitizeString(title),
                quality = stream.stringField("quality")
                    ?: stream.stringField("description")
                    ?: stream.stringField("name"),
                sizeBytes = stream.longField("size")
                    ?: stream.objectField("behaviorHints")?.longField("videoSize"),
                seeders = stream.intField("seeders"),
                origin = origin,
                rawUrl = rawUrl,
                rawFilename = null
            )
        }
    }

    private fun JsonElement.jsonObjectOrNull(): JsonObject? = this as? JsonObject

    private fun JsonElement.jsonArrayOrNull(): JsonArray? = this as? JsonArray

    private fun JsonObject.objectField(name: String): JsonObject? = this[name]?.jsonObjectOrNull()

    private fun JsonObject.stringField(name: String): String? {
        return this[name]
            ?.jsonPrimitiveOrNull()
            ?.contentOrNull
            ?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.intField(name: String): Int? {
        return this[name]?.jsonPrimitiveOrNull()?.intOrNull
    }

    private fun JsonObject.longField(name: String): Long? {
        return this[name]
            ?.jsonPrimitiveOrNull()
            ?.contentOrNull
            ?.toLongOrNull()
    }

    private fun JsonElement.jsonPrimitiveOrNull() = runCatching { jsonPrimitive }.getOrNull()

    private fun isSupportedUrl(url: String): Boolean {
        if (url.isBlank() || url.any { it.isISOControl() || it.isWhitespace() }) return false
        val scheme = runCatching { URI(url).scheme?.lowercase() }.getOrNull() ?: return false

        // Explicitly block dangerous URI schemes regardless of the allowlist,
        // so a future edit to SUPPORTED_URL_SCHEMES cannot accidentally permit them.
        if (scheme in BLOCKED_URL_SCHEMES) return false

        if (scheme !in SUPPORTED_URL_SCHEMES) return false
        if (scheme in setOf("http", "https")) {
            return runCatching { !URI(url).host.isNullOrBlank() }.getOrDefault(false)
        }
        return true
    }
}
