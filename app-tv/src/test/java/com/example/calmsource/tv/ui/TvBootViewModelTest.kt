package com.example.calmsource.tv.ui

import com.example.calmsource.tv.TvScreen
import org.junit.Assert.assertEquals
import org.junit.Test

class TvBootViewModelTest {

    @Test
    fun gate1_blocksUntilCredentialsOrSkip() {
        assertEquals(
            TvBootDestination.Onboarding,
            TvBootViewModel.resolveDestination(
                credentialsExist = false,
                hasSkippedAuth = false,
                activeProfile = null
            )
        )
        assertEquals(
            TvBootDestination.Onboarding,
            TvBootViewModel.resolveDestination(
                credentialsExist = false,
                hasSkippedAuth = false,
                activeProfile = fakeProfile()
            )
        )
    }

    @Test
    fun gate2_requiresProfileAfterAuth() {
        assertEquals(
            TvBootDestination.ProfileSelection,
            TvBootViewModel.resolveDestination(
                credentialsExist = true,
                hasSkippedAuth = false,
                activeProfile = null
            )
        )
        assertEquals(
            TvBootDestination.ProfileSelection,
            TvBootViewModel.resolveDestination(
                credentialsExist = false,
                hasSkippedAuth = true,
                activeProfile = null
            )
        )
    }

    @Test
    fun gate3_homeWhenAuthAndProfileReady() {
        assertEquals(
            TvBootDestination.Home,
            TvBootViewModel.resolveDestination(
                credentialsExist = true,
                hasSkippedAuth = false,
                activeProfile = fakeProfile()
            )
        )
        assertEquals(
            TvBootDestination.Home,
            TvBootViewModel.resolveDestination(
                credentialsExist = false,
                hasSkippedAuth = true,
                activeProfile = fakeProfile()
            )
        )
    }

    @Test
    fun settingsAllowedDuringProfileGate() {
        val profile = fakeProfile()
        assertEquals(
            true,
            TvBootViewModel.isScreenAllowed(
                destination = TvBootDestination.ProfileSelection,
                screen = TvScreen.Settings,
                isAuthPassed = true,
                activeProfile = null
            )
        )
        assertEquals(
            false,
            TvBootViewModel.isScreenAllowed(
                destination = TvBootDestination.ProfileSelection,
                screen = TvScreen.Home,
                isAuthPassed = true,
                activeProfile = null
            )
        )
        assertEquals(
            true,
            TvBootViewModel.isScreenAllowed(
                destination = TvBootDestination.ProfileSelection,
                screen = TvScreen.Home,
                isAuthPassed = true,
                activeProfile = profile
            )
        )
    }

    private fun fakeProfile() = com.example.calmsource.core.database.entity.ProfileEntity(
        id = "profile-1",
        name = "Guest",
        avatarUrl = null
    )
}
