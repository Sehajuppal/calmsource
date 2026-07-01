package com.example.calmsource.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.MobileScreen
import com.example.calmsource.core.data.AuthPreferencesManager
import com.example.calmsource.core.data.BootDestination
import com.example.calmsource.core.data.BootGateResolver
import com.example.calmsource.core.data.BootGateState
import com.example.calmsource.core.data.CloudAuthTokenStore
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.DatabaseProvider
import com.example.calmsource.core.database.entity.ProfileEntity
import com.example.calmsource.core.model.DebridAccount
import com.example.calmsource.core.model.IPTVProvider
import com.example.calmsource.feature.debrid.DebridRepository
import com.example.calmsource.feature.iptv.IPTVRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class MobileBootViewModel @Inject constructor(
    authPreferencesManager: AuthPreferencesManager,
    profileSessionManager: ProfileSessionManager,
    tokenStore: CloudAuthTokenStore,
) : ViewModel() {

    fun isScreenAllowed(screen: MobileScreen): Boolean {
        val gate = gateState.value
        return isScreenAllowed(
            destination = gate.destination,
            screen = screen,
            isAuthPassed = gate.isAuthPassed,
            activeProfile = gate.activeProfile,
        )
    }

    fun redirectScreenIfBlocked(current: MobileScreen): MobileScreen {
        if (isScreenAllowed(current)) return current
        return when (gateState.value.destination) {
            BootDestination.Login -> MobileScreen.Login
            BootDestination.ProfileSelection -> MobileScreen.Profiles
            else -> current
        }
    }

    fun bootScreenForFirstRoute(): MobileScreen? {
        return when (gateState.value.destination) {
            BootDestination.Loading -> null
            BootDestination.Login -> MobileScreen.Login
            BootDestination.ProfileSelection -> MobileScreen.Profiles
            BootDestination.Home -> MobileScreen.Home
        }
    }

    val gateState: StateFlow<BootGateState> = combine(
        DatabaseProvider.databaseReady,
        IPTVRepository.providers,
        DebridRepository.accounts,
        authPreferencesManager.hasSkippedAuth,
        profileSessionManager.activeProfile,
        tokenStore.tokenRevision,
    ) { values ->
        val dbReady = values[0] as Boolean
        val providers = values[1] as List<IPTVProvider>
        val debridAccounts = values[2] as List<DebridAccount>
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

    companion object {
        internal fun isScreenAllowed(
            destination: BootDestination,
            screen: MobileScreen,
            isAuthPassed: Boolean,
            activeProfile: ProfileEntity?,
        ): Boolean {
            return when (destination) {
                BootDestination.Loading -> true
                BootDestination.Login -> screen is MobileScreen.Login
                BootDestination.ProfileSelection -> when (screen) {
                    MobileScreen.Profiles,
                    MobileScreen.Settings -> true
                    MobileScreen.Login -> isAuthPassed
                    else -> activeProfile != null
                }
                BootDestination.Home -> true
            }
        }
    }
}
