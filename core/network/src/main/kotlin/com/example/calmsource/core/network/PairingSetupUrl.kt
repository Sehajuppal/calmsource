package com.example.calmsource.core.network

import io.ktor.http.Url

data class PairingSetupParams(
    val pin: String,
    val publicKeyBase64: String,
    val relayBaseUrl: String,
)

object PairingSetupUrl {

    fun build(pin: String, publicKeyBase64: String, wsAuthUrl: String = BuildConfig.WS_AUTH_URL): String {
        val wsUrl = Url(wsAuthUrl)
        val scheme = if (wsUrl.protocol.name == "wss") "https" else "http"
        val port = wsUrl.port.takeIf { it > 0 } ?: wsUrl.protocol.defaultPort
        val encodedPin = java.net.URLEncoder.encode(pin, Charsets.UTF_8.name())
        val encodedKey = java.net.URLEncoder.encode(publicKeyBase64, Charsets.UTF_8.name())
        return "$scheme://${wsUrl.host}:$port/setup?pin=$encodedPin&key=$encodedKey"
    }

    fun parse(content: String): PairingSetupParams? {
        val trimmed = content.trim()
        if (trimmed.isBlank()) return null

        val uri = runCatching {
            android.net.Uri.parse(trimmed)
        }.getOrNull() ?: return null

        val pin = uri.getQueryParameter("pin")?.trim().orEmpty()
        val key = uri.getQueryParameter("key")?.trim().orEmpty()
        if (pin.length != 6 || !pin.all { it.isDigit() } || key.isBlank()) return null

        val host = uri.host ?: return null
        val scheme = uri.scheme ?: "https"
        val port = uri.port.takeIf { it > 0 } ?: if (scheme == "https") 443 else 80
        val relayBaseUrl = "$scheme://$host:$port"

        return PairingSetupParams(
            pin = pin,
            publicKeyBase64 = key,
            relayBaseUrl = relayBaseUrl,
        )
    }
}
