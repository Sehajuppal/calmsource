package com.example.calmsource.feature.debrid

import com.example.calmsource.core.model.*
import com.example.calmsource.core.network.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import io.ktor.client.statement.use
import io.ktor.client.plugins.ResponseException
import android.os.SystemClock
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.delay
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class RDDeviceCodeResponse(
    @SerialName("device_code") val deviceCode: String,
    @SerialName("user_code") val userCode: String,
    @SerialName("interval") val interval: Int,
    @SerialName("expires_in") val expiresIn: Int,
    @SerialName("verification_url") val verificationUrl: String
)

@Serializable
private data class RDCredentialsResponse(
    @SerialName("client_id") val clientId: String,
    @SerialName("client_secret") val clientSecret: String
)

@Serializable
private data class RDTokenResponse(
    @SerialName("access_token") val accessToken: String,
    @SerialName("refresh_token") val refreshToken: String,
    @SerialName("expires_in") val expiresIn: Long,
    @SerialName("token_type") val tokenType: String
)

@Serializable
private data class RDUserResponse(
    val id: Long,
    val username: String,
    val email: String,
    val premium: Int, // seconds remaining
    val expiration: String? = null
)

@Serializable
private data class RDTorrentAddResponse(
    val id: String,
    val uri: String
)

@Serializable
private data class RDTorrentFileInfo(
    val id: Int,
    val path: String,
    val bytes: Long = 0,
    val selected: Int = 0
)

@Serializable
private data class RDTorrentInfoResponse(
    val id: String,
    val filename: String,
    val status: String,
    val links: List<String> = emptyList(),
    val files: List<RDTorrentFileInfo> = emptyList()
)

@Serializable
private data class RDUnrestrictResponse(
    val id: String,
    val filename: String,
    val download: String,
    val link: String
)

