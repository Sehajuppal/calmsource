package com.example.calmsource.core.network

import com.example.calmsource.core.model.*
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.HttpResponse
import io.ktor.http.isSuccess
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.use
import io.ktor.utils.io.readRemaining
import io.ktor.utils.io.core.readBytes

/** Helper object to securely store and retrieve secrets for configurable extensions. */
object ExtensionSecrets {
    var readDelegate: ((providerId: String, key: String) -> String?)? = null
    var saveDelegate: ((providerId: String, key: String, value: String) -> Unit)? = null
    var deleteDelegate: ((providerId: String, key: String) -> Unit)? = null
    var clearDelegate: ((providerId: String) -> Unit)? = null

    fun saveSecret(providerId: String, key: String, value: String) {
        saveDelegate?.invoke(providerId, key, value)
    }

    fun readSecret(providerId: String, key: String): String? {
        return readDelegate?.invoke(providerId, key)
    }

    fun deleteSecret(providerId: String, key: String) {
        deleteDelegate?.invoke(providerId, key)
    }

    fun clearSecrets(providerId: String) {
        clearDelegate?.invoke(providerId)
    }
}

/** Stremio result envelope. */
sealed interface StremioResult<out T> {
    data class Success<T>(val data: T) : StremioResult<T>
    data class Failure(val error: ExtensionError) : StremioResult<Nothing>
}

/**
 * Client for fetching and parsing Stremio protocol endpoints (manifest, catalog, meta, stream, subtitles).
 */
object StremioAddonClient {
    private const val MAX_RESPONSE_BYTES = 5 * 1024 * 1024L

    private val jsonParser = kotlinx.serialization.json.Json {
        ignoreUnknownKeys = true
        isLenient = true
        encodeDefaults = true
        coerceInputValues = true
    }

    /** Delegate to record signals (failures, timeouts) back to database/repository layer. */
    var recordSignalDelegate: (suspend (providerId: String, url: String, isTimeout: Boolean, errorMsg: String) -> Unit)? = null

    @PublishedApi
    internal suspend fun safeRecordSignal(providerId: String, url: String, isTimeout: Boolean, errorMsg: String) {
        val delegate = recordSignalDelegate
        if (delegate != null) {
            try {
                delegate.invoke(providerId, UrlRedactor.redactUrl(url), isTimeout, errorMsg)
            } catch (e: Exception) {
                // Silently drop exception
            }
        }
    }

    /** Encodes a single path segment for safe use in a URL, preventing path traversal or malformed URLs. */
    private fun encodePathSegment(segment: String): String {
        return java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
    }

    /** Validates and normalizes a base URL, returning a failure if it's empty or lacks a valid scheme. */
    private fun validateBaseUrl(baseUrl: String): String? {
        val trimmed = baseUrl.trimEnd('/')
        if (trimmed.isBlank()) return null
        val scheme = try { java.net.URI(trimmed).scheme?.lowercase() } catch (e: Exception) { null }
        if (scheme != "http" && scheme != "https") return null
        return trimmed
    }

    /** Interpolates secret placeholders (e.g. `{secret_api_key}`) using values from [SecureTokenStore]. */
    fun resolveUrl(url: String, providerId: String): String {
        var resolved = url
        val regex = Regex("\\{secret_([^}]+)\\}")
        val matches = regex.findAll(url)
        for (match in matches) {
            val key = match.groupValues[1]
            val secretVal = try {
                ExtensionSecrets.readSecret(providerId, key)
            } catch (e: Exception) {
                null
            }
            resolved = resolved.replace("{secret_$key}", secretVal.orEmpty())
        }
        return resolved
    }

    /** Extracts configuration parameters from a configured Stremio manifest URL path. */
    fun parseConfigFromUrl(url: String): Map<String, String> {
        return try {
            val doubleSlashIndex = url.indexOf("://")
            val pathStart = if (doubleSlashIndex != -1) {
                url.indexOf("/", doubleSlashIndex + 3)
            } else {
                0
            }
            val rawPath = if (pathStart != -1) url.substring(pathStart) else ""
            val path = rawPath.substringBefore("?").substringBefore("#")
            if (!path.endsWith("/manifest.json")) return emptyMap()
            val segments = path.removeSuffix("/manifest.json").split("/").filter { it.isNotEmpty() }
            if (segments.isEmpty()) return emptyMap()
            val configSegment = segments.last()
            val map = mutableMapOf<String, String>()
            // Stremio addons use two config-segment styles: pipe-delimited (Torrentio, e.g.
            // `realdebrid=KEY|qualityfilter=hdr`) and ampersand-delimited. Split on both so
            // configured Torrentio URLs parse into individual key/value pairs instead of one
            // mangled value (bug #3).
            configSegment.split("|", "&").forEach { pair ->
                if (pair.isEmpty()) return@forEach
                val parts = pair.split("=", limit = 2)
                if (parts.size == 2) {
                    val key = java.net.URLDecoder.decode(parts[0], "UTF-8")
                    val value = java.net.URLDecoder.decode(parts[1], "UTF-8")
                    map[key] = value
                }
            }
            map
        } catch (e: Exception) {
            emptyMap()
        }
    }

