/**
 * Abstraction layer for debrid service provider APIs.
 *
 * Each supported debrid service (Real-Debrid, AllDebrid, Premiumize) implements this
 * interface to provide a uniform API for authentication, account management, cached
 * availability checking, and link resolution.
 *
 * Implementations are registered in [DebridRepository.clients] and looked up by
 * [DebridProviderType]. Production implementations call the provider APIs; test
 * fakes live separately in [FakeDebridProviderClients.kt].
 */
package com.example.calmsource.feature.debrid

import com.example.calmsource.core.model.*

interface DebridProviderClient {
    /** The debrid service this client communicates with. */
    val providerType: DebridProviderType

    /** Human-readable display name for the provider (e.g. "Real-Debrid"). */
    val displayName: String

    /** Set of capabilities this provider supports. */
    val capabilities: Set<DebridCapability>

    /** Initiates an authentication session appropriate for this provider's auth method. */
    suspend fun startAuth(): DebridAuthSession

    /** Polls the provider to check if the user has completed authorization. */
    suspend fun pollAuth(session: DebridAuthSession): DebridAuthSession

    /** Exchanges a completed auth session for a [DebridTokenSet] containing credentials. */
    suspend fun completeAuth(session: DebridAuthSession): DebridTokenSet

    /** Retrieves the current account status (username, premium days, etc.) from the provider. */
    suspend fun getAccountStatus(tokenSet: DebridTokenSet): DebridAccountStatus

    /** Checks which of the given torrent hashes are instantly available (cached) on the service. */
    suspend fun checkCachedAvailability(hashes: List<String>, tokenSet: DebridTokenSet): Map<String, DebridCachedAvailability>

    /** Resolves a torrent hash or hoster link into a direct playback URL. */
    suspend fun resolveLink(request: DebridResolveRequest, tokenSet: DebridTokenSet): DebridResolveResult

    /** Revokes the current session/token, logging the user out of the provider. */
    suspend fun logout(tokenSet: DebridTokenSet)

    /** Validates whether the given credentials are still accepted by the provider. */
    suspend fun validateCredentials(tokenSet: DebridTokenSet): Boolean

    /** Returns the current operational health of the provider's API. */
    suspend fun getHealth(): DebridAccountHealth
}
