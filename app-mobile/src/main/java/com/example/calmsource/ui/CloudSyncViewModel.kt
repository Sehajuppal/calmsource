package com.example.calmsource.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.CloudAuthTokenStore
import com.example.calmsource.core.data.sync.VaultSyncManager
import com.example.calmsource.core.network.AuthRequest
import com.example.calmsource.core.network.CloudAuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CloudSyncViewModel @Inject constructor(
    private val tokenStore: CloudAuthTokenStore,
    private val vaultSyncManager: VaultSyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        CloudSyncUiState(
            authStatus = tokenStore.getToken() != null
        )
    )
    val uiState: StateFlow<CloudSyncUiState> = _uiState.asStateFlow()

    fun getToken(): String? = tokenStore.getToken()


    fun login(email: String, javaPasswordState: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, successMessage = null) }
            try {
                if (email.isBlank() || javaPasswordState.isBlank()) {
                    throw IllegalArgumentException("Email and password cannot be empty")
                }
                val response = CloudAuthRepository.login(AuthRequest(email, javaPasswordState))
                tokenStore.setToken(response.token)
                _uiState.update {
                    it.copy(
                        loading = false,
                        authStatus = true,
                        successMessage = "Logged in successfully!"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Login failed"
                    )
                }
            }
        }
    }

    fun register(email: String, javaPasswordState: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, successMessage = null) }
            try {
                if (email.isBlank() || javaPasswordState.isBlank()) {
                    throw IllegalArgumentException("Email and password cannot be empty")
                }
                val response = CloudAuthRepository.register(AuthRequest(email, javaPasswordState))
                tokenStore.setToken(response.token)
                _uiState.update {
                    it.copy(
                        loading = false,
                        authStatus = true,
                        successMessage = "Registered and logged in successfully!"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Registration failed"
                    )
                }
            }
        }
    }

    fun backup(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, successMessage = null) }
            try {
                if (password.isBlank()) {
                    throw IllegalArgumentException("Backup password cannot be empty")
                }
                vaultSyncManager.backup(password)
                _uiState.update {
                    it.copy(
                        loading = false,
                        successMessage = "Backup completed successfully!"
                    )
                }
            } catch (e: Throwable) {
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = e.message ?: "Backup failed"
                    )
                }
            }
        }
    }

    fun restore(password: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null, successMessage = null) }
            try {
                if (password.isBlank()) {
                    throw IllegalArgumentException("Decryption password cannot be empty")
                }
                vaultSyncManager.restore(password)
                _uiState.update {
                    it.copy(
                        loading = false,
                        successMessage = "Restore completed successfully!"
                    )
                }
            } catch (e: Throwable) {
                val errorMsg = when {
                    e is javax.crypto.AEADBadTagException || e.message?.contains("tag mismatch", ignoreCase = true) == true -> {
                        "Decryption failed: Incorrect password"
                    }
                    e.message?.contains("base64", ignoreCase = true) == true -> {
                        "Invalid backup data format"
                    }
                    else -> e.message ?: "Restore failed"
                }
                _uiState.update {
                    it.copy(
                        loading = false,
                        error = errorMsg
                    )
                }
            }
        }
    }

    fun logout() {
        tokenStore.clearToken()
        _uiState.update {
            it.copy(
                authStatus = false,
                successMessage = "Logged out successfully!",
                error = null
            )
        }
    }

    fun clearErrorAndSuccess() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}

data class CloudSyncUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val authStatus: Boolean = false
)
