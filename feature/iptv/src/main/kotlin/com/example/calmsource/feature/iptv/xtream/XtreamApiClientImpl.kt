package com.example.calmsource.feature.iptv.xtream

import com.example.calmsource.core.model.XtreamAuthResult
import com.example.calmsource.core.model.XtreamCategory
import com.example.calmsource.core.model.XtreamLiveChannel
import com.example.calmsource.core.model.XtreamProviderConfig
import com.example.calmsource.core.model.XtreamSeriesEpisode
import com.example.calmsource.core.model.XtreamSeriesItem
import com.example.calmsource.core.model.XtreamServerInfo
import com.example.calmsource.core.model.XtreamUserInfo
import com.example.calmsource.core.model.XtreamVodItem
import com.example.calmsource.core.model.XtreamShortEpgProgram
import com.example.calmsource.core.network.NetworkClient
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.feature.iptv.XtreamApiClient
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.plugins.timeout
import io.ktor.client.call.body
import io.ktor.client.statement.close
import io.ktor.http.HttpStatusCode
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.core.readBytes
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.net.URLEncoder

/**
 * Implementation of [XtreamApiClient] that communicates with Xtream-Codes compatible APIs.
 *
 * Security notes:
 * - URLs containing credentials are NEVER logged directly; all error messages use [UrlRedactor].
 * - Server URLs are validated before any network call to reject unsafe schemes.
 * - Response sizes are capped to prevent memory exhaustion.
 * - Ktor exception messages may embed the request URL — safeGet() redacts the full
 *   exception chain before returning it to callers.
 *
 * This implementation uses manual JSON parsing (jsonObject/jsonPrimitive) because the
 * feature/iptv module does not apply the kotlinx.serialization compiler plugin.
 */
