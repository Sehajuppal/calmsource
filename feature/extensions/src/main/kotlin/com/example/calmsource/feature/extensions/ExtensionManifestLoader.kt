package com.example.calmsource.feature.extensions

import com.example.calmsource.core.model.ExtensionError
import com.example.calmsource.core.model.ExtensionInstallResult
import com.example.calmsource.core.network.NetworkClient
import com.example.calmsource.core.parser.ExtensionManifestParser
import io.ktor.client.request.get
import io.ktor.client.plugins.timeout
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.use
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles fetching and parsing Stremio-compatible extension manifests from remote URLs.
 *
 * Enforces a strict boundary:
 * - Uses the configured [NetworkClient] with strict timeouts.
 * - Extracts body as text to pass to [ExtensionManifestParser].
 * - Gracefully catches timeouts, DNS failures, or large payloads, returning clean
 *   [ExtensionInstallResult] errors instead of crashing.
 */
object ExtensionManifestLoader {

    private val cache = ExtensionManifestCache()

    /**
     * Fetches a manifest from the provided URL, validates it, and parses it.
     *
     * @param url The raw user-provided URL.
     * @param isDebug Whether debug mode is enabled, to allow local URLs.
     * @param forceRefresh Whether to bypass cache.
     * @param allowStaleFallback When false, HTTP/parse/network failures return errors instead of stale cache.
     * @return [ExtensionInstallResult] indicating success (with parsed manifest) or failure.
     */
    suspend fun loadManifest(
        url: String,
        isDebug: Boolean = false,
        forceRefresh: Boolean = false,
        allowStaleFallback: Boolean = true
    ): ExtensionInstallResult = withContext(Dispatchers.IO) {
        val cleanUrl = ExtensionInstallValidator.normalizeUrl(url)
        val allowCleartext = com.example.calmsource.core.database.repository.UserPreferencesRepository.preferences.value.allowCleartextUserSources
        val validation = ExtensionInstallValidator.validate(cleanUrl, isDebug, allowCleartext)

        if (validation is ExtensionInstallValidator.ValidationResult.Invalid) {
            return@withContext ExtensionInstallResult(
                isSuccess = false,
                error = validation.error,
                warnings = emptyList()
            )
        }

        val validUri = (validation as ExtensionInstallValidator.ValidationResult.Valid)
        val requestUrl = validUri.requestUrl
        val warnings = validUri.warnings.toMutableList()

        if (!forceRefresh) {
            val cachedManifest = cache.get(requestUrl)
            if (cachedManifest != null) {
                val isAdult = cachedManifest.behaviorHints["adult"]?.toBoolean() == true ||
                        cachedManifest.rawAttributes["adult"]?.toBoolean() == true
                val isP2P = cachedManifest.behaviorHints["p2p"]?.toBoolean() == true ||
                        cachedManifest.rawAttributes["p2p"]?.toBoolean() == true
                if (isAdult) warnings.add("This addon contains adult content.")
                if (isP2P) warnings.add("This addon utilizes P2P (BitTorrent) streaming, which may expose your IP address.")
                return@withContext ExtensionInstallResult(
                    isSuccess = true,
                    manifest = cachedManifest,
                    warnings = warnings
                )
            }
        }

        try {
            val result = NetworkClient.client.get(requestUrl) {
                timeout {
                    requestTimeoutMillis = 15_000L
                }
            }.use { response ->
                if (!response.status.isSuccess()) {
                    val staleManifest = if (allowStaleFallback) cache.getStale(requestUrl) else null
                    if (staleManifest != null) {
                        warnings.add("Failed to refresh manifest. Using stale cache.")
                        return@use ExtensionInstallResult(
                            isSuccess = true,
                            manifest = staleManifest,
                            warnings = warnings
                        )
                    }
                    return@use ExtensionInstallResult(
                        isSuccess = false,
                        error = ExtensionError.InvalidManifest("HTTP Error: ${response.status.value} ${response.status.description}"),
                        warnings = warnings
                    )
                }

                val contentTypeHeader = response.headers["Content-Type"]
                if (contentTypeHeader != null) {
                    val parsedType = io.ktor.http.ContentType.parse(contentTypeHeader)
                    val isJson = (parsedType.contentType == "application" && 
                                  (parsedType.contentSubtype == "json" || parsedType.contentSubtype.endsWith("+json"))) ||
                                 (parsedType.contentType == "text" && parsedType.contentSubtype == "json")
                    if (!isJson) {
                        return@use ExtensionInstallResult(
                            isSuccess = false,
                            error = ExtensionError.InvalidManifest("Invalid Content-Type: $contentTypeHeader. Expected JSON type."),
                            warnings = warnings
                        )
                    }
                }

                val MAX_MANIFEST_BYTES = 5L * 1024 * 1024
                val contentLength = response.headers["Content-Length"]?.toLongOrNull()
                if (contentLength != null && contentLength > MAX_MANIFEST_BYTES) {
                    return@use ExtensionInstallResult(
                        isSuccess = false,
                        error = ExtensionError.InvalidManifest("Manifest exceeds ${MAX_MANIFEST_BYTES / 1024 / 1024}MB size limit."),
                        warnings = warnings
                    )
                }

                val jsonBody = response.bodyAsText()
                if (jsonBody.encodeToByteArray().size > MAX_MANIFEST_BYTES) {
                    return@use ExtensionInstallResult(
                        isSuccess = false,
                        error = ExtensionError.InvalidManifest("Manifest exceeds ${MAX_MANIFEST_BYTES / 1024 / 1024}MB size limit."),
                        warnings = warnings
                    )
                }
                val parseResult = ExtensionManifestParser.parse(jsonBody)

                if (!parseResult.isSuccess) {
                    val staleManifest = if (allowStaleFallback) cache.getStale(requestUrl) else null
                    if (staleManifest != null) {
                        warnings.add("Parse failed. Using stale cache.")
                        return@use ExtensionInstallResult(
                            isSuccess = true,
                            manifest = staleManifest,
                            warnings = warnings
                        )
                    }
                    return@use parseResult
                }

                warnings.addAll(parseResult.warnings)

                val parsedManifest = parseResult.manifest
                if (parsedManifest != null) {
                    val isAdult = parsedManifest.behaviorHints["adult"]?.toBoolean() == true ||
                            parsedManifest.rawAttributes["adult"]?.toBoolean() == true
                    val isP2P = parsedManifest.behaviorHints["p2p"]?.toBoolean() == true ||
                            parsedManifest.rawAttributes["p2p"]?.toBoolean() == true
                    if (isAdult) {
                        warnings.add("This addon contains adult content.")
                    }
                    if (isP2P) {
                        warnings.add("This addon utilizes P2P (BitTorrent) streaming, which may expose your IP address.")
                    }
                    cache.put(requestUrl, parsedManifest)
                }

                ExtensionInstallResult(
                    isSuccess = true,
                    manifest = parsedManifest,
                    warnings = warnings
                )
            }
            return@withContext result
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            val staleManifest = if (allowStaleFallback) cache.getStale(requestUrl) else null
            if (staleManifest != null) {
                warnings.add("Network error. Using stale cache.")
                return@withContext ExtensionInstallResult(
                    isSuccess = true,
                    manifest = staleManifest,
                    warnings = warnings
                )
            }
            val errorMsg = e.localizedMessage ?: e.javaClass.simpleName
            val redactedMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(errorMsg, requestUrl)

            val wasTimeout = e is java.net.SocketTimeoutException ||
                    e is io.ktor.client.plugins.HttpRequestTimeoutException ||
                    e is io.ktor.client.network.sockets.ConnectTimeoutException ||
                    e is kotlinx.coroutines.TimeoutCancellationException
            val wasConnectivity = e is java.net.UnknownHostException ||
                    e is java.net.ConnectException ||
                    e is java.net.NoRouteToHostException ||
                    e is javax.net.ssl.SSLHandshakeException

            val mappedError = if (wasTimeout) {
                ExtensionError.Timeout("Request timed out: $redactedMsg")
            } else if (wasConnectivity) {
                ExtensionError.NetworkError("Connection failed: $redactedMsg")
            } else {
                ExtensionError.InvalidManifest("Network error: $redactedMsg")
            }

            return@withContext ExtensionInstallResult(
                isSuccess = false,
                error = mappedError,
                warnings = warnings
            )
        }
    }
}
