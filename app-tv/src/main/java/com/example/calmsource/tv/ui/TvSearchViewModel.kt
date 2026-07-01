package com.example.calmsource.tv.ui

import android.app.Application
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.feature.search.BaseSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvSearchViewModel @Inject constructor(
    application: Application,
    private val profileSessionManager: ProfileSessionManager,
) : BaseSearchViewModel(application) {
    override fun activeProfileId(): String =
        profileSessionManager.activeProfile.value?.id ?: "default"
}
