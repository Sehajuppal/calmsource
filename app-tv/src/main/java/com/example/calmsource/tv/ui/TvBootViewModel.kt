package com.example.calmsource.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.AuthPreferencesManager
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ProfileEntity
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import com.example.calmsource.tv.TvScreen
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Boot navigation gate evaluated strictly in order:
 * 1. Auth — persisted [AuthCredentials] (IPTV/debrid accounts) OR skip flag
 * 2. Profile — active profile from [ProfileSessionManager]
 * 3. Home — both gates passed
 */
enum class TvBootDestination {
    Loading,
    Onboarding,
    ProfileSelection,
    Home
}

data class TvBootGateState(
    val destination: TvBootDestination = TvBootDestination.Loading,
    val authCredentialsExist: Boolean = false,
    val hasSkippedAuth: Boolean = false,
    val activeProfile: ProfileEntity? = null
) {
    val isAuthPassed: Boolean = authCredentialsExist || hasSkippedAuth
}

@HiltViewModel
class TvBootViewModel @Inject constructor(
    authPreferencesManager: AuthPreferencesManager,
    profileSessionManager: ProfileSessionManager
) : ViewModel() {

    val gateState: StateFlow<TvBootGateState> = combine(
        DatabaseProvider.databaseReady,
        IPTVRepository.providers,
        DebridRepository.accounts,
        authPreferencesManager.hasSkippedAuth,
        profileSessionManager.activeProfile
    ) { dbReady, providers, debridAccounts, skippedAuth, profile ->
        if (!dbReady) {
            TvBootGateState(destination = TvBootDestination.Loading)
        } else {
            val credentialsExist = providers.isNotEmpty() || debridAccounts.isNotEmpty()
            TvBootGateState(
                destination = resolveDestination(
                    credentialsExist = credentialsExist,
                    hasSkippedAuth = skippedAuth,
                    activeProfile = profile
                ),
                authCredentialsExist = credentialsExist,
                hasSkippedAuth = skippedAuth,
                activeProfile = profile
            )
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = TvBootGateState()
        )

    fun isScreenAllowed(screen: TvScreen): Boolean {
        val gate = gateState.value
        return isScreenAllowed(
            destination = gate.destination,
            screen = screen,
            isAuthPassed = gate.isAuthPassed,
            activeProfile = gate.activeProfile
        )
    }

    fun redirectScreenIfBlocked(current: TvScreen): TvScreen {
        if (isScreenAllowed(current)) return current
        return when (gateState.value.destination) {
            TvBootDestination.Onboarding -> TvScreen.Onboarding
            TvBootDestination.ProfileSelection -> TvScreen.ProfileSelection
            else -> current
        }
    }

    fun bootScreenForFirstRoute(): TvScreen? {
        return when (gateState.value.destination) {
            TvBootDestination.Loading -> null
            TvBootDestination.Onboarding -> TvScreen.Onboarding
            TvBootDestination.ProfileSelection -> TvScreen.ProfileSelection
            TvBootDestination.Home -> TvScreen.Home
        }
    }

    companion object {
        internal fun resolveDestination(
            credentialsExist: Boolean,
            hasSkippedAuth: Boolean,
            activeProfile: ProfileEntity?
        ): TvBootDestination {
            val authPassed = credentialsExist || hasSkippedAuth
            return when {
                !authPassed -> TvBootDestination.Onboarding
                activeProfile == null -> TvBootDestination.ProfileSelection
                else -> TvBootDestination.Home
            }
        }

        internal fun isScreenAllowed(
            destination: TvBootDestination,
            screen: TvScreen,
            isAuthPassed: Boolean,
            activeProfile: ProfileEntity?
        ): Boolean {
            return when (destination) {
                TvBootDestination.Loading -> true
                TvBootDestination.Onboarding -> screen is TvScreen.Onboarding
                TvBootDestination.ProfileSelection -> when (screen) {
                    TvScreen.ProfileSelection,
                    TvScreen.Settings -> true
                    TvScreen.Onboarding -> isAuthPassed
                    else -> activeProfile != null
                }
                TvBootDestination.Home -> true
            }
        }
    }
}