class RealDebridHttpClient(
    private val client: HttpClient = NetworkClient.client
) : DebridProviderClient {

    // Mutex to serialize token refresh requests. Real-Debrid rotates refresh
    // tokens on every use, so concurrent refreshes cause the second request
    // to fail with an invalid token.
    private val refreshMutex = Mutex()

    override val providerType = DebridProviderType.REAL_DEBRID
    override val displayName = "Real-Debrid"
    override val capabilities = setOf(
        DebridCapability.ACCOUNT_STATUS,
        DebridCapability.CACHED_AVAILABILITY_CHECK,
        DebridCapability.LINK_RESOLVE,
        DebridCapability.DEVICE_CODE_AUTH
    )



    private fun getClientId(): String {
        // Public OAuth client ID for Real-Debrid device code flow.
        // This ID is registered with Real-Debrid for this application.
        return String(charArrayOf('X', '2', '4', '5', 'A', '4', 'X', 'A', 'I', 'B', 'G', 'V', 'M'))
    }

    internal data class RealDebridSessionState(
        val deviceCode: String,
        val expiresAtMs: Long,
        val clientId: String? = null,
        val clientSecret: String? = null,
        val tokenSet: DebridTokenSet? = null
    )

    override suspend fun startAuth(): DebridAuthSession {
        val response = client.get("https://api.real-debrid.com/oauth/v2/device/code") {
            parameter("client_id", getClientId())
            parameter("new_credentials", "yes")
        }.body<RDDeviceCodeResponse>()

        val sessionDetails = DebridDeviceCodeSession(
            userCode = response.userCode,
            deviceCode = response.deviceCode,
            verificationUrl = response.verificationUrl,
            intervalSeconds = response.interval,
            expiresInSeconds = response.expiresIn
        )

        val sessionId = "sess-rd-${UUID.randomUUID().toString().take(6)}"
        synchronized(sessionStates) {
            sessionStates.entries.removeIf { it.value.expiresAtMs < System.currentTimeMillis() }
            if (sessionStates.size >= 50) {
                val sortedKeys = sessionStates.entries
                    .sortedBy { it.value.expiresAtMs }
                    .map { it.key }
                val toEvict = sessionStates.size - 49
                for (i in 0 until toEvict) {
                    sessionStates.remove(sortedKeys[i])
                }
            }
            sessionStates[sessionId] = RealDebridSessionState(
                deviceCode = response.deviceCode,
                expiresAtMs = System.currentTimeMillis() + (response.expiresIn * 1000L)
            )
        }

        return DebridAuthSession.DeviceCode(
            id = sessionId,
            providerType = providerType,
            details = sessionDetails
        )
    }

    override suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession {
        require(session is DebridAuthSession.DeviceCode) { "Invalid session type" }
        val state = sessionStates[session.id] ?: throw IllegalStateException("Session state not found")

        val intervalMs = (session.details.intervalSeconds * 1000L).coerceAtLeast(1000L)
        val deadlineElapsedMs = SystemClock.elapsedRealtime() + (session.details.expiresInSeconds * 1000L)

        var success = false
        try {
            while (SystemClock.elapsedRealtime() < deadlineElapsedMs) {
                try {
                    client.get("https://api.real-debrid.com/oauth/v2/device/credentials") {
                        parameter("client_id", getClientId())
                        parameter("code", state.deviceCode)
                    }.use { response ->
                        if (response.status == HttpStatusCode.OK) {
                            val credentials = response.body<RDCredentialsResponse>()
                            synchronized(sessionStates) {
                                if (sessionStates.containsKey(session.id)) {
                                    sessionStates[session.id] = state.copy(
                                        clientId = credentials.clientId,
                                        clientSecret = credentials.clientSecret
                                    )
                                }
                            }
                            success = true
                            return session
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Ignore transient polling errors until the device code expires.
                }
                delay(intervalMs)
            }
            throw IllegalStateException("Device code verification expired")
        } finally {
            if (!success) {
                sessionStates.remove(session.id)
            }
        }
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        try {
            val state = sessionStates[session.id] ?: throw IllegalStateException("Session state not found")
            val clientId = state.clientId ?: throw IllegalStateException("Client ID not acquired")
            val clientSecret = state.clientSecret ?: throw IllegalStateException("Client secret not acquired")

            val tokenResponse = client.submitForm(
                url = "https://api.real-debrid.com/oauth/v2/device/token",
                formParameters = Parameters.build {
                    append("client_id", clientId)
                    append("client_secret", clientSecret)
                    append("code", state.deviceCode)
                    append("grant_type", "http://oauth.net/grant_type/device/1.0")
                }
            ).body<RDTokenResponse>()

            return DebridTokenSet(
                accessToken = tokenResponse.accessToken,
                refreshToken = tokenResponse.refreshToken,
                expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L),
                clientId = clientId,
                clientSecret = clientSecret
            )
        } finally {
            sessionStates.remove(session.id)
        }
    }

    private fun isTokenExpiredOrNearExpiry(tokenSet: DebridTokenSet): Boolean {
        if (tokenSet.accessToken == null) return true
        if (tokenSet.expiresAt == 0L) return false
        // Refresh if expired or expiring in less than 5 minutes (300,000 ms)
        return System.currentTimeMillis() + 300_000L >= tokenSet.expiresAt
    }

    private suspend fun refreshAccessToken(tokenSet: DebridTokenSet): DebridTokenSet {
        val refreshToken = tokenSet.refreshToken ?: throw IllegalStateException("Missing refresh token")
        val clientId = tokenSet.clientId ?: getClientId()
        val clientSecret = tokenSet.clientSecret ?: throw IllegalStateException("Missing client secret")

        val tokenResponse = client.submitForm(
            url = "https://api.real-debrid.com/oauth/v2/token",
            formParameters = Parameters.build {
                append("client_id", clientId)
                append("client_secret", clientSecret)
                append("code", refreshToken)
                append("grant_type", "refresh_token")
            }
        ).body<RDTokenResponse>()

        val newTokens = DebridTokenSet(
            accessToken = tokenResponse.accessToken,
            refreshToken = tokenResponse.refreshToken,
            expiresAt = System.currentTimeMillis() + (tokenResponse.expiresIn * 1000L),
            clientId = clientId,
            clientSecret = clientSecret
        )

        val accountId = DebridRepository.listAccounts()
            .find { account ->
                val tokens = DebridRepository.tokenStore.getTokensForAccount(DebridProviderType.REAL_DEBRID, account.id)
                tokens?.refreshToken == refreshToken
            }?.id ?: SecureTokenStore.DEFAULT_ACCOUNT_ID

        DebridRepository.tokenStore.saveTokensForAccount(DebridProviderType.REAL_DEBRID, accountId, newTokens)
        if (accountId != SecureTokenStore.DEFAULT_ACCOUNT_ID) {
            DebridRepository.tokenStore.saveTokensForAccount(DebridProviderType.REAL_DEBRID, SecureTokenStore.DEFAULT_ACCOUNT_ID, newTokens)
        }

        return newTokens
    }

    /**
     * Serializes token refresh through [refreshMutex] so that concurrent
     * requests don't each try to rotate the single-use refresh token.
     */
    private suspend fun safeRefreshAccessToken(tokenSet: DebridTokenSet): DebridTokenSet {
        return refreshMutex.withLock {
            // Another coroutine may have already refreshed while we waited.
            // Re-read the stored token and compare to detect this.
            val accountId = DebridRepository.listAccounts()
                .find { account ->
                    val tokens = DebridRepository.tokenStore.getTokensForAccount(DebridProviderType.REAL_DEBRID, account.id)
                    tokens?.refreshToken == tokenSet.refreshToken
                }?.id ?: SecureTokenStore.DEFAULT_ACCOUNT_ID
            val stored = DebridRepository.tokenStore.getTokensForAccount(DebridProviderType.REAL_DEBRID, accountId)
            if (stored != null && stored.accessToken != tokenSet.accessToken && !isTokenExpiredOrNearExpiry(stored)) {
                // Already refreshed by another coroutine
                return@withLock stored
            }
            refreshAccessToken(tokenSet)
        }
    }

    private suspend fun <T> executeWithTokenRefresh(
        tokenSet: DebridTokenSet,
        block: suspend (DebridTokenSet) -> T
    ): T {
        var currentTokenSet = tokenSet
        if (isTokenExpiredOrNearExpiry(currentTokenSet) &&
            !currentTokenSet.refreshToken.isNullOrBlank() &&
            !currentTokenSet.clientSecret.isNullOrBlank()
        ) {
            try {
                currentTokenSet = safeRefreshAccessToken(currentTokenSet)
            } catch (e: Exception) {
                android.util.Log.e("RealDebridHttpClient", "Pre-emptive token refresh failed", e)
            }
        }

        try {
            return block(currentTokenSet)
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Unauthorized &&
                !currentTokenSet.refreshToken.isNullOrBlank() &&
                !currentTokenSet.clientSecret.isNullOrBlank()
            ) {
                android.util.Log.d("RealDebridHttpClient", "Received 401 Unauthorized, attempting token refresh")
                try {
                    currentTokenSet = safeRefreshAccessToken(currentTokenSet)
                    return block(currentTokenSet)
                } catch (refreshEx: Exception) {
                    android.util.Log.e("RealDebridHttpClient", "Token refresh retry failed", refreshEx)
                    throw e
                }
            } else {
                throw e
            }
        }
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus = executeWithTokenRefresh(tokenSet) { tokens ->
        val accessToken = tokens.accessToken ?: throw IllegalArgumentException("Missing access token")
        val response = client.get("https://api.real-debrid.com/rest/1.0/user") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body<RDUserResponse>()

        val daysRemaining = (response.premium / 86400).coerceAtLeast(0)
        DebridAccountStatus(
            username = response.username,
            email = response.email,
            premiumDaysRemaining = daysRemaining,
            expirationDate = response.expiration,
            isPremium = response.premium > 0
        )
    }

    override suspend fun checkCachedAvailability(
        hashes: List<String>,
        tokenSet: DebridTokenSet
    ): Map<String, DebridCachedAvailability> = executeWithTokenRefresh(tokenSet) { tokens ->
        if (hashes.isEmpty()) return@executeWithTokenRefresh emptyMap()
        val accessToken = tokens.accessToken ?: return@executeWithTokenRefresh emptyMap()

        // Real-Debrid instant availability check takes up to 100 hashes separated by '/'
        val pathHashes = hashes.joinToString("/")
        val response = client.get("https://api.real-debrid.com/rest/1.0/torrents/instantAvailability/$pathHashes") {
            headers {
                append(HttpHeaders.Authorization, "Bearer $accessToken")
            }
        }.body<Map<String, JsonObject>>()

        hashes.associateWith { hash ->
            val hashResponse = response[hash.lowercase()] ?: response[hash.uppercase()]
            val rdArray = hashResponse?.get("rd")?.jsonArray
            val isCached = rdArray != null && rdArray.isNotEmpty()
            
            val filesList = mutableListOf<String>()
            if (isCached) {
                rdArray.forEach { fileMap ->
                    fileMap.jsonObject.values.forEach { fileInfo ->
                        fileInfo.jsonObject["filename"]?.jsonPrimitive?.contentOrNull?.let {
                            filesList.add(it)
                        }
                    }
                }
            }

            DebridCachedAvailability(
                infoHash = hash,
                isCached = isCached,
                filesList = filesList
            )
        }
    }

    override suspend fun resolveLink(
        request: DebridResolveRequest,
        tokenSet: DebridTokenSet
    ): DebridResolveResult = executeWithTokenRefresh(tokenSet) { tokens ->
        val accessToken = tokens.accessToken ?: return@executeWithTokenRefresh DebridResolveResult(
            status = DebridResolveStatus.FAILURE,
            error = "Missing access token"
        )

        try {
            // Direct Link Resolution
            val hostLink = request.hostLink
            if (hostLink != null) {
                val res = client.submitForm(
                    url = "https://api.real-debrid.com/rest/1.0/unrestrict/link",
                    formParameters = Parameters.build {
                        append("link", hostLink)
                    }
                ) {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $accessToken")
                    }
                }.body<RDUnrestrictResponse>()
                return@executeWithTokenRefresh DebridResolveResult(url = res.download)
            }

            // Torrent/Magnet Resolution
            val magnet = request.magnetUrl ?: "magnet:?xt=urn:btih:${request.infoHash}"
            val addRes = client.submitForm(
                url = "https://api.real-debrid.com/rest/1.0/torrents/addMagnet",
                formParameters = Parameters.build {
                    append("magnet", magnet)
                }
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }.body<RDTorrentAddResponse>()

            val torrentId = addRes.id

            // Select all files in the torrent
            client.submitForm(
                url = "https://api.real-debrid.com/rest/1.0/torrents/selectFiles/$torrentId",
                formParameters = Parameters.build {
                    append("files", "all")
                }
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }.use { }

            // Poll for completion within a bounded playback-resolution window.
            var info: RDTorrentInfoResponse? = null
            var attempts = 0
            val deadlineElapsedMs = SystemClock.elapsedRealtime() + TORRENT_RESOLVE_TIMEOUT_MS
            while (attempts < TORRENT_RESOLVE_MAX_POLLS && SystemClock.elapsedRealtime() < deadlineElapsedMs) {
                info = client.get("https://api.real-debrid.com/rest/1.0/torrents/info/$torrentId") {
                    headers {
                        append(HttpHeaders.Authorization, "Bearer $accessToken")
                    }
                }.body<RDTorrentInfoResponse>()

                if (info.status == "downloaded" || info.status == "uploading") {
                    break
                }
                delay(TORRENT_RESOLVE_POLL_INTERVAL_MS)
                attempts++
            }

            val links = info?.links
            if (links.isNullOrEmpty()) {
                return@executeWithTokenRefresh DebridResolveResult(
                    status = DebridResolveStatus.UNAVAILABLE,
                    error = "No links generated for torrent"
                )
            }

            // Resolve the link corresponding to fileIndex (or default to the first link)
            val selectedLinkIndex = request.fileIndex?.coerceIn(0, links.lastIndex) ?: 0
            val hosterLink = links[selectedLinkIndex]

            val unrestrictRes = client.submitForm(
                url = "https://api.real-debrid.com/rest/1.0/unrestrict/link",
                formParameters = Parameters.build {
                    append("link", hosterLink)
                }
            ) {
                headers {
                    append(HttpHeaders.Authorization, "Bearer $accessToken")
                }
            }.body<RDUnrestrictResponse>()

            DebridResolveResult(url = unrestrictRes.download)

        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseException) {
            if (e.response.status == HttpStatusCode.Unauthorized &&
                !tokens.refreshToken.isNullOrBlank() &&
                !tokens.clientSecret.isNullOrBlank()
            ) {
                throw e
            }
            val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
            DebridResolveResult(
                status = DebridResolveStatus.FAILURE,
                error = safeMsg
            )
        } catch (e: Exception) {
            val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
            DebridResolveResult(
                status = DebridResolveStatus.FAILURE,
                error = safeMsg
            )
        }
    }

    override suspend fun logout(tokenSet: DebridTokenSet) {
        // Real-Debrid does not offer a revoke OAuth endpoint. Cleaning local state is sufficient.
    }

    override suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean {
        return try {
            getAccountStatus(tokenSet)
            true
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            false
        }
    }

    override suspend fun getHealth(): DebridAccountHealth = DebridAccountHealth.HEALTHY

    companion object {
        const val TORRENT_RESOLVE_MAX_POLLS = 20
        const val TORRENT_RESOLVE_POLL_INTERVAL_MS = 1_000L
        const val TORRENT_RESOLVE_TIMEOUT_MS = 20_000L

        @JvmField
        internal val sessionStates = ConcurrentHashMap<String, RealDebridSessionState>()
    }
}
