package com.example.calmsource.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.AuthPreferencesManager
import com.example.calmsource.core.model.AuthCredentials
import com.example.calmsource.core.network.AuthCryptoManager
import com.example.calmsource.core.network.AuthRelayRepository
import com.example.calmsource.core.network.AuthRequest
import com.example.calmsource.core.network.CloudAuthRepository
import com.example.calmsource.core.network.PairingSetupParams
import com.example.calmsource.core.network.PairingSetupUrl
import com.example.calmsource.core.network.UrlRedactor
import com.example.calmsource.core.data.CloudAuthTokenStore
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.extensions.ExtensionRepository
import com.example.calmsource.core.model.IPTVProviderType
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.feature.iptv.XtreamRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import javax.inject.Inject

sealed interface LoginUiState {
    data class Account(
        val mode: AccountMode = AccountMode.SignIn,
        val email: String = "",
        val password: String = "",
        val loading: Boolean = false,
        val error: String? = null,
    ) : LoginUiState

    data class TvPair(
        val step: TvPairStep = TvPairStep.Scan,
        val pairing: PairingSetupParams? = null,
        val xtreamUrl: String = "",
        val xtreamUsername: String = "",
        val xtreamPassword: String = "",
        val debridToken: String = "",
        val loading: Boolean = false,
        val error: String? = null,
        val success: Boolean = false,
    ) : LoginUiState
}

enum class AccountMode { SignIn, CreateAccount }

