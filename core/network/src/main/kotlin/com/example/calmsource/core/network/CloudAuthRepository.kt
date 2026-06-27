package com.example.calmsource.core.network

import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.request.header
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.HttpHeaders
import io.ktor.client.statement.use
import kotlinx.serialization.Serializable

@Serializable
data class AuthRequest(
    val email: String,
    val password: String
)

@Serializable
data class AuthResponse(
    val token: String
)

@Serializable
data class VaultFetchResponse(
    val vault_ciphertext: String?
)

@Serializable
data class VaultUpdateRequest(
    val vault_ciphertext: String
)

@Serializable
data class VaultUpdateResponse(
    val success: Boolean,
    val message: String? = null
)

object CloudAuthRepository {

    @Volatile
    var baseUrl: String = BuildConfig.BACKEND_BASE_URL.takeIf { it.isNotEmpty() } ?: ""

    @Volatile
    var mockFetchVault: (suspend (String) -> VaultFetchResponse)? = null
    @Volatile
    var mockUpdateVault: (suspend (String, VaultUpdateRequest) -> VaultUpdateResponse)? = null

    suspend fun register(request: AuthRequest): AuthResponse {
        return NetworkClient.client.post("$baseUrl/api/auth/register") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.use { response ->
            if (response.status.value in 200..299) {
                response.body<AuthResponse>()
            } else {
                throw Exception("Registration failed with status ${response.status.value}")
            }
        }
    }

    suspend fun login(request: AuthRequest): AuthResponse {
        return NetworkClient.client.post("$baseUrl/api/auth/login") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }.use { response ->
            if (response.status.value in 200..299) {
                response.body<AuthResponse>()
            } else {
                throw Exception("Login failed with status ${response.status.value}")
            }
        }
    }

    suspend fun fetchVault(token: String): VaultFetchResponse {
        val mock = mockFetchVault
        if (mock != null) return mock(token)
        return NetworkClient.client.get("$baseUrl/api/vault/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
        }.use { response ->
            if (response.status.value in 200..299) {
                response.body<VaultFetchResponse>()
            } else {
                throw Exception("Fetch vault failed with status ${response.status.value}")
            }
        }
    }

    suspend fun updateVault(token: String, request: VaultUpdateRequest): VaultUpdateResponse {
        val mock = mockUpdateVault
        if (mock != null) return mock(token, request)
        return NetworkClient.client.post("$baseUrl/api/vault/sync") {
            header(HttpHeaders.Authorization, "Bearer $token")
            contentType(ContentType.Application.Json)
            setBody(request)
        }.use { response ->
            if (response.status.value in 200..299) {
                response.body<VaultUpdateResponse>()
            } else {
                throw Exception("Update vault failed with status ${response.status.value}")
            }
        }
    }
}
