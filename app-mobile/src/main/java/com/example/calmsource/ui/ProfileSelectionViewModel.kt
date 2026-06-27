package com.example.calmsource.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.calmsource.core.data.ProfileRepository
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.core.database.entity.ProfileEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProfileSelectionViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val sessionManager: ProfileSessionManager
) : ViewModel() {

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeProfiles()
        .catch { e ->
            Log.e("ProfileSelectionVM", "Failed to observe profiles", e)
            emit(emptyList())
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    fun selectProfile(profileId: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                sessionManager.selectProfile(profileId)
                onComplete()
            } catch (e: Exception) {
                Log.e("ProfileSelectionVM", "Failed to select profile", e)
                _error.value = "Failed to select profile: ${e.message}"
            }
        }
    }

    fun addProfile(name: String, onComplete: () -> Unit = {}) {
        if (name.isBlank()) return
        viewModelScope.launch {
            try {
                val newProfile = ProfileEntity(
                    id = UUID.randomUUID().toString(),
                    name = name.trim(),
                    avatarUrl = null
                )
                profileRepository.insertProfile(newProfile)
                onComplete()
            } catch (e: Exception) {
                Log.e("ProfileSelectionVM", "Failed to add profile", e)
                _error.value = "Failed to create profile: ${e.message}"
            }
        }
    }

    fun renameProfile(profileId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                val existing = profileRepository.getProfileById(profileId)
                if (existing != null) {
                    profileRepository.updateProfile(existing.copy(name = newName.trim()))
                }
            } catch (e: Exception) {
                Log.e("ProfileSelectionVM", "Failed to rename profile", e)
                _error.value = "Failed to rename profile: ${e.message}"
            }
        }
    }

    fun updateProfile(profileId: String, newName: String, avatarUrl: String?) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            try {
                val existing = profileRepository.getProfileById(profileId)
                if (existing != null) {
                    profileRepository.updateProfile(existing.copy(name = newName.trim(), avatarUrl = avatarUrl))
                }
            } catch (e: Exception) {
                Log.e("ProfileSelectionVM", "Failed to update profile", e)
                _error.value = "Failed to update profile: ${e.message}"
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            try {
                profileRepository.deleteProfileById(profileId)
            } catch (e: Exception) {
                Log.e("ProfileSelectionVM", "Failed to delete profile", e)
                _error.value = "Failed to delete profile: ${e.message}"
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