    suspend fun getManifest(url: String, providerId: String, timeoutMs: Long = 25000L): StremioResult<StremioManifest> = withContext(Dispatchers.IO) {
        val resolved = resolveUrl(url, providerId)
        safeGet<StremioManifest>(resolved, providerId, timeoutMs)
    }

    suspend fun getCatalog(resolvedBaseUrl: String, type: String, catalogId: String, extraArgs: String? = null, providerId: String = "", timeoutMs: Long = 25000L): StremioResult<StremioCatalogResponse> = withContext(Dispatchers.IO) {
        val base = validateBaseUrl(resolvedBaseUrl)
            ?: return@withContext StremioResult.Failure(ExtensionError.NetworkError("Invalid or empty addon base URL"))
        val encodedType = encodePathSegment(type)
        val encodedCatalogId = encodePathSegment(catalogId)
        val path = if (extraArgs.isNullOrBlank()) {
            "catalog/$encodedType/$encodedCatalogId.json"
        } else {
            // extraArgs (e.g. "search=query") is already externally encoded; keep as-is for Stremio spec
            "catalog/$encodedType/$encodedCatalogId/$extraArgs.json"
        }
        val url = "$base/$path"
        safeGet<StremioCatalogResponse>(url, providerId, timeoutMs)
    }

    suspend fun getMeta(resolvedBaseUrl: String, type: String, id: String, providerId: String = "", timeoutMs: Long = 25000L): StremioResult<StremioMetaResponse> = withContext(Dispatchers.IO) {
        val base = validateBaseUrl(resolvedBaseUrl)
            ?: return@withContext StremioResult.Failure(ExtensionError.NetworkError("Invalid or empty addon base URL"))
        val url = "$base/meta/${encodePathSegment(type)}/${encodePathSegment(id)}.json"
        safeGet<StremioMetaResponse>(url, providerId, timeoutMs)
    }

    suspend fun getStreams(resolvedBaseUrl: String, type: String, id: String, providerId: String = "", timeoutMs: Long = 25000L): StremioResult<StremioStreamResponse> = withContext(Dispatchers.IO) {
        val base = validateBaseUrl(resolvedBaseUrl)
            ?: return@withContext StremioResult.Failure(ExtensionError.NetworkError("Invalid or empty addon base URL"))
        val url = "$base/stream/${encodePathSegment(type)}/${encodePathSegment(id)}.json"
        safeGet<StremioStreamResponse>(url, providerId, timeoutMs)
    }

    suspend fun getSubtitles(resolvedBaseUrl: String, type: String, id: String, providerId: String = "", timeoutMs: Long = 25000L): StremioResult<StremioSubtitleResponse> = withContext(Dispatchers.IO) {
        val base = validateBaseUrl(resolvedBaseUrl)
            ?: return@withContext StremioResult.Failure(ExtensionError.NetworkError("Invalid or empty addon base URL"))
        val url = "$base/subtitles/${encodePathSegment(type)}/${encodePathSegment(id)}.json"
        safeGet<StremioSubtitleResponse>(url, providerId, timeoutMs)
    }