class XtreamApiClientImpl(
    private val client: HttpClient = NetworkClient.xtreamClient
) : XtreamApiClient {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    companion object {
        // JSON is retained as a UTF-16 String and then expanded into a JsonElement tree.
        // Keeping this well below the smallest supported TV heap prevents a valid-but-huge
        // provider response from exhausting memory before it can be rejected.
        private const val MAX_RESPONSE_SIZE = 16 * 1024 * 1024L
        private const val AUTH_TIMEOUT_STANDARD_MILLIS = 60_000L
        private const val AUTH_TIMEOUT_LOW_RAM_MILLIS = 90_000L
        private const val CONNECT_TIMEOUT_STANDARD_MILLIS = 20_000L
        private const val CONNECT_TIMEOUT_LOW_RAM_MILLIS = 30_000L
        private const val LOW_RAM_HEAP_BYTES = 256L * 1024 * 1024
        private const val CATEGORY_TIMEOUT_MILLIS = 120_000L
        private const val LIVE_CATALOG_TIMEOUT_MILLIS = 300_000L
        private const val VOD_CATALOG_TIMEOUT_MILLIS = 300_000L
        private const val SERIES_CATALOG_TIMEOUT_MILLIS = 300_000L
        private const val SERIES_INFO_TIMEOUT_MILLIS = 120_000L
        private const val EPG_TIMEOUT_MILLIS = 60_000L
        private val BLOCKED_SCHEMES = setOf("file", "javascript", "data", "content", "ftp")
        // Aggressively match anything that looks like a URL with query params
        private val ERROR_URL_PATTERN = Regex("""(?:https?|xtream)://[^\s"'<>\])}]+""")
        // Also catch credential patterns even if the URL is partially malformed
        private val CREDENTIAL_PARAM_PATTERN = Regex("""[&?](?:password|pass|token|api_key)=[^&\s]+""")
        private val INACTIVE_ACCOUNT_STATUSES = setOf(
            "Expired",
            "Disabled",
            "Banned",
            "Not Active",
            "Inactive"
        )
        private val RETRYABLE_HTTP_STATUSES = setOf(429, 502, 503, 504, 513)
        private const val AUTH_MAX_ATTEMPTS = 3

        private fun encodePathSegment(value: String): String =
            URLEncoder.encode(value, "UTF-8").replace("+", "%20")

        internal fun authTimeoutMillis(): Long =
            if (Runtime.getRuntime().maxMemory() <= LOW_RAM_HEAP_BYTES) {
                AUTH_TIMEOUT_LOW_RAM_MILLIS
            } else {
                AUTH_TIMEOUT_STANDARD_MILLIS
            }

        internal fun authConnectTimeoutMillis(): Long =
            if (Runtime.getRuntime().maxMemory() <= LOW_RAM_HEAP_BYTES) {
                CONNECT_TIMEOUT_LOW_RAM_MILLIS
            } else {
                CONNECT_TIMEOUT_STANDARD_MILLIS
            }
    }

    // ── URL construction (private — never expose URLs with credentials) ───────

    /**
     * Strips any trailing path segments and query string from a server URL,
     * returning a clean base (scheme://host:port) suitable for appending API paths.
     */
    private fun normalizeBaseUrl(serverUrl: String): String {
        return XtreamServerUrlNormalizer.normalizePortalUrl(serverUrl)
            ?: serverUrl.trim().trimEnd('/').substringBefore('?')
    }

    /**
     * Builds the base player API URL with credentials embedded as query parameters.
     * The returned URL must NEVER be logged or persisted.
     */
    internal fun buildBaseUrl(config: XtreamProviderConfig, password: String): String {
        val baseUrl = normalizeBaseUrl(config.serverUrl)
        return buildPlayerApiUrl(baseUrl, config.username, password)
    }

    internal fun buildPlayerApiUrl(baseUrl: String, username: String, password: String): String {
        val encodedUser = URLEncoder.encode(username, "UTF-8")
        val encodedPass = URLEncoder.encode(password, "UTF-8")
        return "$baseUrl/player_api.php?username=$encodedUser&password=$encodedPass"
    }

    internal fun buildAuthRequestUrls(config: XtreamProviderConfig, password: String): List<String> {
        val primaryBase = normalizeBaseUrl(config.serverUrl)
        val candidateBases = linkedSetOf(primaryBase)
        XtreamServerUrlNormalizer.alternateSchemeUrl(primaryBase)?.let { candidateBases.add(it) }
        return candidateBases.map { buildPlayerApiUrl(it, config.username, password) }
    }

    internal fun shouldTryAlternateAuthUrl(errorMessage: String?): Boolean {
        if (errorMessage.isNullOrBlank()) return false
        val lower = errorMessage.lowercase()
        return lower.contains("404") ||
            lower.contains("403") ||
            lower.contains("not found") ||
            lower.contains("access denied") ||
            lower.contains("could not reach") ||
            lower.contains("network error") ||
            lower.contains("513") ||
            lower.contains("temporarily unavailable")
    }

    internal fun buildActionUrl(
        config: XtreamProviderConfig,
        password: String,
        action: String
    ): String {
        return "${buildBaseUrl(config, password)}&action=$action"
    }

    // ── URL validation ───────────────────────────────────────────────────────

    /**
     * Validates that the server URL uses an allowed scheme and contains no whitespace.
     * Rejects file://, javascript:, data:, content://, ftp:// and missing schemes.
     */
    internal fun validateServerUrl(url: String): Result<Unit> {
        if (url.isBlank()) {
            return Result.failure(IllegalArgumentException("Server URL is required"))
        }
        if (url != url.trim()) {
            return Result.failure(IllegalArgumentException("Server URL must not contain whitespace"))
        }
        val prepared = XtreamServerUrlNormalizer.preprocessPortalInput(url)
        val lower = prepared.lowercase().trim()
        if (lower.isBlank()) {
            return Result.failure(IllegalArgumentException("Server URL is required"))
        }
        for (scheme in BLOCKED_SCHEMES) {
            if (lower.startsWith("$scheme:")) {
                return Result.failure(SecurityException("Unsafe URL scheme: $scheme"))
            }
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) {
            return Result.failure(IllegalArgumentException("Server URL must use http:// or https://"))
        }
        if (prepared.any { it.isWhitespace() }) {
            return Result.failure(IllegalArgumentException("Server URL must not contain whitespace"))
        }
        return Result.success(Unit)
    }

    // ── Safe HTTP GET ────────────────────────────────────────────────────────

    /**
     * Performs an HTTP GET with size limits and error handling.
     *
     * Ktor may embed the full request URL (including query parameters with credentials)
     * in exception messages. We aggressively redact the entire exception chain before
     * returning it, using both URL-pattern matching and credential-parameter heuristics.
     */
    private suspend fun safeGet(
        url: String,
        contentLabel: String,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long = authConnectTimeoutMillis().coerceAtMost(requestTimeoutMillis)
    ): Result<String> {
        val maxAttempts = if (contentLabel == "account details") AUTH_MAX_ATTEMPTS else 3
        var lastFailure: Exception? = null
        for (attempt in 0 until maxAttempts) {
            if (attempt > 0) {
                delay(if (attempt == 1) 2_000L else 5_000L)
            }
            when (val outcome = safeGetOnce(url, contentLabel, requestTimeoutMillis, connectTimeoutMillis)) {
                is SafeGetOutcome.Success -> return Result.success(outcome.body)
                is SafeGetOutcome.HttpError -> {
                    val message = userFacingHttpStatus(outcome.status, contentLabel) +
                        outcome.hint?.let { " $it" }.orEmpty()
                    lastFailure = Exception(message)
                    val canRetry = attempt < maxAttempts - 1 &&
                        outcome.status in RETRYABLE_HTTP_STATUSES
                    if (!canRetry) {
                        return Result.failure(lastFailure!!)
                    }
                }
                is SafeGetOutcome.NetworkError -> {
                    lastFailure = outcome.error
                    if (attempt < maxAttempts - 1) {
                        continue  // Retry on transient network errors
                    }
                    return Result.failure(outcome.error)
                }
            }
        }
        return Result.failure(lastFailure ?: Exception("Request failed while fetching $contentLabel"))
    }

    private sealed class SafeGetOutcome {
        data class Success(val body: String) : SafeGetOutcome()
        data class HttpError(val status: Int, val hint: String? = null) : SafeGetOutcome()
        data class NetworkError(val error: Exception) : SafeGetOutcome()
    }

    private suspend fun safeGetOnce(
        url: String,
        contentLabel: String,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long
    ): SafeGetOutcome {
        return try {
            val response = client.get(url) {
                timeout {
                    this.requestTimeoutMillis = requestTimeoutMillis
                    this.connectTimeoutMillis = connectTimeoutMillis
                    this.socketTimeoutMillis = requestTimeoutMillis
                }
            }
            try {
                if (response.status != HttpStatusCode.OK) {
                    val hint = runCatching {
                        response.body<ByteReadChannel>()
                            .readRemaining(4_097L)
                            .readBytes()
                            .takeIf { it.size <= 4_096 }
                            ?.decodeToString()
                            ?.take(512)
                            ?.let(::parseProviderErrorHint)
                    }.getOrNull()
                    return SafeGetOutcome.HttpError(response.status.value, hint)
                }
                val declaredLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()
                if (declaredLength != null && declaredLength > MAX_RESPONSE_SIZE) {
                    return SafeGetOutcome.NetworkError(
                        Exception("Response too large while fetching $contentLabel")
                    )
                }
                val bytes = response.body<ByteReadChannel>()
                    .readRemaining(MAX_RESPONSE_SIZE + 1L)
                    .readBytes()
                if (bytes.size.toLong() > MAX_RESPONSE_SIZE) {
                    return SafeGetOutcome.NetworkError(
                        Exception("Response too large while fetching $contentLabel")
                    )
                }
                val body = bytes.decodeToString()
                SafeGetOutcome.Success(body)
            } finally {
                response.close()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMessage = redactExceptionChain(
                message = e.message ?: "Network error",
                causeMessage = e.cause?.message,
                contentLabel = contentLabel,
                requestTimeoutMillis = requestTimeoutMillis
            )
            SafeGetOutcome.NetworkError(Exception(safeMessage))
        }
    }

    internal fun parseProviderErrorHint(body: String): String? {
        if (body.isBlank()) return null
        return try {
            val root = json.parseToJsonElement(body.trim())
            val obj = root.jsonObject
            listOf("message", "error", "msg")
                .mapNotNull { key -> obj[key]?.jsonPrimitive?.contentOrNull?.trim() }
                .firstOrNull { it.isNotBlank() }
                ?.let { hint ->
                    val redacted = UrlRedactor.redactErrorMessage(hint)
                    ERROR_URL_PATTERN.replace(redacted, "REDACTED_URL")
                }
                ?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    internal fun isInactiveAccountStatus(status: String?): Boolean {
        if (status.isNullOrBlank()) return false
        return INACTIVE_ACCOUNT_STATUSES.any { it.equals(status, ignoreCase = true) }
    }

    /**
     * Redacts an entire exception chain, including the cause, ensuring no
     * credential-bearing URLs leak into user-facing or logged error messages.
     */
    internal fun userFacingHttpStatus(statusCode: Int, contentLabel: String): String {
        return when (statusCode) {
            401, 403 -> if (contentLabel == "account details") {
                "Access denied. Check your username, password, and server URL (http://host:port)."
            } else {
                "Access denied while fetching $contentLabel."
            }
            404 -> if (contentLabel == "account details") {
                "Server URL not found. Use http://host:port with no /get.php path, and include the port if your provider gave one."
            } else {
                "Provider endpoint not found while fetching $contentLabel."
            }
            429 -> "Too many requests. Wait a moment and try again."
            500, 502, 503, 504 ->
                "IPTV server is temporarily unavailable. Try again in a few minutes."
            513 ->
                "IPTV server is busy (HTTP 513). Close other devices using this account, wait a few minutes, then try again. If it persists, contact your provider or verify http vs https and the port."
            in 400..499 ->
                "Server rejected the request (HTTP $statusCode) while fetching $contentLabel."
            in 500..599 ->
                "Server error (HTTP $statusCode) while fetching $contentLabel."
            else ->
                "Server returned HTTP $statusCode while fetching $contentLabel."
        }
    }

    private fun timeoutMessage(contentLabel: String, requestTimeoutMillis: Long): String {
        val seconds = requestTimeoutMillis / 1000L
        return when (contentLabel) {
            "account details" ->
                "Could not reach the Xtream server within ${seconds}s. Use http://host:port (no /c/ or /get.php path), then verify username and password. If you used https://, also try http:// and include the port."
            else ->
                "Request timed out while fetching $contentLabel after $seconds seconds. The provider may be slow or the catalog may be large; try Sync again."
        }
    }

    private fun redactExceptionChain(
        message: String,
        causeMessage: String?,
        contentLabel: String,
        requestTimeoutMillis: Long
    ): String {
        if (message.contains("timeout", ignoreCase = true)) {
            return timeoutMessage(contentLabel, requestTimeoutMillis)
        }
        if (message.contains("Response too large", ignoreCase = true)) {
            return "Response too large while fetching $contentLabel"
        }

        // Redact the primary message
        var cleaned = UrlRedactor.redactErrorMessage(message)
        cleaned = ERROR_URL_PATTERN.replace(cleaned, "REDACTED_URL")
        cleaned = CREDENTIAL_PARAM_PATTERN.replace(cleaned, "REDACTED_PARAM")
        cleaned = cleaned.replace(Regex("""password=[^&\s]+"""), "password=<redacted>")
        cleaned = cleaned.replace(Regex("""pass=[^&\s]+"""), "pass=<redacted>")

        // Redact the cause message if present
        val causeCleaned = if (
            !causeMessage.isNullOrBlank() &&
            causeMessage != message &&
            !isUselessCauseMessage(causeMessage)
        ) {
            var c = UrlRedactor.redactErrorMessage(causeMessage)
            c = ERROR_URL_PATTERN.replace(c, "REDACTED_URL")
            c = CREDENTIAL_PARAM_PATTERN.replace(c, "REDACTED_PARAM")
            c = c.replace(Regex("""password=[^&\s]+"""), "password=<redacted>")
            c = c.replace(Regex("""pass=[^&\s]+"""), "pass=<redacted>")
            c
        } else null

        if (cleaned.contains("Unable to resolve host", ignoreCase = true)) {
            return "Network error while fetching $contentLabel: IPTV server hostname could not be reached (DNS lookup failed). Re-add the provider using your portal URL from your supplier."
        }

        val suffix = if (causeCleaned != null) " Cause: $causeCleaned" else ""
        return "Network error while fetching $contentLabel: $cleaned$suffix"
    }

    private fun isUselessCauseMessage(message: String): Boolean {
        val trimmed = message.trim()
        return trimmed == "kotlin.Unit" || trimmed == "Unit"
    }

    internal fun userFacingNetworkError(
        message: String,
        contentLabel: String,
        requestTimeoutMillis: Long
    ): String {
        if (message.contains("timeout", ignoreCase = true)) {
            return timeoutMessage(contentLabel, requestTimeoutMillis)
        }
        if (message.contains("Response too large", ignoreCase = true)) {
            return "Response too large while fetching $contentLabel"
        }
        val redacted = UrlRedactor.redactErrorMessage(message)
        val withoutUrls = ERROR_URL_PATTERN.replace(redacted, "REDACTED_URL")
        return "Network error while fetching $contentLabel: $withoutUrls"
    }

    // ── Authentication ───────────────────────────────────────────────────────

    override suspend fun authenticate(config: XtreamProviderConfig, password: String): Boolean {
        validateServerUrl(config.serverUrl).getOrElse { return false }
        val authUrls = buildAuthRequestUrls(config, password)
        val requestTimeoutMillis = authTimeoutMillis()
        val connectTimeoutMillis = authConnectTimeoutMillis()
        for (url in authUrls) {
            val body = safeGet(
                url = url,
                contentLabel = "account details",
                requestTimeoutMillis = requestTimeoutMillis,
                connectTimeoutMillis = connectTimeoutMillis,
            ).getOrNull() ?: continue
            if (parseAuthenticated(body)) return true
        }
        return false
    }

    private fun parseAuthenticated(body: String): Boolean {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val userInfo = root["user_info"]?.jsonObject ?: return false
            val auth = userInfo["auth"]?.jsonPrimitive?.contentOrNull
            val authLong = userInfo["auth"]?.jsonPrimitive?.longOrNull ?: auth?.toLongOrNull()
            val status = userInfo["status"]?.jsonPrimitive?.contentOrNull
            (authLong == 1L || auth == "1") && !isInactiveAccountStatus(status)
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Richer authentication that returns [XtreamAuthResult] with user/server info.
     * Uses the domain model from core:model.
     */
    suspend fun validateAccount(
        config: XtreamProviderConfig,
        password: String
    ): XtreamAuthResult {
        validateServerUrl(config.serverUrl).getOrElse { e ->
            return XtreamAuthResult(isAuthenticated = false, error = e.message)
        }
        val authUrls = buildAuthRequestUrls(config, password)
        val requestTimeoutMillis = authTimeoutMillis()
        val connectTimeoutMillis = authConnectTimeoutMillis()

        if (authUrls.size > 1) {
            return validateAccountParallel(
                authUrls = authUrls,
                requestTimeoutMillis = requestTimeoutMillis,
                connectTimeoutMillis = connectTimeoutMillis,
            )
        }

        return validateAccountSequential(
            authUrls = authUrls,
            requestTimeoutMillis = requestTimeoutMillis,
            connectTimeoutMillis = connectTimeoutMillis,
        )
    }

    private suspend fun validateAccountParallel(
        authUrls: List<String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
    ): XtreamAuthResult = coroutineScope {
        val outcomes = authUrls.map { url ->
            async {
                url to safeGet(
                    url = url,
                    contentLabel = "account details",
                    requestTimeoutMillis = requestTimeoutMillis,
                    connectTimeoutMillis = connectTimeoutMillis,
                )
            }
        }.awaitAll()

        outcomes.firstOrNull { it.second.isSuccess }?.let { (_, bodyResult) ->
            return@coroutineScope parseAuthResponse(bodyResult.getOrThrow())
        }

        val lastError = outcomes.lastOrNull()?.second?.exceptionOrNull()?.message ?: "Connection failed"
        XtreamAuthResult(isAuthenticated = false, error = lastError)
    }

    private suspend fun validateAccountSequential(
        authUrls: List<String>,
        requestTimeoutMillis: Long,
        connectTimeoutMillis: Long,
    ): XtreamAuthResult {
        var lastError: String? = null
        for ((index, url) in authUrls.withIndex()) {
            val bodyResult = safeGet(
                url = url,
                contentLabel = "account details",
                requestTimeoutMillis = requestTimeoutMillis,
                connectTimeoutMillis = connectTimeoutMillis,
            )
            if (bodyResult.isSuccess) {
                return parseAuthResponse(bodyResult.getOrThrow())
            }
            lastError = bodyResult.exceptionOrNull()?.message ?: "Connection failed"
            if (index < authUrls.lastIndex && shouldTryAlternateAuthUrl(lastError)) {
                continue
            }
            return XtreamAuthResult(
                isAuthenticated = false,
                error = lastError
            )
        }
        return XtreamAuthResult(
            isAuthenticated = false,
            error = lastError ?: "Connection failed"
        )
    }

    private fun parseAuthResponse(body: String): XtreamAuthResult {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val userInfoObj = root["user_info"]?.jsonObject
            if (userInfoObj == null) {
                return XtreamAuthResult(isAuthenticated = false, error = "Invalid credentials")
            }
            val auth = userInfoObj["auth"]?.jsonPrimitive?.contentOrNull
            val authLong = userInfoObj["auth"]?.jsonPrimitive?.longOrNull ?: auth?.toLongOrNull()
            if (authLong == 0L || auth == "0") {
                return XtreamAuthResult(isAuthenticated = false, error = "Invalid credentials")
            }
            val status = userInfoObj["status"]?.jsonPrimitive?.contentOrNull
            if (isInactiveAccountStatus(status)) {
                val reason = when (status?.lowercase()) {
                    "expired" -> "Subscription expired"
                    "disabled" -> "Account disabled"
                    "banned" -> "Account banned"
                    else -> "Account inactive ($status)"
                }
                return XtreamAuthResult(isAuthenticated = false, error = reason)
            }
            val expDateStr = userInfoObj["exp_date"]?.jsonPrimitive?.contentOrNull
            val expDate = if (expDateStr != null && expDateStr != "null") {
                expDateStr.toLongOrNull()
            } else {
                userInfoObj["exp_date"]?.jsonPrimitive?.longOrNull
            }

            val userInfo = XtreamUserInfo(
                username = userInfoObj["username"]?.jsonPrimitive?.contentOrNull ?: "",
                status = status ?: "",
                expirationDate = expDate,
                isTrial = userInfoObj["is_trial"]?.jsonPrimitive?.contentOrNull == "1",
                activeConnections = userInfoObj["active_cons"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                maxConnections = userInfoObj["max_connections"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                allowedOutputFormats = emptyList() // Parsed separately if needed
            )

            val serverInfoObj = root["server_info"]?.jsonObject
            val serverInfo = if (serverInfoObj != null) {
                XtreamServerInfo(
                    url = serverInfoObj["url"]?.jsonPrimitive?.contentOrNull ?: "",
                    port = serverInfoObj["port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    httpsPort = serverInfoObj["https_port"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0,
                    serverProtocol = serverInfoObj["server_protocol"]?.jsonPrimitive?.contentOrNull ?: "http",
                    timezone = serverInfoObj["timezone"]?.jsonPrimitive?.contentOrNull ?: ""
                )
            } else null

            XtreamAuthResult(
                isAuthenticated = true,
                userInfo = userInfo,
                serverInfo = serverInfo
            )
        } catch (e: Exception) {
            XtreamAuthResult(isAuthenticated = false, error = "Invalid server response")
        }
    }

    // ── Live Streams ─────────────────────────────────────────────────────────

    override suspend fun getLiveCategories(
        config: XtreamProviderConfig,
        password: String
    ): List<XtreamCategory> {
        validateServerUrl(config.serverUrl).getOrThrow()
        val url = buildActionUrl(config, password, "get_live_categories")
        val body = safeGet(
            url = url,
            contentLabel = "live categories",
            requestTimeoutMillis = CATEGORY_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseCategories(body)
    }

    override suspend fun getLiveStreams(
        config: XtreamProviderConfig,
        password: String,
        categoryId: String?
    ): List<XtreamLiveChannel> {
        validateServerUrl(config.serverUrl).getOrThrow()
        var url = buildActionUrl(config, password, "get_live_streams")
        if (categoryId != null) url += "&category_id=${URLEncoder.encode(categoryId, "UTF-8")}"
        val body = safeGet(
            url = url,
            contentLabel = "live channel catalog",
            requestTimeoutMillis = LIVE_CATALOG_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseLiveStreams(body, config.id)
    }

    // ── VOD ──────────────────────────────────────────────────────────────────

    override suspend fun getVodCategories(
        config: XtreamProviderConfig,
        password: String
    ): List<XtreamCategory> {
        validateServerUrl(config.serverUrl).getOrThrow()
        val url = buildActionUrl(config, password, "get_vod_categories")
        val body = safeGet(
            url = url,
            contentLabel = "VOD categories",
            requestTimeoutMillis = CATEGORY_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseCategories(body)
    }

    override suspend fun getVodStreams(
        config: XtreamProviderConfig,
        password: String,
        categoryId: String?
    ): List<XtreamVodItem> {
        validateServerUrl(config.serverUrl).getOrThrow()
        var url = buildActionUrl(config, password, "get_vod_streams")
        if (categoryId != null) url += "&category_id=${URLEncoder.encode(categoryId, "UTF-8")}"
        val body = safeGet(
            url = url,
            contentLabel = "VOD catalog",
            requestTimeoutMillis = VOD_CATALOG_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseVodStreams(body, config.id)
    }

    // ── Series ───────────────────────────────────────────────────────────────

    override suspend fun getSeriesCategories(
        config: XtreamProviderConfig,
        password: String
    ): List<XtreamCategory> {
        validateServerUrl(config.serverUrl).getOrThrow()
        val url = buildActionUrl(config, password, "get_series_categories")
        val body = safeGet(
            url = url,
            contentLabel = "series categories",
            requestTimeoutMillis = CATEGORY_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseCategories(body)
    }

    override suspend fun getSeries(
        config: XtreamProviderConfig,
        password: String,
        categoryId: String?
    ): List<XtreamSeriesItem> {
        validateServerUrl(config.serverUrl).getOrThrow()
        var url = buildActionUrl(config, password, "get_series")
        if (categoryId != null) url += "&category_id=${URLEncoder.encode(categoryId, "UTF-8")}"
        val body = safeGet(
            url = url,
            contentLabel = "series catalog",
            requestTimeoutMillis = SERIES_CATALOG_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseSeries(body, config.id)
    }

    override suspend fun getSeriesEpisodes(
        config: XtreamProviderConfig,
        password: String,
        seriesId: String
    ): List<XtreamSeriesEpisode> {
        validateServerUrl(config.serverUrl).getOrThrow()
        if (seriesId.isBlank()) return emptyList()
        val encodedSeriesId = URLEncoder.encode(seriesId, "UTF-8")
        val url = "${buildActionUrl(config, password, "get_series_info")}&series_id=$encodedSeriesId"
        val body = safeGet(
            url = url,
            contentLabel = "series info",
            requestTimeoutMillis = SERIES_INFO_TIMEOUT_MILLIS
        ).getOrThrow()
        return parseSeriesEpisodes(body, config.id, seriesId)
    }

    override suspend fun getShortEpg(
        config: XtreamProviderConfig,
        password: String,
        streamId: String
    ): List<XtreamShortEpgProgram> {
        validateServerUrl(config.serverUrl).getOrThrow()
        val url = "${buildActionUrl(config, password, "get_short_epg")}&stream_id=${URLEncoder.encode(streamId, "UTF-8")}"
        
        val body = safeGet(
            url = url,
            contentLabel = "short EPG",
            requestTimeoutMillis = EPG_TIMEOUT_MILLIS
        ).getOrThrow()
        
        return parseShortEpg(body)
    }

    internal fun parseShortEpg(body: String): List<XtreamShortEpgProgram> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val listings = root["epg_listings"]?.jsonArray ?: return emptyList()
            listings.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val epgId = obj["epg_id"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    
                    // Safe Base64 decoding of title & description
                    val titleRaw = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val title = decodeBase64Safe(titleRaw)
                    
                    val descRaw = obj["description"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val description = decodeBase64Safe(descRaw)
                    
                    val lang = obj["lang"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val start = obj["start"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    val end = obj["end"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    
                    XtreamShortEpgProgram(
                        id = id,
                        epgId = epgId,
                        title = title,
                        language = lang,
                        startTimestamp = start.toLongOrNull() ?: 0L,
                        endTimestamp = end.toLongOrNull() ?: 0L,
                        description = description
                    )
                } catch (e: Exception) {
                    safeLogWarning("Failed to parse short EPG listing entry", e)
                    null
                }
            }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse short EPG response", e)
            emptyList()
        }
    }

    private fun decodeBase64Safe(encoded: String): String {
        if (encoded.isBlank()) return ""
        return try {
            val decodedBytes = try {
                java.util.Base64.getDecoder().decode(encoded)
            } catch (e: Throwable) {
                android.util.Base64.decode(encoded, android.util.Base64.DEFAULT)
            }
            String(decodedBytes, Charsets.UTF_8)
        } catch (e: Exception) {
            encoded // Fallback to original string if not valid Base64
        }
    }

    internal fun requireArrayResponse(body: String, contentLabel: String) {
        runCatching {
            json.parseToJsonElement(body).jsonArray
        }.getOrElse {
            throw IllegalStateException("Provider returned an invalid $contentLabel response")
        }
    }

    // ── Stream URL builders (called lazily at playback time, NEVER persisted) ─

    /**
     * Builds a live stream playback URL. Must not be logged or stored.
     */
    fun buildLiveStreamUrl(
        config: XtreamProviderConfig,
        password: String,
        streamId: String
    ): String {
        validateServerUrl(config.serverUrl).getOrThrow()
        val base = normalizeBaseUrl(config.serverUrl)
        return "$base/live/${encodePathSegment(config.username)}/${encodePathSegment(password)}/$streamId.ts"
    }

    /**
     * Builds a VOD stream playback URL. Must not be logged or stored.
     */
    fun buildVodStreamUrl(
        config: XtreamProviderConfig,
        password: String,
        streamId: String,
        extension: String = "mp4"
    ): String {
        validateServerUrl(config.serverUrl).getOrThrow()
        val base = normalizeBaseUrl(config.serverUrl)
        return "$base/movie/${encodePathSegment(config.username)}/${encodePathSegment(password)}/$streamId.$extension"
    }

    // ── JSON parsing helpers (manual parsing — no @Serializable needed) ──────

    internal fun parseCategories(body: String): List<XtreamCategory> {
        return try {
            val array = json.parseToJsonElement(body).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val id = obj["category_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["category_name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val parentId = obj["parent_id"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it != "0" && it.isNotBlank() }
                    XtreamCategory(id = id, name = name, parentId = parentId)
                } catch (e: Exception) {
                    safeLogWarning("Failed to parse category entry", e)
                    null
                }
            }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse categories response - provider may have changed their API format", e)
            emptyList()
        }
    }

    internal fun parseLiveStreams(body: String, providerId: String): List<XtreamLiveChannel> {
        return try {
            val array = json.parseToJsonElement(body).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val streamId = obj["stream_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val streamType = obj["stream_type"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (streamType.isNotBlank() && !streamType.equals("live", ignoreCase = true)) {
                        return@mapNotNull null
                    }
                    val categoryId = obj["category_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val logo = obj["stream_icon"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    val epgChannelId = obj["epg_channel_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val tvArchivePrimitive = obj["tv_archive"]?.jsonPrimitive
                    val tvArchive = tvArchivePrimitive?.intOrNull
                        ?: tvArchivePrimitive?.contentOrNull?.toIntOrNull()
                        ?: 0
                    val tvArchiveDurationPrimitive = obj["tv_archive_duration"]?.jsonPrimitive
                    val tvArchiveDuration = tvArchiveDurationPrimitive?.intOrNull
                        ?: tvArchiveDurationPrimitive?.contentOrNull?.toIntOrNull()
                        ?: 0
                    XtreamLiveChannel(
                        id = "${providerId}_live_$streamId",
                        name = name,
                        streamId = streamId,
                        categoryId = categoryId,
                        logo = logo,
                        epgChannelId = epgChannelId,
                        tvArchive = tvArchive != 0,
                        tvArchiveDuration = tvArchiveDuration
                    )
                } catch (e: Exception) {
                    safeLogWarning("Failed to parse live stream entry for provider $providerId", e)
                    null
                }
            }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse live streams response for provider $providerId - provider may have changed their API format", e)
            emptyList()
        }
    }

    internal fun parseVodStreams(body: String, providerId: String): List<XtreamVodItem> {
        return try {
            val array = json.parseToJsonElement(body).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val streamId = obj["stream_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val categoryId = obj["category_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val poster = obj["stream_icon"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    val ratingStr = obj["rating"]?.jsonPrimitive?.contentOrNull
                    val rating = obj["rating"]?.jsonPrimitive?.doubleOrNull
                        ?: ratingStr?.toDoubleOrNull()
                    val containerExtension = obj["container_extension"]?.jsonPrimitive?.contentOrNull ?: "mp4"
                    val addedStr = obj["added"]?.jsonPrimitive?.contentOrNull
                    val added = obj["added"]?.jsonPrimitive?.longOrNull
                        ?: addedStr?.toLongOrNull() ?: 0L
                    XtreamVodItem(
                        id = "${providerId}_vod_$streamId",
                        name = name,
                        streamId = streamId,
                        categoryId = categoryId,
                        poster = poster,
                        rating = rating,
                        containerExtension = containerExtension,
                        added = added,
                        providerId = providerId
                    )
                } catch (e: Exception) {
                    safeLogWarning("Failed to parse VOD entry for provider $providerId", e)
                    null
                }
            }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse VOD response for provider $providerId - provider may have changed their API format", e)
            emptyList()
        }
    }

    internal fun parseSeries(body: String, providerId: String): List<XtreamSeriesItem> {
        return try {
            val array = json.parseToJsonElement(body).jsonArray
            array.mapNotNull { element ->
                try {
                    val obj = element.jsonObject
                    val seriesId = obj["series_id"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val name = obj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                    val categoryId = obj["category_id"]?.jsonPrimitive?.contentOrNull ?: ""
                    val poster = obj["cover"]?.jsonPrimitive?.contentOrNull
                        ?.takeIf { it.isNotBlank() }
                    val ratingStr = obj["rating"]?.jsonPrimitive?.contentOrNull
                    val rating = obj["rating"]?.jsonPrimitive?.doubleOrNull
                        ?: ratingStr?.toDoubleOrNull()
                    XtreamSeriesItem(
                        id = "${providerId}_series_$seriesId",
                        name = name,
                        seriesId = seriesId,
                        categoryId = categoryId,
                        poster = poster,
                        rating = rating,
                        providerId = providerId
                    )
                } catch (e: Exception) {
                    safeLogWarning("Failed to parse series entry for provider $providerId", e)
                    null
                }
            }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse series response for provider $providerId - provider may have changed their API format", e)
            emptyList()
        }
    }

    internal fun parseSeriesEpisodes(
        body: String,
        providerId: String,
        seriesId: String
    ): List<XtreamSeriesEpisode> {
        return try {
            val root = json.parseToJsonElement(body).jsonObject
            val episodes = root["episodes"] ?: return emptyList()
            when (episodes) {
                is kotlinx.serialization.json.JsonObject -> episodes.entries.flatMap { (seasonKey, value) ->
                    parseEpisodeCollection(
                        element = value,
                        providerId = providerId,
                        seriesId = seriesId,
                        fallbackSeason = seasonKey.toIntOrNull()
                    )
                }
                is kotlinx.serialization.json.JsonArray -> parseEpisodeCollection(
                    element = episodes,
                    providerId = providerId,
                    seriesId = seriesId,
                    fallbackSeason = null
                )
                else -> emptyList()
            }.distinctBy { it.episodeId }
        } catch (e: Exception) {
            safeLogWarning("Failed to parse series episodes for provider $providerId series $seriesId - provider may have changed their API format", e)
            emptyList()
        }
    }

    private fun parseEpisodeCollection(
        element: kotlinx.serialization.json.JsonElement,
        providerId: String,
        seriesId: String,
        fallbackSeason: Int?
    ): List<XtreamSeriesEpisode> {
        return when (element) {
            is kotlinx.serialization.json.JsonArray -> element.mapNotNull {
                parseEpisode(it, providerId, seriesId, fallbackSeason)
            }
            is kotlinx.serialization.json.JsonObject -> element.values.mapNotNull {
                parseEpisode(it, providerId, seriesId, fallbackSeason)
            }
            else -> emptyList()
        }
    }

    private fun parseEpisode(
        element: kotlinx.serialization.json.JsonElement,
        providerId: String,
        seriesId: String,
        fallbackSeason: Int?
    ): XtreamSeriesEpisode? {
        return try {
            val obj = element.jsonObject
            val episodeId = obj["id"]?.jsonPrimitive?.contentOrNull
                ?: obj["episode_id"]?.jsonPrimitive?.contentOrNull
                ?: return null
            val episodeNumber = obj["episode_num"]?.jsonPrimitive?.intOrNull
                ?: obj["episode_num"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: obj["episode"]?.jsonPrimitive?.intOrNull
                ?: obj["episode"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
            val seasonNumber = obj["season"]?.jsonPrimitive?.intOrNull
                ?: obj["season"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                ?: fallbackSeason
            val title = obj["title"]?.jsonPrimitive?.contentOrNull
                ?: obj["name"]?.jsonPrimitive?.contentOrNull
                ?: episodeNumber?.let { "Episode $it" }
                ?: "Episode"
            val containerExtension = obj["container_extension"]?.jsonPrimitive?.contentOrNull
                ?.takeIf { it.isNotBlank() }
                ?: "mp4"
            XtreamSeriesEpisode(
                id = "${providerId}_series_${seriesId}_episode_$episodeId",
                seriesId = seriesId,
                episodeId = episodeId,
                title = title,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                containerExtension = containerExtension,
                providerId = providerId
            )
        } catch (e: Exception) {
            // Individual episode parse failure is expected; logging at verbose level.
            safeLogVerbose("Failed to parse episode entry for series $seriesId: ${e.message}")
            null
        }
    }

    private fun safeLogWarning(message: String, throwable: Throwable) {
        runCatching { android.util.Log.w("XtreamApiClient", message, throwable) }
    }

    private fun safeLogVerbose(message: String) {
        runCatching { android.util.Log.v("XtreamApiClient", message) }
    }
}
