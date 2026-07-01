package com.example.calmsource.core.network

import android.content.pm.ApplicationInfo
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.serialization.kotlinx.json.json
import io.ktor.client.plugins.logging.Logging
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logger
import kotlinx.serialization.json.Json
import okhttp3.ConnectionPool
import okhttp3.Dispatcher
import okio.buffer
import java.util.concurrent.TimeUnit

/**
 * Singleton providing a globally configured HTTP client for secure network operations.
 *
 * Security & Reliability configurations:
 * - Uses OkHttp engine
 * - Default request timeout of 10 seconds to prevent hanging operations.
 * - Max redirect limit enforced by OkHttp internally (prevents infinite redirects).
 * - Ignores unknown JSON keys to be forward-compatible with external schemas.
 * - Sets a user-agent to identify the application.
 *
 * Logging is installed only when the host process is debuggable (i.e. in
 * debug builds). Release builds skip the Ktor Logging plugin entirely so
 * user-facing traffic doesn't appear in `adb logcat`.
 */
object NetworkClient {

    private const val TIMEOUT_MILLIS = 25_000L
    private const val XTREAM_TIMEOUT_MILLIS = 120_000L
    private const val DEFAULT_MAX_RESPONSE_BYTES = 5 * 1024 * 1024L
    private const val XTREAM_MAX_RESPONSE_BYTES = 100 * 1024 * 1024L
    private const val IPTV_MAX_RESPONSE_BYTES = 256 * 1024 * 1024L
    private const val MAX_REDIRECTS = 5

    // Shared OkHttp connection pool and dispatcher for all Ktor clients.
    // Avoids creating 4 separate pools/dispatchers which wastes threads and sockets.
    private val sharedConnectionPool = ConnectionPool(
        maxIdleConnections = 5,
        keepAliveDuration = 30,
        timeUnit = TimeUnit.SECONDS
    )
    private val sharedDispatcher = Dispatcher().apply {
        maxRequests = 64
        maxRequestsPerHost = 10
    }
    private const val XTREAM_USER_AGENT =
        "Mozilla/5.0 (Linux; Android 13; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Mobile Safari/537.36"
    private val SENSITIVE_REDIRECT_HEADERS = listOf(
        "Authorization",
        "Proxy-Authorization",
        "Cookie",
        "X-Api-Key",
        "X-Auth-Token",
        "Token"
    )

    @Volatile
    var allowPrivateIps: Boolean = false

    fun isPrivateOrLocalAddress(address: java.net.InetAddress): Boolean {
        if (address.isLoopbackAddress ||
            address.isSiteLocalAddress ||
            address.isLinkLocalAddress ||
            address.isAnyLocalAddress) {
            return true
        }
        val bytes = address.address
        if (bytes != null && bytes.size == 16) {
            val firstByte = bytes[0].toInt() and 0xFF
            if (firstByte == 0xFC || firstByte == 0xFD) {
                return true
            }
        }
        return false
    }

    fun isLocalHostOrPrivateIpStatic(host: String): Boolean {
        val h = host.trim().lowercase().removeSurrounding("[", "]")
        if (h == "localhost" || h == "localhost.localdomain" || h.endsWith(".local")) return true
        
        // check IPv4 patterns
        val parts = h.split('.')
        if (parts.size == 4) {
            val p0 = parts[0].toIntOrNull()
            val p1 = parts[1].toIntOrNull()
            if (p0 != null && p1 != null) {
                if (p0 == 127 || p0 == 10 || p0 == 0) return true
                if (p0 == 192 && p1 == 168) return true
                if (p0 == 169 && p1 == 254) return true
                if (p0 == 172 && p1 in 16..31) return true
            }
        }
        
        // check IPv6 patterns
        if (h.contains(':')) {
            if (h == "::1" || h == "::") return true
            if (h.startsWith("fe8") || h.startsWith("fe9") || h.startsWith("fea") || h.startsWith("feb") ||
                h.startsWith("fec") || h.startsWith("fed") || h.startsWith("fee") || h.startsWith("fef") ||
                h.startsWith("fc") || h.startsWith("fd")) {
                return true
            }
        }
        return false
    }