    private suspend inline fun <reified T> safeGet(url: String, providerId: String = "", timeoutMs: Long = 25000L, recordSignal: Boolean = true, maxAttempts: Int = 3): StremioResult<T> {
        if (url.isBlank()) {
            return StremioResult.Failure(ExtensionError.NetworkError("Empty URL"))
        }
        val scheme = try { java.net.URI(url).scheme?.lowercase() } catch(e: Exception) { null }
        if (scheme != "http" && scheme != "https") {
            return StremioResult.Failure(ExtensionError.NetworkError("Unsafe or invalid URL scheme"))
        }
        val backoffDelays = longArrayOf(500L, 1000L) // exponential backoff between retries
        var lastResult: StremioResult<T>? = null
        for (attempt in 1..maxAttempts) {
            val result: StremioResult<T> = try {
                NetworkClient.client.get(url) {
                    timeout {
                        requestTimeoutMillis = timeoutMs
                        connectTimeoutMillis = timeoutMs
                        socketTimeoutMillis = timeoutMs
                    }
                }.use { response ->
                    if (!response.status.isSuccess()) {
                        if (response.status.value == 404 && (
                            T::class == StremioMetaResponse::class || 
                            T::class == StremioStreamResponse::class || 
                            T::class == StremioSubtitleResponse::class
                        )) {
                            return StremioResult.Success(jsonParser.decodeFromString<T>("{}"))
                        }
                        val statusCode = response.status.value
                        val retryableStatus = statusCode == 429 || statusCode == 502 || statusCode == 503 || statusCode == 504
                        val errorMsg = "HTTP Status: $statusCode"
                        if (!retryableStatus) {
                            // Non-retryable HTTP error — fail immediately
                            if (recordSignal) safeRecordSignal(providerId, url, false, errorMsg)
                            return StremioResult.Failure(ExtensionError.NetworkError(errorMsg))
                        }
                        // Retryable HTTP status — record as transient failure for this attempt
                        StremioResult.Failure(ExtensionError.NetworkError(errorMsg))
                    } else {
                        val contentLength = response.headers[io.ktor.http.HttpHeaders.ContentLength]?.toLongOrNull()
                        if (contentLength != null && contentLength > MAX_RESPONSE_BYTES) {
                            val errorMsg = "Response exceeds 5MB limit"
                            if (recordSignal) safeRecordSignal(providerId, url, false, errorMsg)
                            return StremioResult.Failure(ExtensionError.NetworkError(errorMsg))
                        }
                        val text = kotlinx.coroutines.withTimeout(timeoutMs) {
                            // Use Ktor's built-in body collection instead of a manual
                            // busy-wait loop. The underlying OkHttp engine handles
                            // blocking reads properly, and this avoids compounding
                            // delay(50)+yield() spinning on slow connections.
                            val bytes = response.body<io.ktor.utils.io.ByteReadChannel>()
                                .readRemaining()
                                .readBytes()
                            if (bytes.size > MAX_RESPONSE_BYTES) {
                                throw java.io.IOException("Response exceeds 5MB limit")
                            }
                            bytes.decodeToString()
                        }
                        StremioResult.Success(jsonParser.decodeFromString<T>(text))
                    }
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException && e !is kotlinx.coroutines.TimeoutCancellationException) {
                    throw e
                }
                // Parse errors are not transient — fail immediately
                if (e is kotlinx.serialization.SerializationException || e is IllegalArgumentException) {
                    val redacted = UrlRedactor.redactErrorMessage(e.localizedMessage ?: e.javaClass.simpleName, url)
                    if (recordSignal) safeRecordSignal(providerId, url, false, redacted)
                    return StremioResult.Failure(ExtensionError.ParseError("Parsing failed: $redacted"))
                }
                val isTimeout = e is io.ktor.client.plugins.HttpRequestTimeoutException ||
                        e is java.net.SocketTimeoutException ||
                        e is kotlinx.coroutines.TimeoutCancellationException ||
                        e.javaClass.simpleName.contains("Timeout", ignoreCase = true)
                val redacted = UrlRedactor.redactErrorMessage(e.localizedMessage ?: e.javaClass.simpleName, url)
                if (isTimeout) {
                    StremioResult.Failure(ExtensionError.Timeout("Network timed out: $redacted"))
                } else {
                    StremioResult.Failure(ExtensionError.NetworkError("Network failed: $redacted"))
                }
            }
            // If successful, return immediately
            if (result is StremioResult.Success) {
                return result
            }
            lastResult = result
            // If we have more attempts, wait with exponential backoff before retrying
            if (attempt < maxAttempts) {
                val delayMs = backoffDelays.getOrElse(attempt - 1) { backoffDelays.last() }
                kotlinx.coroutines.delay(delayMs)
            }
        }
        // All attempts exhausted — record signal for the final failure and return it
        if (lastResult is StremioResult.Failure && recordSignal) {
            val error = (lastResult as StremioResult.Failure).error
            val isTimeout = error is ExtensionError.Timeout
            val errorMsg = error.message ?: "Unknown error"
            safeRecordSignal(providerId, url, isTimeout, errorMsg)
        }
        return lastResult ?: StremioResult.Failure(ExtensionError.NetworkError("All $maxAttempts attempts failed"))
    }
}
