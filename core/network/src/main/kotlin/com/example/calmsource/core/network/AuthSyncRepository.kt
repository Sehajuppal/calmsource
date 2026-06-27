@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
package com.example.calmsource.core.network

import com.example.calmsource.core.network.BuildConfig
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.websocket.WebSockets
import io.ktor.client.plugins.websocket.webSocketSession
import io.ktor.client.plugins.websocket.webSocket
import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.http.HttpMethod
import io.ktor.http.Url
import io.ktor.websocket.Frame
import io.ktor.websocket.readText
import io.ktor.websocket.close
import io.ktor.websocket.send
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.util.concurrent.atomic.AtomicBoolean

@Serializable
data class AuthWebSocketMessage(
    val pin: String? = null,
    val payload: String? = null
)

object AuthSyncRepository {

    sealed interface SyncState {
        object Idle : SyncState
        object Connecting : SyncState
        data class SessionCreated(val pin: String) : SyncState
        data class Decrypting(val ciphertext: String) : SyncState
        object Success : SyncState
        data class Error(val message: String) : SyncState
    }

    private val _syncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val syncState: StateFlow<SyncState> = _syncState.asStateFlow()

    private var httpClient: HttpClient? = null
    private var webSocketSession: DefaultClientWebSocketSession? = null
    private var connectionJob: Job? = null
    private val isConnecting = AtomicBoolean(false)

    @Volatile
    var wsUrl: String = BuildConfig.WS_AUTH_URL

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    fun connect(cryptoManager: AuthCryptoManager, scope: CoroutineScope) {
        close()
        if (!isConnecting.compareAndSet(false, true)) return

        _syncState.value = SyncState.Connecting

        connectionJob = scope.launch(Dispatchers.IO) {
            var localClient: HttpClient? = null
            var localSession: DefaultClientWebSocketSession? = null
            try {
                val client = HttpClient(OkHttp) {
                    install(WebSockets)
                    install(HttpTimeout) {
                        connectTimeoutMillis = 15_000L
                        requestTimeoutMillis = 15_000L
                        socketTimeoutMillis = 15_000L
                    }
                    engine {
                        config {
                            dns(NetworkClient.safeDns)
                        }
                    }
                }
                localClient = client
                httpClient = client

                val parsedUrl = Url(wsUrl)
                val targetPath = parsedUrl.encodedPath.removeSuffix("/")
                val targetPort = parsedUrl.port.takeIf { it > 0 } ?: parsedUrl.protocol.defaultPort

                client.webSocket(
                    method = HttpMethod.Get,
                    host = parsedUrl.host,
                    port = targetPort,
                    path = targetPath
                ) {
                    val session = this
                    localSession = session
                    webSocketSession = session

                    // Send TV public key to the WebSocket server upon connection
                    val publicKeyBase64 = cryptoManager.getPublicKeyBase64()
                    session.send(Frame.Text("{\"publicKey\":\"$publicKeyBase64\"}"))

                    session.incoming.consumeEach { frame ->
                        if (frame is Frame.Text) {
                            val text = frame.readText()
                            handleIncomingMessage(text)
                        }
                    }
                }
                
                // If loop terminates without exception, connection was closed
                _syncState.value = SyncState.Idle
            } catch (e: Throwable) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                if (isConnecting.get()) {
                    _syncState.value = SyncState.Error(UrlRedactor.redactErrorMessage(e.message ?: "Connection error"))
                }
            } finally {
                try {
                    localSession?.close()
                } catch (_: Exception) {}
                try {
                    localClient?.close()
                } catch (_: Exception) {}

                if (webSocketSession === localSession) {
                    webSocketSession = null
                }
                if (httpClient === localClient) {
                    httpClient = null
                }
                isConnecting.set(false)
            }
        }
    }

    fun close() {
        isConnecting.set(false)
        connectionJob?.cancel()
        connectionJob = null
        closeSession()
        _syncState.value = SyncState.Idle
    }

    private fun closeSession() {
        webSocketSession = null
        try {
            httpClient?.close()
        } catch (_: Exception) {}
        httpClient = null
    }

    fun handleIncomingMessage(text: String) {
        try {
            val msg = json.decodeFromString<AuthWebSocketMessage>(text)
            if (msg.pin != null) {
                _syncState.value = SyncState.SessionCreated(msg.pin)
            }
            if (msg.payload != null) {
                _syncState.value = SyncState.Decrypting(msg.payload)
            }
        } catch (e: Throwable) {
            val redactedText = UrlRedactor.redactErrorMessage(text)
            _syncState.value = SyncState.Error(
                UrlRedactor.redactErrorMessage("Failed to process message: ${e.message} (input: $redactedText)")
            )
        }
    }
}