    private fun guardedDns(allowPrivateAddresses: Boolean) = object : okhttp3.Dns {
        override fun lookup(hostname: String): List<java.net.InetAddress> {
            val addresses = okhttp3.Dns.SYSTEM.lookup(hostname)
            if (!allowPrivateAddresses && !allowPrivateIps && !com.example.calmsource.core.model.TestEnvironment.isTest) {
                for (address in addresses) {
                    if (isPrivateOrLocalAddress(address)) {
                        throw java.io.IOException("Access to private/local address is blocked: $address")
                    }
                }
            }
            return addresses
        }
    }

    /** DNS policy used by ordinary internet clients. User-configured IPTV clients use an
     * isolated opt-in policy so LAN portals work without weakening SSRF protection elsewhere. */
    val safeDns: okhttp3.Dns = guardedDns(allowPrivateAddresses = false)

    /**
     * Whether the Ktor Logging plugin should be installed. Defaults to false
     * (Secure by default). The app's [android.app.Application.onCreate] flips
     * this to true in debug builds via [setLoggingEnabled].
     */
    @Volatile
    private var loggingEnabled: Boolean = false

    /**
     * Toggle Ktor request/response logging. Call from `Application.onCreate`
     * with `applicationInfo.flags and FLAG_DEBUGGABLE != 0` (or similar) to
     * disable it in release builds.
     */
    fun setLoggingEnabled(enabled: Boolean) {
        loggingEnabled = enabled
    }

    val client: HttpClient by lazy {
        createClient(maxResponseBytes = DEFAULT_MAX_RESPONSE_BYTES)
    }

    val xtreamClient: HttpClient by lazy {
        createClient(
            maxResponseBytes = XTREAM_MAX_RESPONSE_BYTES,
            timeoutMillis = XTREAM_TIMEOUT_MILLIS,
            userAgent = XTREAM_USER_AGENT,
            acceptHeader = "*/*"
        )
    }

    /** Client for user-entered M3U/XMLTV URLs, including trusted LAN hosts. */
    val iptvClient: HttpClient by lazy {
        createClient(
            maxResponseBytes = IPTV_MAX_RESPONSE_BYTES,
            allowPrivateAddresses = true
        )
    }

    /** Large-response Xtream client scoped to user-entered IPTV portals, including LAN hosts. */
    val iptvXtreamClient: HttpClient by lazy {
        createClient(
            maxResponseBytes = XTREAM_MAX_RESPONSE_BYTES,
            timeoutMillis = XTREAM_TIMEOUT_MILLIS,
            userAgent = XTREAM_USER_AGENT,
            acceptHeader = "*/*",
            allowPrivateAddresses = true
        )
    }

