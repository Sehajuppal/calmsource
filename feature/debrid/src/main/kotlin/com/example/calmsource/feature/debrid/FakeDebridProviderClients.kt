package com.example.calmsource.feature.debrid

import com.example.calmsource.core.model.*
import kotlinx.coroutines.delay
import java.util.UUID

class RealDebridFakeClient : DebridProviderClient {
    override val providerType = DebridProviderType.REAL_DEBRID
    override val displayName = "Real-Debrid"
    override val capabilities = setOf(
        DebridCapability.ACCOUNT_STATUS,
        DebridCapability.CACHED_AVAILABILITY_CHECK,
        DebridCapability.LINK_RESOLVE,
        DebridCapability.DEVICE_CODE_AUTH
    )

    private var pollCount = 0

    override suspend fun startAuth(): DebridAuthSession {
        pollCount = 0
        val sessionDetails = DebridDeviceCodeSession(
            userCode = "RD-XM4K",
            deviceCode = "rd_device_code_${UUID.randomUUID()}",
            verificationUrl = "https://real-debrid.com/device",
            intervalSeconds = 5,
            expiresInSeconds = 300
        )
        return DebridAuthSession.DeviceCode(
            id = "sess-rd-${UUID.randomUUID().toString().take(6)}",
            providerType = providerType,
            details = sessionDetails
        )
    }

    override suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession {
        delay(100)
        pollCount++
        return session
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        if (pollCount < 1) {
            throw IllegalStateException("Authorization not yet completed by user")
        }
        return DebridTokenSet(
            accessToken = "RD_ACCESS_TOKEN_${UUID.randomUUID().toString().take(8)}",
            refreshToken = "RD_REFRESH_TOKEN_${UUID.randomUUID().toString().take(8)}",
            expiresAt = System.currentTimeMillis() + 3600 * 1000
        )
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus {
        return DebridAccountStatus(
            username = "RDUser_Premium",
            email = "rd_user@gmail.com",
            premiumDaysRemaining = 124,
            expirationDate = "2026-10-07",
            isPremium = true
        )
    }

    override suspend fun checkCachedAvailability(hashes: List<String>, tokenSet: DebridTokenSet): Map<String, DebridCachedAvailability> {
        return hashes.associateWith { hash ->
            val isCached = hash.equals("spiderman-4k-hash", ignoreCase = true) || hash.equals("inception-debrid-hash", ignoreCase = true) || hash.equals("interstellar-debrid-hash", ignoreCase = true)
            DebridCachedAvailability(
                infoHash = hash,
                isCached = isCached,
                filesList = if (isCached) listOf("movie.mkv", "subs.srt") else emptyList()
            )
        }
    }

    override suspend fun resolveLink(request: DebridResolveRequest, tokenSet: DebridTokenSet): DebridResolveResult {
        return DebridResolveResult(
            url = "https://real-debrid.com/d/unrestricted-stream-link.mkv"
        )
    }

    override suspend fun logout(tokenSet: DebridTokenSet) {}

    override suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean {
        return tokenSet.accessToken?.startsWith("RD_ACCESS_TOKEN_") == true
    }

    override suspend fun getHealth(): DebridAccountHealth = DebridAccountHealth.HEALTHY
}

class AllDebridFakeClient : DebridProviderClient {
    override val providerType = DebridProviderType.ALL_DEBRID
    override val displayName = "AllDebrid"
    override val capabilities = setOf(
        DebridCapability.ACCOUNT_STATUS,
        DebridCapability.CACHED_AVAILABILITY_CHECK,
        DebridCapability.LINK_RESOLVE,
        DebridCapability.PIN_AUTH
    )

    private var pollCount = 0

    override suspend fun startAuth(): DebridAuthSession {
        pollCount = 0
        val sessionDetails = DebridPinSession(
            pinUrl = "https://alldebrid.com/pin",
            pinCode = "AD-998A"
        )
        return DebridAuthSession.Pin(
            id = "sess-ad-${UUID.randomUUID().toString().take(6)}",
            providerType = providerType,
            details = sessionDetails
        )
    }

    override suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession {
        delay(100)
        pollCount++
        return session
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        if (pollCount < 1) {
            throw IllegalStateException("PIN not yet authorized")
        }
        return DebridTokenSet(
            apiKey = "AD_API_KEY_${UUID.randomUUID().toString().take(8)}"
        )
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus {
        return DebridAccountStatus(
            username = "ADUser_Calm",
            email = "ad_user@gmail.com",
            premiumDaysRemaining = 45,
            expirationDate = "2026-07-20",
            isPremium = true
        )
    }

    override suspend fun checkCachedAvailability(hashes: List<String>, tokenSet: DebridTokenSet): Map<String, DebridCachedAvailability> {
        return hashes.associateWith { hash ->
            DebridCachedAvailability(infoHash = hash, isCached = false)
        }
    }

    override suspend fun resolveLink(request: DebridResolveRequest, tokenSet: DebridTokenSet): DebridResolveResult {
        return DebridResolveResult(url = "https://api.alldebrid.com/v4/link/unlock-mocked.mp4")
    }

    override suspend fun logout(tokenSet: DebridTokenSet) {}

    override suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean {
        return tokenSet.apiKey?.startsWith("AD_API_KEY_") == true
    }

    override suspend fun getHealth(): DebridAccountHealth = DebridAccountHealth.HEALTHY
}

class PremiumizeFakeClient : DebridProviderClient {
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
        return session
    }

    override suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet {
        throw UnsupportedOperationException("Premiumize uses API Key authentication directly. Call startAuth or validateCredentials.")
    }

    override suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus {
        return DebridAccountStatus(
            username = "PMUser_Cloud",
            email = "pm_user@gmail.com",
            premiumDaysRemaining = 12,
            expirationDate = "2026-06-17",
            isPremium = true
        )
    }

    override suspend fun checkCachedAvailability(hashes: List<String>, tokenSet: DebridTokenSet): Map<String, DebridCachedAvailability> {
        return hashes.associateWith { hash ->
            DebridCachedAvailability(infoHash = hash, isCached = false)
        }
    }

    override suspend fun resolveLink(request: DebridResolveRequest, tokenSet: DebridTokenSet): DebridResolveResult {
        return DebridResolveResult(url = "https://premiumize.me/transfer/directdl-mocked.mp4")
    }

    override suspend fun logout(tokenSet: DebridTokenSet) {}

    override suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean {
        return tokenSet.apiKey?.startsWith("PM_API_KEY_") == true
    }

    override suspend fun getHealth(): DebridAccountHealth = DebridAccountHealth.HEALTHY
}
