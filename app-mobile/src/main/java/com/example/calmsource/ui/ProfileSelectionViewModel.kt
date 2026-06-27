package com.example.calmsource.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionManager: ProfileSessionManager
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeProfiles()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectProfile(profileId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            sessionManager.selectProfile(profileId)
            onComplete()
        }
    }

    fun addProfile(name: String, onComplete: () -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val newProfile = ProfileEntity(
                id = UUID.randomUUID().toString(),
                name = name.trim(),
                avatarUrl = null
            )
            profileRepository.insertProfile(newProfile)
            onComplete()
        }
    }

    fun renameProfile(profileId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                profileRepository.updateProfile(existing.copy(name = newName.trim()))
            }
        }
    }

    fun updateProfile(profileId: String, newName: String, avatarUrl: String?) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val existing = profileRepository.getProfileById(profileId)
            if (existing != null) {
                profileRepository.updateProfile(existing.copy(name = newName.trim(), avatarUrl = avatarUrl))
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            profileRepository.deleteProfileById(profileId)
        }
    }
}
