package com.example.calmsource.core.data

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ProfileSessionEntryPoint {
    fun profileSessionManager(): ProfileSessionManager
}

@Composable
fun rememberProfileSessionManager(): ProfileSessionManager {
    val context = LocalContext.current
    return remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            ProfileSessionEntryPoint::class.java,
        ).profileSessionManager()
    }
}

@Composable
fun rememberActiveProfileId(): String {
    val sessionManager = rememberProfileSessionManager()
    val activeProfile by sessionManager.activeProfile.collectAsState()
    return activeProfile?.id ?: "default"
}