    private fun createClient(
        maxResponseBytes: Long,
        timeoutMillis: Long = TIMEOUT_MILLIS,
        userAgent: String = "CalmSource/1.0 (Android)",
        acceptHeader: String = ContentType.Application.Json.toString(),
        allowPrivateAddresses: Boolean = false
    ): HttpClient {
        require(maxResponseBytes > 0) { "maxResponseBytes must be positive" }
        require(timeoutMillis > 0) { "timeoutMillis must be positive" }
        return HttpClient(OkHttp) {
            expectSuccess = false // We handle HTTP error codes manually where needed

            install(HttpTimeout) {
                requestTimeoutMillis = timeoutMillis
                connectTimeoutMillis = timeoutMillis
                socketTimeoutMillis = timeoutMillis
            }

            install(ContentNegotiation) {
                json(Json {
                    ignoreUnknownKeys = true
                    isLenient = true
                    encodeDefaults = true
                    coerceInputValues = true
                })
            }

            defaultRequest {
                header(HttpHeaders.Accept, acceptHeader)
                header(HttpHeaders.UserAgent, userAgent)
            }

            // Only install the Ktor Logging plugin when explicitly enabled
            // (typically only in debug builds). Release builds skip this
            // entirely so request URLs don't leak into `adb logcat`.
            install(Logging) {
                logger = object : Logger {
                    override fun log(message: String) {
                        if (!loggingEnabled) return
                        val redacted = UrlRedactor.redactErrorMessage(message)
                        try {
                            android.util.Log.i("KtorClient", redacted)
                        } catch (_: Throwable) {
                            // Local JVM unit tests use Android stubs where Log.i is not implemented.
                        }
                    }
                }
                level = LogLevel.INFO
                filter { loggingEnabled }
                sanitizeHeader { header ->
                    header.equals(io.ktor.http.HttpHeaders.Authorization, ignoreCase = true) ||
                    header.equals(io.ktor.http.HttpHeaders.Cookie, ignoreCase = true) ||
                    header.contains("Token", ignoreCase = true) ||
                    header.contains("Key", ignoreCase = true)
                }
            }

            engine {
                config {
                    dispatcher(sharedDispatcher)
                    dns(guardedDns(allowPrivateAddresses))
                    retryOnConnectionFailure(true)
                    connectionPool(sharedConnectionPool)
                    followRedirects(false)
                    followSslRedirects(false)
                    // Custom redirect interceptor to cap chain length at MAX_REDIRECTS.
                    // OkHttp's built-in followRedirects only counts a redirect chain
                    // when the URL stays identical; cache-busting query parameters
                    // trick the engine into a near-infinite loop, blocking the IO pool.
                    addInterceptor { chain ->
                        var request = chain.request()
                        var redirectCount = 0
                        while (redirectCount < MAX_REDIRECTS) {
                            val host = request.url.host
                            if (!allowPrivateAddresses && !NetworkClient.allowPrivateIps && !com.example.calmsource.core.model.TestEnvironment.isTest && NetworkClient.isLocalHostOrPrivateIpStatic(host)) {
                                throw java.io.IOException("Access to private/local address is blocked: $host")
                            }
                            val response = chain.proceed(request)
                            val location = response.header("Location")
                            if (response.isRedirect && !location.isNullOrBlank()) {
                                response.close()
                                val resolved = request.url.resolve(location) ?: break
                                // Do NOT propagate original query params to redirect targets.
                                // Xtream URLs contain credentials in query params — carrying them
                                // to a CDN redirect target is both a security leak and a playback
                                // failure (CDN rejects unknown params).
                                val sameOrigin = request.url.scheme == resolved.scheme &&
                                    request.url.host == resolved.host &&
                                    request.url.port == resolved.port
                                val redirectUrl = if (sameOrigin) {
                                    resolved
                                } else {
                                    resolved.newBuilder()
                                        .username("")
                                        .password("")
                                        .build()
                                }
                                val redirectBuilder = request.newBuilder().url(redirectUrl)
                                if (!sameOrigin) {
                                    SENSITIVE_REDIRECT_HEADERS.forEach { headerName ->
                                        redirectBuilder.removeHeader(headerName)
                                    }
                                }
                                request = redirectBuilder.build()
                                redirectCount++
                                continue
                            }
                            return@addInterceptor response
                        }
                        throw java.io.IOException("Too many redirects (max $MAX_REDIRECTS)")
                    }
                    addNetworkInterceptor { chain ->
                        val response = chain.proceed(chain.request())
                        val body = response.body
                        if (body != null) {
                            val limit = maxResponseBytes
                            val contentLength = body.contentLength()
                            if (contentLength > limit) {
                                response.close()
                                throw java.io.IOException("Response too large")
                            }
                            val wrappedBody = object : okhttp3.ResponseBody() {
                                private var closed = false
                                private var sourceClosed = false

                                private val source = (object : okio.ForwardingSource(body.source()) {
                                    private var totalBytes = 0L
                                    override fun read(sink: okio.Buffer, byteCount: Long): Long {
                                        if (sourceClosed) {
                                            throw java.io.IOException("Stream closed")
                                        }
                                        val readBytes = try {
                                            super.read(sink, byteCount)
                                        } catch (e: java.io.IOException) {
                                            sourceClosed = true
                                            safeClose()
                                            throw e
                                        }
                                        if (readBytes != -1L) {
                                            totalBytes += readBytes
                                            if (totalBytes > limit) {
                                                sourceClosed = true
                                                safeClose()
                                                throw java.io.IOException("Response too large")
                                            }
                                        }
                                        return readBytes
                                    }
                                    override fun close() {
                                        if (!sourceClosed) {
                                            sourceClosed = true
                                            try {
                                                super.close()
                                            } catch (e: Exception) {
                                                // Ignore
                                            }
                                        }
                                    }
                                }).buffer()

                                private fun safeClose() {
                                    if (!closed) {
                                        closed = true
                                        try {
                                            response.close()
                                        } catch (e: Exception) {
                                            // Ignore
                                        }
                                    }
                                }

                                override fun contentType() = body.contentType()
                                override fun contentLength() = contentLength
                                override fun source() = source
                                override fun close() {
                                    safeClose()
                                    try {
                                        source.close()
                                    } catch (e: Exception) {
                                        // Ignore
                                    }
                                }
                            }
                            response.newBuilder().body(wrappedBody).build()
                        } else {
                            response
                        }
                    }
                }
            }
        }
    }
}
