package com.example.calmsource.core.data

import com.example.calmsource.core.database.entity.ProfileEntity
import kotlinx.coroutines.flow.StateFlow

interface ProfileSessionManager {
    val activeProfile: StateFlow<ProfileEntity?>
    suspend fun selectProfile(profileId: String)
}

class FallbackProfileSessionManager : ProfileSessionManager {
    override val activeProfile: StateFlow<ProfileEntity?> =
        kotlinx.coroutines.flow.MutableStateFlow(ProfileEntity(id = "default", name = "Default Profile"))
    override suspend fun selectProfile(profileId: String) {}
}
