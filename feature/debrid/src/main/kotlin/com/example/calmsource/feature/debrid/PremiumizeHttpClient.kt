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
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
private data class PMResponse<T>(
    val status: String,
    val response: T? = null,
    val message: String? = null
)

@Serializable
private data class PMAccountInfo(
    @SerialName("customer_id") val customerId: String,
    @SerialName("premium_until") val premiumUntil: Long
)

@Serializable
private data class PMCacheResponse(
    val status: String,
    val response: List<Boolean> = emptyList(),
    val message: String? = null
)

@Serializable
private data class PMTransferCreateResponse(
    val status: String,
    val id: String? = null,
    val message: String? = null
)

@Serializable
private data class PMTransferListResponse(
    val status: String,
    val transfers: List<PMTransfer> = emptyList()
)

@Serializable
private data class PMTransfer(
    val id: String,
    val name: String,
    val status: String,
    val progress: Float = 0f,
    @SerialName("folder_id") val folderId: String? = null
)

@Serializable
private data class PMFolderListResponse(
    val status: String,
    val content: List<PMFolderItem> = emptyList()
)

@Serializable
private data class PMFolderItem(
    val name: String,
    val type: String, // "file" or "folder"
    val size: Long = 0,
    val link: String? = null,
    @SerialName("stream_link") val streamLink: String? = null
)

class PremiumizeHttpClient(
    private val client: HttpClient = NetworkClient.client
) : DebridProviderClient {

    override val providerType = DebridProviderType.PREMIUMIZE
    override val displayName = "Premiumize"
    override val capabilities = setOf(
        DebridCapability.ACCOUNT_STATUS,
        DebridCapability.CACHED_AVAILABILITY_CHECK,
        DebridCapability.LINK_RESOLVE,
        DebridCapability.API_KEY_AUTH
    )

    override suspend fun startAuth(): DebridAuthSession {
        return DebridAuthSession.ApiKey(
            id = "sess-pm-${UUID.randomUUID().toString().take(6)}",
            providerType = providerType,
            details = DebridApiKeySession(providerType)
        )
    }

    override suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession {
        // API key auth does not poll. Connection is established instantly with API key.
        return session
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        // Handled via direct API key entry in DebridRepository.addAccountWithApiKey.
        throw UnsupportedOperationException("PIN/Device polling not supported for Premiumize. Use direct API Key connection.")
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus {
        val apiKey = tokenSet.apiKey ?: throw IllegalArgumentException("Missing API key")
        val response = client.get("https://www.premiumize.me/api/account/info") {
            parameter("apikey", apiKey)
        }.body<PMResponse<PMAccountInfo>>()

        if (response.status != "success" || response.response == null) {
            throw IllegalStateException(response.message ?: "Failed to get Premiumize account status")
        }

        val info = response.response
        val premiumMs = info.premiumUntil * 1000L
        val remainingMs = premiumMs - System.currentTimeMillis()
        val daysRemaining = (remainingMs / (86400 * 1000L)).coerceAtLeast(0).toInt()
        val isPremium = remainingMs > 0

        return DebridAccountStatus(
            username = info.customerId,
            email = "",
            premiumDaysRemaining = daysRemaining,
            expirationDate = null,
            isPremium = isPremium
        )
    }

    override suspend fun checkCachedAvailability(
        hashes: List<String>,
        tokenSet: DebridTokenSet
    ): Map<String, DebridCachedAvailability> {
        if (hashes.isEmpty()) return emptyMap()
        val apiKey = tokenSet.apiKey ?: return emptyMap()

        val response = client.get("https://www.premiumize.me/api/cache/check") {
            parameter("apikey", apiKey)
            hashes.forEach { hash ->
                parameter("src[]", hash)
            }
        }.body<PMCacheResponse>()

        if (response.status != "success") {
            return emptyMap()
        }

        return hashes.mapIndexed { index, hash ->
            val isCached = response.response.getOrNull(index) == true
            DebridCachedAvailability(
                infoHash = hash,
                isCached = isCached,
                filesList = if (isCached) listOf("VideoFile.mkv") else emptyList() // Premiumize cache check doesn't return file lists directly
            )
        }.associateBy { it.infoHash }
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
                return DebridResolveResult(url = hostLink)
            }

            // Torrent/Magnet Resolution
            val magnet = request.magnetUrl ?: "magnet:?xt=urn:btih:${request.infoHash}"
            val createRes = client.submitForm(
                url = "https://www.premiumize.me/api/transfer/create",
                formParameters = Parameters.build {
                    append("src", magnet)
                }
            ) {
                parameter("apikey", apiKey)
            }.body<PMTransferCreateResponse>()

            if (createRes.status != "success" || createRes.id == null) {
                return DebridResolveResult(
                    status = DebridResolveStatus.FAILURE,
                    error = createRes.message ?: "Transfer creation failed"
                )
            }

            val transferId = createRes.id

            // Poll for transfer completion (finished/downloaded)
            var folderId: String? = null
            var attempts = 0
            while (attempts < 10) {
                val listRes = client.get("https://www.premiumize.me/api/transfer/list") {
                    parameter("apikey", apiKey)
                }.body<PMTransferListResponse>()

                val transfer = listRes.transfers.find { it.id == transferId }
                if (transfer != null) {
                    if (transfer.status == "finished" || transfer.status == "seeding") {
                        folderId = transfer.folderId
                        break
                    }
                } else {
                    // Transfer might have finished immediately and cleared
                    break
                }
                delay(1000)
                attempts++
            }

            if (folderId == null) {
                return DebridResolveResult(
                    status = DebridResolveStatus.UNAVAILABLE,
                    error = "Transfer did not complete in time"
                )
            }

            // List files in the finished transfer folder
            val folderRes = client.get("https://www.premiumize.me/api/folder/list") {
                parameter("id", folderId)
                parameter("apikey", apiKey)
            }.body<PMFolderListResponse>()

            if (folderRes.status != "success" || folderRes.content.isEmpty()) {
                return DebridResolveResult(
                    status = DebridResolveStatus.FAILURE,
                    error = "Folder is empty or inaccessible"
                )
            }

            // Filter for video files (or select the first file)
            val videoFiles = folderRes.content.filter {
                it.type == "file" && (it.name.contains(".mkv", ignoreCase = true) ||
                        it.name.contains(".mp4", ignoreCase = true) ||
                        it.name.contains(".avi", ignoreCase = true))
            }
            val targetFile = if (videoFiles.isNotEmpty()) {
                val selectedIndex = request.fileIndex?.coerceIn(0, videoFiles.lastIndex) ?: 0
                videoFiles[selectedIndex]
            } else {
                folderRes.content.firstOrNull { it.type == "file" }
            }

            val playbackUrl = targetFile?.streamLink ?: targetFile?.link
            if (playbackUrl != null) {
                return DebridResolveResult(url = playbackUrl)
            } else {
                return DebridResolveResult(
                    status = DebridResolveStatus.FAILURE,
                    error = "No streaming link generated by Premiumize"
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
}
