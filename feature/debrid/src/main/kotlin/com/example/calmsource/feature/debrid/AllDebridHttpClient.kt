package com.example.calmsource.feature.debrid

import com.example.calmsource.core.model.*
import com.example.calmsource.core.network.NetworkClient
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.*
import io.ktor.client.request.forms.submitForm
import io.ktor.http.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.Serializable
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class ADResponse<T>(
    val status: String,
    val data: T? = null,
    val error: ADError? = null
)

@Serializable
private data class ADError(
    val code: String,
    val message: String
)

@Serializable
private data class ADPinGetResponse(
    val pin: String,
    val check: String,
    val expires_in: Int,
    val userUrl: String
)

@Serializable
private data class ADPinCheckResponse(
    val activated: Boolean,
    val apikey: String? = null
)

@Serializable
private data class ADUserResponse(
    val user: ADUserInfo
)

@Serializable
private data class ADUserInfo(
    val username: String,
    val email: String,
    val isPremium: Boolean,
    val premiumUntil: Long
)

@Serializable
private data class ADInstantResponse(
    val magnets: List<ADInstantMagnet> = emptyList()
)

@Serializable
private data class ADInstantMagnet(
    val hash: String,
    val instant: Boolean,
    val files: List<ADInstantFile> = emptyList()
)

@Serializable
private data class ADInstantFile(
    val n: String,
    val s: Long = 0
)

@Serializable
private data class ADUnlockResponse(
    val link: String,
    val filename: String
)

@Serializable
private data class ADUploadResponse(
    val magnets: List<ADUploadMagnet> = emptyList()
)

@Serializable
private data class ADUploadMagnet(
    val id: Long,
    val name: String,
    val hash: String
)

@Serializable
private data class ADStatusResponse(
    val magnets: ADStatusMagnet
)

@Serializable
private data class ADStatusMagnet(
    val id: Long,
    val statusCode: Int,
    val links: List<ADStatusLink> = emptyList()
)

@Serializable
private data class ADStatusLink(
    val link: String,
    val filename: String
)

