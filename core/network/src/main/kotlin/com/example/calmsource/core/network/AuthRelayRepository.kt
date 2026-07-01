package com.example.calmsource.core.network

import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.client.statement.bodyAsText
import io.ktor.client.statement.use
import kotlinx.serialization.Serializable

@Serializable
data class RelayRequest(
    val pin: String,
    val payload: String,
)

@Serializable
data class RelayResponse(
    val success: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)

object AuthRelayRepository {

    @Volatile
    var relayUrl: String = BuildConfig.RELAY_API_URL

    suspend fun relayEncryptedPayload(
        pin: String,
        encryptedPayloadBase64: String,
        baseUrlOverride: String? = null,
    ) {
        val endpoint = resolveRelayEndpoint(baseUrlOverride)
        val response = NetworkClient.client.post(endpoint) {
            contentType(ContentType.Application.Json)
            setBody(RelayRequest(pin = pin, payload = encryptedPayloadBase64))
        }.use { httpResponse ->
            if (httpResponse.status.value !in 200..299) {
                val body = runCatching { httpResponse.bodyAsText() }.getOrDefault("")
                throw Exception(
                    UrlRedactor.redactErrorMessage(
                        "Relay failed (${httpResponse.status.value})${if (body.isNotBlank()) ": $body" else ""}"
                    )
                )
            }
            httpResponse
        }
        response
    }

    private fun resolveRelayEndpoint(baseUrlOverride: String?): String {
        val base = baseUrlOverride?.trim()?.trimEnd('/')?.takeIf { it.isNotBlank() }
        return if (base != null) {
            "$base/api/relay"
        } else {
            relayUrl
        }
    }
}
