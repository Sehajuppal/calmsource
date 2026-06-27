/**
 * Token storage abstraction for debrid provider credentials.
 *
 * This interface defines the contract for securely persisting, retrieving, and deleting
 * OAuth tokens and API keys used by debrid providers. Implementations support both
 * provider-level and granular per-account, per-token-type operations.
 *
 * The production implementation ([EncryptedSecureTokenStore]) uses AndroidX Security
 * Crypto / EncryptedSharedPreferences backed by Android Keystore AES-GCM encryption.
 *
 * The test implementation ([FakeInMemorySecureTokenStore]) uses volatile in-memory
 * storage for development and unit testing.
 *
 * @see DebridTokenSet for the credential data structure
 * @see DebridRepository for where the token store is used
 */
package com.example.calmsource.feature.debrid

import com.example.calmsource.core.model.DebridTokenSet
import com.example.calmsource.core.model.DebridProviderType

/**
 * Contract for secure debrid credential storage.
 *
 * Provides both legacy provider-level token operations (backward-compatible)
 * and granular per-account, per-token-type operations for production use.
 */
interface SecureTokenStore {

    // --- Granular token operations ---

    /** Saves a single token value identified by provider, account, and token type. */
    fun saveToken(providerType: DebridProviderType, accountId: String, tokenType: String, value: String)

    /** Reads a single token value. Returns null if not found or on error. */
    fun readToken(providerType: DebridProviderType, accountId: String, tokenType: String): String?

    /** Deletes a single token value. No-op if not found. */
    fun deleteToken(providerType: DebridProviderType, accountId: String, tokenType: String)

    /** Removes all tokens for a specific account under a provider. */
    fun clearAccount(providerType: DebridProviderType, accountId: String)

    /** Removes all tokens for all accounts under a provider. */
    fun clearProvider(providerType: DebridProviderType)

    /** Removes all stored credentials for all providers. */
    fun clearAll()

    /** Checks if a specific token exists. */
    fun hasToken(providerType: DebridProviderType, accountId: String, tokenType: String): Boolean

    // --- Legacy provider-level operations (backward-compatible) ---

    /** Persists a [DebridTokenSet] for the given provider using a default account ID. */
    fun saveTokens(providerType: DebridProviderType, tokenSet: DebridTokenSet) {
        saveTokensForAccount(providerType, DEFAULT_ACCOUNT_ID, tokenSet)
    }

    /** Retrieves the stored [DebridTokenSet] for the given provider, or null if none exists. */
    fun getTokens(providerType: DebridProviderType): DebridTokenSet? {
        return getTokensForAccount(providerType, DEFAULT_ACCOUNT_ID)
    }

    /** Persists a [DebridTokenSet] for a specific account ID. */
    fun saveTokensForAccount(providerType: DebridProviderType, accountId: String, tokenSet: DebridTokenSet) {
        tokenSet.accessToken?.let { saveToken(providerType, accountId, "access_token", it) }
        tokenSet.refreshToken?.let { saveToken(providerType, accountId, "refresh_token", it) }
        if (tokenSet.expiresAt > 0) saveToken(providerType, accountId, "expires_at", tokenSet.expiresAt.toString())
        tokenSet.apiKey?.let { saveToken(providerType, accountId, "api_key", it) }
        tokenSet.clientId?.let { saveToken(providerType, accountId, "client_id", it) }
        tokenSet.clientSecret?.let { saveToken(providerType, accountId, "client_secret", it) }
    }

    /** Retrieves a [DebridTokenSet] for a specific account ID, or null if none exists. */
    fun getTokensForAccount(providerType: DebridProviderType, accountId: String): DebridTokenSet? {
        val accessToken = readToken(providerType, accountId, "access_token")
        val refreshToken = readToken(providerType, accountId, "refresh_token")
        val expiresAt = readToken(providerType, accountId, "expires_at")?.toLongOrNull() ?: 0L
        val apiKey = readToken(providerType, accountId, "api_key")
        val clientId = readToken(providerType, accountId, "client_id")
        val clientSecret = readToken(providerType, accountId, "client_secret")
        if (accessToken == null && refreshToken == null && apiKey == null && clientId == null && clientSecret == null) return null
        return DebridTokenSet(
            accessToken = accessToken,
            refreshToken = refreshToken,
            expiresAt = expiresAt,
            apiKey = apiKey,
            clientId = clientId,
            clientSecret = clientSecret
        )
    }

    /** Deletes the stored credentials for the given provider. No-op if none exists. */
    fun deleteTokens(providerType: DebridProviderType) {
        clearAccount(providerType, DEFAULT_ACCOUNT_ID)
    }

    /** Deletes stored credentials for a specific account ID. */
    fun deleteTokensForAccount(providerType: DebridProviderType, accountId: String) {
        clearAccount(providerType, accountId)
    }

    companion object {
        const val DEFAULT_ACCOUNT_ID = "default"
    }
}

/**
 * WARNING: This is a simulated, in-memory token store designed for development and testing.
 * It does NOT persist credentials securely to disk (uses volatile memory).
 * Production builds must use [EncryptedSecureTokenStore] instead.
 */
class FakeInMemorySecureTokenStore : SecureTokenStore {
    // Key format: "{providerType}:{accountId}:{tokenType}"
    private val store = java.util.concurrent.ConcurrentHashMap<String, String>()

    private fun key(providerType: DebridProviderType, accountId: String, tokenType: String): String =
        "${providerType.name}:${accountId}:$tokenType"

    override fun saveToken(providerType: DebridProviderType, accountId: String, tokenType: String, value: String) {
        store[key(providerType, accountId, tokenType)] = value
    }

    override fun readToken(providerType: DebridProviderType, accountId: String, tokenType: String): String? {
        return store[key(providerType, accountId, tokenType)]
    }

    override fun deleteToken(providerType: DebridProviderType, accountId: String, tokenType: String) {
        store.remove(key(providerType, accountId, tokenType))
    }

    override fun clearAccount(providerType: DebridProviderType, accountId: String) {
        val prefix = "${providerType.name}:${accountId}:"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    override fun clearProvider(providerType: DebridProviderType) {
        val prefix = "${providerType.name}:"
        store.keys.filter { it.startsWith(prefix) }.forEach { store.remove(it) }
    }

    override fun clearAll() {
        store.clear()
    }

    override fun hasToken(providerType: DebridProviderType, accountId: String, tokenType: String): Boolean {
        return store.containsKey(key(providerType, accountId, tokenType))
    }
}
