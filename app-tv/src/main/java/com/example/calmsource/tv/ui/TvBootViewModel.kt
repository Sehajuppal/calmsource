package com.example.calmsource.tv.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.AuthPreferencesManager
import com.example.calmsource.core.data.BootDestination
import com.example.calmsource.core.data.BootGateResolver
import com.example.calmsource.core.data.BootGateState
import com.example.calmsource.core.data.CloudAuthTokenStore
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
 * 1. Auth — IPTV/debrid credentials, cloud account, or skip flag
 * 2. Profile — active profile from [ProfileSessionManager]
 * 3. Home — both gates passed
 */
enum class TvBootDestination {
    Loading,
    Onboarding,
    ProfileSelection,
    Home,
}

typealias TvBootGateState = BootGateState

@HiltViewModel
class TvBootViewModel @Inject constructor(
    authPreferencesManager: AuthPreferencesManager,
    profileSessionManager: ProfileSessionManager,
    tokenStore: CloudAuthTokenStore,
) : ViewModel() {

    val gateState: StateFlow<BootGateState> = combine(
        DatabaseProvider.databaseReady,
        IPTVRepository.providers,
        DebridRepository.accounts,
        authPreferencesManager.hasSkippedAuth,
        profileSessionManager.activeProfile,
        tokenStore.tokenRevision,
    ) { values ->
        val dbReady = values[0] as Boolean
        val providers = values[1] as List<com.example.calmsource.core.model.IPTVProvider>
        val debridAccounts = values[2] as List<com.example.calmsource.core.model.DebridAccount>
        val skippedAuth = values[3] as Boolean
        val profile = values[4] as ProfileEntity?
        if (!dbReady) {
            BootGateState(destination = BootDestination.Loading)
        } else {
            val credentialsExist = providers.isNotEmpty() || debridAccounts.isNotEmpty()
            val cloudSignedIn = tokenStore.getToken() != null
            BootGateState(
                destination = BootGateResolver.resolveDestination(
                    credentialsExist = credentialsExist,
                    cloudSignedIn = cloudSignedIn,
                    hasSkippedAuth = skippedAuth,
                    activeProfile = profile,
                ),
                authCredentialsExist = credentialsExist,
                cloudSignedIn = cloudSignedIn,
                hasSkippedAuth = skippedAuth,
                activeProfile = profile,
            )
        }
    }
        .distinctUntilChanged()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = BootGateState(),
        )

    fun isScreenAllowed(screen: TvScreen): Boolean {
        val gate = gateState.value
        return isScreenAllowed(
            destination = gate.destination.toTvBootDestination(),
            screen = screen,
            isAuthPassed = gate.isAuthPassed,
            activeProfile = gate.activeProfile,
        )
    }

    fun redirectScreenIfBlocked(current: TvScreen): TvScreen {
        if (isScreenAllowed(current)) return current
        return when (gateState.value.destination.toTvBootDestination()) {
            TvBootDestination.Onboarding -> TvScreen.Onboarding
            TvBootDestination.ProfileSelection -> TvScreen.ProfileSelection
            else -> current
        }
    }

    fun bootScreenForFirstRoute(): TvScreen? {
        return when (gateState.value.destination.toTvBootDestination()) {
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
            activeProfile: ProfileEntity?,
            cloudSignedIn: Boolean = false,
        ): TvBootDestination {
            return BootGateResolver.resolveDestination(
                credentialsExist = credentialsExist,
                cloudSignedIn = cloudSignedIn,
                hasSkippedAuth = hasSkippedAuth,
                activeProfile = activeProfile,
            ).toTvBootDestination()
        }

        internal fun isScreenAllowed(
            destination: TvBootDestination,
            screen: TvScreen,
            isAuthPassed: Boolean,
            activeProfile: ProfileEntity?,
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

private fun BootDestination.toTvBootDestination(): TvBootDestination = when (this) {
    BootDestination.Loading -> TvBootDestination.Loading
    BootDestination.Login -> TvBootDestination.Onboarding
    BootDestination.ProfileSelection -> TvBootDestination.ProfileSelection
    BootDestination.Home -> TvBootDestination.Home
}
