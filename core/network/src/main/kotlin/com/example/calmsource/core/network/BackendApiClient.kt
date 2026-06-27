package com.example.calmsource.core.network

import com.example.calmsource.core.model.BackendMetaResponse
import com.example.calmsource.core.model.BackendSearchResult
import com.example.calmsource.core.network.BuildConfig
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.use

object BackendApiClient {
    @Volatile
    var baseUrl: String = BuildConfig.BACKEND_BASE_URL.takeIf { it.isNotEmpty() } ?: ""

    fun canAttemptRequest(): Boolean {
        if (baseUrl.isBlank()) return false
        return !isPrivateOrLoopbackUrl(baseUrl)
    }

    internal fun isPrivateOrLoopbackUrl(rawUrl: String): Boolean {
        val host = runCatching {
            java.net.URI(rawUrl.trim()).host?.trim()?.lowercase()
        }.getOrNull().orEmpty()
        if (host.isEmpty()) return true
        if (host == "localhost" || host == "127.0.0.1" || host == "::1" || host == "10.0.2.2") {
            return true
        }
        if (host.endsWith(".local")) return true
        val ipv4 = host.split('.').mapNotNull { it.toIntOrNull() }
        if (ipv4.size == 4) {
            val (a, b) = ipv4[0] to ipv4[1]
            if (a == 10) return true
            if (a == 172 && b in 16..31) return true
            if (a == 192 && b == 168) return true
            if (a == 127) return true
        }
        return false
    }

    suspend fun getMeta(type: String, id: String): BackendMetaResponse? {
        if (!canAttemptRequest()) return null
        return try {
            NetworkClient.client.get("$baseUrl/meta/$type/$id.json").use { response ->
                if (response.status.value == 200) {
                    response.body<BackendMetaResponse>()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSimilar(type: String, id: String): List<BackendSearchResult>? {
        if (!canAttemptRequest()) return null
        return try {
            NetworkClient.client.get("$baseUrl/meta/$type/$id/similar.json").use { response ->
                if (response.status.value == 200) {
                    response.body<List<BackendSearchResult>>()
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun search(query: String, limit: Int = 10): List<BackendSearchResult> {
        if (!canAttemptRequest()) return emptyList()
        return try {
            NetworkClient.client.get("$baseUrl/meta/search") {
                parameter("q", query)
                parameter("limit", limit)
            }.use { response ->
                if (response.status.value == 200) {
                    response.body<List<BackendSearchResult>>()
                } else {
                    emptyList()
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }
}
