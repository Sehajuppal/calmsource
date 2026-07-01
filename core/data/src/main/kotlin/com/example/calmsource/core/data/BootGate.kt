package com.example.calmsource.core.data

import com.example.calmsource.core.database.entity.ProfileEntity

enum class BootDestination {
    Loading,
    Login,
    ProfileSelection,
    Home,
}

data class BootGateState(
    val destination: BootDestination = BootDestination.Loading,
    val authCredentialsExist: Boolean = false,
    val cloudSignedIn: Boolean = false,
    val hasSkippedAuth: Boolean = false,
    val activeProfile: ProfileEntity? = null,
) {
    val isAuthPassed: Boolean = authCredentialsExist || cloudSignedIn || hasSkippedAuth
}

object BootGateResolver {
    fun resolveDestination(
        credentialsExist: Boolean,
        cloudSignedIn: Boolean,
        hasSkippedAuth: Boolean,
        activeProfile: ProfileEntity?,
    ): BootDestination {
        val authPassed = credentialsExist || cloudSignedIn || hasSkippedAuth
        return when {
            !authPassed -> BootDestination.Login
            activeProfile == null -> BootDestination.ProfileSelection
            else -> BootDestination.Home
        }
    }
}