enum class TvPairStep { Scan, Confirm, Done }

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val tokenStore: CloudAuthTokenStore,
    private val authPreferencesManager: AuthPreferencesManager,
) : ViewModel() {

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _accountState = MutableStateFlow(LoginUiState.Account())
    val accountState: StateFlow<LoginUiState.Account> = _accountState.asStateFlow()

    private val _tvPairState = MutableStateFlow(LoginUiState.TvPair())
    val tvPairState: StateFlow<LoginUiState.TvPair> = _tvPairState.asStateFlow()

    fun selectTab(index: Int) {
        _selectedTab.value = index
        clearErrors()
    }

    fun updateEmail(value: String) {
        _accountState.update { it.copy(email = value) }
    }

    fun updatePassword(value: String) {
        _accountState.update { it.copy(password = value) }
    }

    fun setAccountMode(mode: AccountMode) {
        _accountState.update { it.copy(mode = mode, error = null) }
    }

    fun signIn() {
        val state = _accountState.value
        viewModelScope.launch {
            _accountState.update { it.copy(loading = true, error = null) }
            try {
                if (state.email.isBlank() || state.password.isBlank()) {
                    throw IllegalArgumentException("Enter your email and password")
                }
                val response = if (state.mode == AccountMode.SignIn) {
                    CloudAuthRepository.login(AuthRequest(state.email, state.password))
                } else {
                    CloudAuthRepository.register(AuthRequest(state.email, state.password))
                }
                tokenStore.setToken(response.token)
                _accountState.update { it.copy(loading = false) }
            } catch (e: Throwable) {
                _accountState.update {
                    it.copy(
                        loading = false,
                        error = UrlRedactor.redactErrorMessage(e.message ?: "Sign in failed"),
                    )
                }
            }
        }
    }

    fun skipSignIn() {
        viewModelScope.launch {
            authPreferencesManager.setAuthSkipped(true)
        }
    }

    fun onQrScanned(rawContent: String) {
        val params = PairingSetupUrl.parse(rawContent)
        if (params == null) {
            _tvPairState.update { it.copy(error = "That QR code is not a CalmSource TV sign-in code.") }
            return
        }
        viewModelScope.launch {
            val prefilled = loadLocalCredentials()
            _tvPairState.update {
                it.copy(
                    step = TvPairStep.Confirm,
                    pairing = params,
                    xtreamUrl = prefilled?.xtreamUrl.orEmpty(),
                    xtreamUsername = prefilled?.username.orEmpty(),
                    xtreamPassword = prefilled?.password.orEmpty(),
                    debridToken = prefilled?.debridToken.orEmpty(),
                    error = null,
                )
            }
        }
    }

    fun updateTvPairField(field: TvPairField, value: String) {
        _tvPairState.update { state ->
            when (field) {
                TvPairField.Url -> state.copy(xtreamUrl = value)
                TvPairField.Username -> state.copy(xtreamUsername = value)
                TvPairField.Password -> state.copy(xtreamPassword = value)
                TvPairField.Debrid -> state.copy(debridToken = value)
            }
        }
    }

    fun resetTvPairScan() {
        _tvPairState.value = LoginUiState.TvPair()
    }

    fun sendCredentialsToTv() {
        val state = _tvPairState.value
        val pairing = state.pairing ?: return
        viewModelScope.launch {
            _tvPairState.update { it.copy(loading = true, error = null) }
            try {
                val credentials = AuthCredentials(
                    xtreamUrl = state.xtreamUrl.trim().ifBlank { null },
                    username = state.xtreamUsername.trim().ifBlank { null },
                    password = state.xtreamPassword.trim().ifBlank { null },
                    debridToken = state.debridToken.trim().ifBlank { null },
                    installedExtensions = loadInstalledExtensionUrls(),
                )
                val hasPayload = !credentials.xtreamUrl.isNullOrBlank() ||
                    !credentials.debridToken.isNullOrBlank() ||
                    !credentials.installedExtensions.isNullOrEmpty()
                if (!hasPayload) {
                    throw IllegalArgumentException("Add at least one source to send to your TV")
                }

                val plaintext = Json {
                    ignoreUnknownKeys = true
                    encodeDefaults = false
                }.encodeToString(AuthCredentials.serializer(), credentials)

                val encrypted = withContext(Dispatchers.Default) {
                    AuthCryptoManager().encryptForPublicKey(pairing.publicKeyBase64, plaintext)
                }

                AuthRelayRepository.relayEncryptedPayload(
                    pin = pairing.pin,
                    encryptedPayloadBase64 = encrypted,
                    baseUrlOverride = pairing.relayBaseUrl,
                )

                _tvPairState.update {
                    it.copy(loading = false, step = TvPairStep.Done, success = true)
                }
            } catch (e: Throwable) {
                _tvPairState.update {
                    it.copy(
                        loading = false,
                        error = UrlRedactor.redactErrorMessage(e.message ?: "Could not reach your TV"),
                    )
                }
            }
        }
    }

    private suspend fun loadLocalCredentials(): AuthCredentials? = withContext(Dispatchers.Default) {
        val provider = IPTVRepository.providers.value.firstOrNull { it.type == IPTVProviderType.XTREAM }
        val username = provider?.username?.trim().orEmpty()
        val password = if (provider != null && username.isNotBlank()) {
            XtreamRepository.getPassword(provider.id, username)
        } else {
            null
        }
        val debridAccount = DebridRepository.accounts.value.firstOrNull()
        val tokenSet = debridAccount?.let { DebridRepository.getAccountWithTokens(it.id)?.tokenSet }
        val debrid = tokenSet?.accessToken ?: tokenSet?.apiKey
        if (provider == null && debrid.isNullOrBlank()) return@withContext null
        AuthCredentials(
            xtreamUrl = provider?.serverUrl?.takeIf { it.isNotBlank() },
            username = username.takeIf { it.isNotBlank() },
            password = password,
            debridToken = debrid,
        )
    }

    private suspend fun loadInstalledExtensionUrls(): List<String>? = withContext(Dispatchers.Default) {
        val urls = ExtensionRepository.extensions.value
            .mapNotNull { extension -> extension.url.takeIf { it.isNotBlank() } }
            .distinct()
        urls.takeIf { it.isNotEmpty() }
    }

    private fun clearErrors() {
        _accountState.update { it.copy(error = null) }
        _tvPairState.update { it.copy(error = null) }
    }
}

enum class TvPairField {
    Url, Username, Password, Debrid,
}
