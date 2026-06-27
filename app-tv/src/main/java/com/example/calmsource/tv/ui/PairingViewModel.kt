package com.example.calmsource.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.model.AuthCredentials
import com.example.calmsource.core.model.DebridProviderType
import com.example.calmsource.core.network.AuthCryptoManager
import com.example.calmsource.core.network.AuthSyncRepository
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.iptv.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject
import com.example.calmsource.core.data.AuthPreferencesManager

sealed interface PairingState {
    object Idle : PairingState
    object Connecting : PairingState
    data class ShowPin(val pin: String, val publicKey: String) : PairingState
    object Decrypting : PairingState
    object Success : PairingState
    data class Error(val message: String) : PairingState
}

@HiltViewModel
class PairingViewModel @Inject constructor(
    private val authPreferencesManager: AuthPreferencesManager
) : ViewModel() {

    private val _state = MutableStateFlow<PairingState>(PairingState.Idle)
    val state: StateFlow<PairingState> = _state.asStateFlow()

    internal var cryptoManager: AuthCryptoManager? = null
    private var pairingJob: Job? = null

    fun startPairing() {
        pairingJob?.cancel()
        _state.value = PairingState.Connecting

        pairingJob = viewModelScope.launch {
            // Instantiate AuthCryptoManager which deletes any existing key and generates a new ephemeral RSA-2048 keypair
            val manager = try {
                withContext(Dispatchers.Default) {
                    AuthCryptoManager()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = PairingState.Error(com.example.calmsource.core.network.UrlRedactor.redactErrorMessage("Failed to initialize cryptography: ${e.message}"))
                return@launch
            }
            cryptoManager = manager

            val publicKey = try {
                withContext(Dispatchers.Default) {
                    manager.getPublicKeyBase64()
                }
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.value = PairingState.Error(com.example.calmsource.core.network.UrlRedactor.redactErrorMessage("Failed to generate public key: ${e.message}"))
                return@launch
            }

            // Close any existing connection first
            AuthSyncRepository.close()

            // Observe SyncState from repository
            launch {
                AuthSyncRepository.syncState.collect { syncState ->
                    try {
                        when (syncState) {
                            is AuthSyncRepository.SyncState.Idle -> {
                                // No action required
                            }
                            is AuthSyncRepository.SyncState.Connecting -> {
                                _state.value = PairingState.Connecting
                            }
                            is AuthSyncRepository.SyncState.SessionCreated -> {
                                _state.value = PairingState.ShowPin(syncState.pin, publicKey)
                            }
                            is AuthSyncRepository.SyncState.Decrypting -> {
                                _state.value = PairingState.Decrypting
                                val activeManager = cryptoManager
                                if (activeManager == null) {
                                    _state.value = PairingState.Error("Crypto manager not ready")
                                    return@collect
                                }
                                try {
                                    // Decrypt payload on Dispatchers.Default
                                    val decryptedJson = withContext(Dispatchers.Default) {
                                        activeManager.decrypt(syncState.ciphertext)
                                    }
                                    val credentials = withContext(Dispatchers.Default) {
                                        Json {
                                            ignoreUnknownKeys = true
                                            isLenient = true
                                        }.decodeFromString<AuthCredentials>(decryptedJson)
                                    }

                                    // Persist credentials off the main thread; Default avoids nested runBlocking deadlocks in unit tests.
                                    withContext(Dispatchers.Default) {
                                        val token = credentials.debridToken ?: credentials.realDebridToken
                                        if (!token.isNullOrBlank()) {
                                            DebridRepository.addAccountWithApiKey(
                                                DebridProviderType.REAL_DEBRID,
                                                "RealDebrid Link",
                                                "link@calmsource.tv",
                                                token
                                            )
                                        }

                                        val url = credentials.xtreamUrl ?: credentials.xtreamServerUrl
                                        val username = credentials.username ?: credentials.xtreamUsername
                                        val password = credentials.password ?: credentials.xtreamPassword
                                        if (!url.isNullOrBlank() && !username.isNullOrBlank() && !password.isNullOrBlank()) {
                                            val result = XtreamRepository.addXtreamProvider(
                                                name = "Xtream Live",
                                                serverUrl = url,
                                                username = username,
                                                password = password
                                            )
                                            result.getOrThrow()
                                        }

                                        credentials.installedExtensions?.forEach { extensionUrl ->
                                            if (!extensionUrl.isNullOrBlank()) {
                                                try {
                                                    val previewResult = com.example.calmsource.feature.extensions.ExtensionRepository.previewExtension(extensionUrl)
                                                    val manifest = previewResult.manifest
                                                    if (previewResult.isSuccess && manifest != null) {
                                                        com.example.calmsource.feature.extensions.ExtensionRepository.confirmInstall(manifest, extensionUrl)
                                                    }
                                                } catch (e: Throwable) {
                                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                                    // catch and proceed so one bad URL does not block/fail others
                                                }
                                            }
                                        }
                                    }

                                    _state.value = PairingState.Success
                                } catch (e: Throwable) {
                                    if (e is kotlinx.coroutines.CancellationException) throw e
                                    val detail = e.message?.takeIf { it.isNotBlank() }
                                        ?: e.cause?.message?.takeIf { it.isNotBlank() }
                                        ?: e::class.simpleName
                                    _state.value = PairingState.Error(com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(detail ?: "Failed to save credentials"))
                                } finally {
                                    AuthSyncRepository.close()
                                }
                            }
                            is AuthSyncRepository.SyncState.Error -> {
                                when (_state.value) {
                                    is PairingState.Success,
                                    is PairingState.ShowPin,
                                    is PairingState.Decrypting,
                                    is PairingState.Error -> Unit
                                    else -> _state.value = PairingState.Error(com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(syncState.message))
                                }
                            }
                            is AuthSyncRepository.SyncState.Success -> {
                                _state.value = PairingState.Success
                            }
                        }
                    } catch (e: Throwable) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                        val detail = e.message?.takeIf { it.isNotBlank() } ?: e::class.simpleName
                        _state.value = PairingState.Error(com.example.calmsource.core.network.UrlRedactor.redactErrorMessage(detail ?: "Sync session failed"))
                    }
                }
            }

            // Connect WebSocket client
            AuthSyncRepository.connect(manager, this)
        }
    }

    fun cancelPairing() {
        pairingJob?.cancel()
        pairingJob = null
        cryptoManager = null
        AuthSyncRepository.close()
        _state.value = PairingState.Idle
    }

    fun skipAuthentication() {
        viewModelScope.launch {
            authPreferencesManager.setAuthSkipped(true)
            _state.value = PairingState.Success
        }
    }

    override fun onCleared() {
        super.onCleared()
        cancelPairing()
        cryptoManager = null
    }
}