class AllDebridHttpClient(
    private val client: HttpClient = NetworkClient.client
) : DebridProviderClient {

    override val providerType = DebridProviderType.ALL_DEBRID
    override val displayName = "AllDebrid"
    override val capabilities = setOf(
        DebridCapability.ACCOUNT_STATUS,
        DebridCapability.CACHED_AVAILABILITY_CHECK,
        DebridCapability.LINK_RESOLVE,
        DebridCapability.PIN_AUTH
    )

    private val AGENT = "CalmSource"
    internal data class AllDebridSessionState(
        val pin: String,
        val check: String,
        val expiresAtMs: Long,
        val apiKey: String? = null
    )

    override suspend fun startAuth(): DebridAuthSession {
        val url = "https://api.alldebrid.com/v4/pin/get"
        val response = client.get(url) {
            parameter("agent", AGENT)
        }.body<ADResponse<ADPinGetResponse>>()

        if (response.status != "success" || response.data == null) {
            throw IllegalStateException(response.error?.message ?: "Failed to get PIN from AllDebrid")
        }

        val data = response.data
        val sessionDetails = DebridPinSession(
            pinUrl = data.userUrl,
            pinCode = data.pin,
            expiresInSeconds = data.expires_in
        )

        val sessionId = "sess-ad-${UUID.randomUUID().toString().take(6)}"
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
            sessionStates[sessionId] = AllDebridSessionState(
                pin = data.pin,
                check = data.check,
                expiresAtMs = System.currentTimeMillis() + (data.expires_in * 1000L)
            )
        }

        return DebridAuthSession.Pin(
            id = sessionId,
            providerType = providerType,
            details = sessionDetails
        )
    }

    override suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession {
        require(session is DebridAuthSession.Pin) { "Invalid session type" }
        val state = sessionStates[session.id] ?: throw IllegalStateException("Session state not found")

        val expiresAtMs = session.details.expiresAtMs
        var success = false
        try {
            while (System.currentTimeMillis() < expiresAtMs) {
                try {
                    val response = client.get("https://api.alldebrid.com/v4/pin/check") {
                        parameter("agent", AGENT)
                        parameter("pin", state.pin)
                        parameter("check", state.check)
                    }.body<ADResponse<ADPinCheckResponse>>()

                    if (response.status == "success" && response.data != null) {
                        val data = response.data
                        if (data.activated && data.apikey != null) {
                            synchronized(sessionStates) {
                                if (sessionStates.containsKey(session.id)) {
                                    sessionStates[session.id] = state.copy(apiKey = data.apikey)
                                }
                            }
                            success = true
                            return session
                        }
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    // Ignore transient polling network errors
                }
                delay(5000)
            }
            throw IllegalStateException("PIN authorization expired")
        } finally {
            if (!success) {
                sessionStates.remove(session.id)
            }
        }
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        try {
            val state = sessionStates[session.id] ?: throw IllegalStateException("Session state not found")
            val apiKey = state.apiKey ?: throw IllegalStateException("API key not acquired")

            return DebridTokenSet(
                apiKey = apiKey
            )
        } finally {
            sessionStates.remove(session.id)
        }
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus {
        val apiKey = tokenSet.apiKey ?: throw IllegalArgumentException("Missing API Key")
        val response = client.get("https://api.alldebrid.com/v4/user") {
            parameter("agent", AGENT)
            parameter("apikey", apiKey)
        }.body<ADResponse<ADUserResponse>>()

        if (response.status != "success" || response.data == null) {
            throw IllegalStateException(response.error?.message ?: "Failed to get account info")
        }

        val user = response.data.user
        val remainingMs = (user.premiumUntil * 1000L) - System.currentTimeMillis()
        val daysRemaining = (remainingMs / (86400 * 1000L)).coerceAtLeast(0).toInt()

        return DebridAccountStatus(
            username = user.username,
            email = user.email,
            premiumDaysRemaining = daysRemaining,
            expirationDate = null,
            isPremium = user.isPremium
        )
    }

    override suspend fun checkCachedAvailability(
        hashes: List<String>,
        tokenSet: DebridTokenSet
    ): Map<String, DebridCachedAvailability> {
        if (hashes.isEmpty()) return emptyMap()
        val apiKey = tokenSet.apiKey ?: return emptyMap()

        val response = client.submitForm(
            url = "https://api.alldebrid.com/v4/magnet/instant",
            formParameters = Parameters.build {
                hashes.forEach { hash ->
                    append("magnets[]", hash)
                }
            }
        ) {
            parameter("agent", AGENT)
            parameter("apikey", apiKey)
        }.body<ADResponse<ADInstantResponse>>()

        if (response.status != "success" || response.data == null) {
            return emptyMap()
        }

        val magnetsMap = response.data.magnets.associateBy { it.hash.lowercase() }

        return hashes.associateWith { hash ->
            val magnet = magnetsMap[hash.lowercase()]
            val isCached = magnet?.instant == true
            val filesList = magnet?.files?.map { it.n } ?: emptyList()

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
    ): DebridResolveResult {
        val apiKey = tokenSet.apiKey ?: return DebridResolveResult(
            status = DebridResolveStatus.FAILURE,
            error = "Missing API key"
        )

        try {
            // Direct Link Resolution
            val hostLink = request.hostLink
            if (hostLink != null) {
                val res = client.get("https://api.alldebrid.com/v4/link/unlock") {
                    parameter("agent", AGENT)
                    parameter("apikey", apiKey)
                    parameter("link", hostLink)
                }.body<ADResponse<ADUnlockResponse>>()

                if (res.status == "success" && res.data != null) {
                    return DebridResolveResult(url = res.data.link)
                } else {
                    return DebridResolveResult(
                        status = DebridResolveStatus.FAILURE,
                        error = res.error?.message ?: "Unlock failed"
                    )
                }
            }

            // Torrent/Magnet Resolution
            val magnet = request.magnetUrl ?: "magnet:?xt=urn:btih:${request.infoHash}"
            val uploadRes = client.submitForm(
                url = "https://api.alldebrid.com/v4/magnet/upload",
                formParameters = Parameters.build {
                    append("magnets[]", magnet)
                }
            ) {
                parameter("agent", AGENT)
                parameter("apikey", apiKey)
            }.body<ADResponse<ADUploadResponse>>()

            if (uploadRes.status != "success" || uploadRes.data == null || uploadRes.data.magnets.isEmpty()) {
                return DebridResolveResult(
                    status = DebridResolveStatus.FAILURE,
                    error = uploadRes.error?.message ?: "Upload failed"
                )
            }

            val magnetId = uploadRes.data.magnets.first().id

            // Poll for completion (statusCode 4 = ready)
            var status: ADStatusResponse? = null
            var attempts = 0
            while (attempts < 10) {
                val response = client.get("https://api.alldebrid.com/v4/magnet/status") {
                    parameter("agent", AGENT)
                    parameter("apikey", apiKey)
                    parameter("id", magnetId)
                }.body<ADResponse<ADStatusResponse>>()

                if (response.status == "success" && response.data != null) {
                    status = response.data
                    if (status.magnets.statusCode == 4) {
                        break
                    }
                }
                delay(1000)
                attempts++
            }

            val links = status?.magnets?.links
            if (links.isNullOrEmpty()) {
                return DebridResolveResult(
                    status = DebridResolveStatus.UNAVAILABLE,
                    error = "No links generated for magnet"
                )
            }

            // Resolve the link corresponding to fileIndex (or default to the first link)
            val selectedLinkIndex = request.fileIndex?.coerceIn(0, links.lastIndex) ?: 0
            val hosterLink = links[selectedLinkIndex].link

            val unlockRes = client.get("https://api.alldebrid.com/v4/link/unlock") {
                parameter("agent", AGENT)
                parameter("apikey", apiKey)
                parameter("link", hosterLink)
            }.body<ADResponse<ADUnlockResponse>>()

            if (unlockRes.status == "success" && unlockRes.data != null) {
                return DebridResolveResult(url = unlockRes.data.link)
            } else {
                return DebridResolveResult(
                    status = DebridResolveStatus.FAILURE,
                    error = unlockRes.error?.message ?: "Unlock failed"
                )
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            val safeMsg = com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(e.message ?: "Unknown error")
            return DebridResolveResult(
                status = DebridResolveStatus.FAILURE,
                error = safeMsg
            )
        }
    }

    override suspend fun logout(tokenSet: DebridTokenSet) {
        // Cleaning local credentials in tokenStore is sufficient.
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
        @JvmField
        internal val sessionStates = ConcurrentHashMap<String, AllDebridSessionState>()
    }
}
