package com.example.calmsource.ui

import android.app.Application
import com.example.calmsource.core.data.ProfileSessionManager
import com.example.calmsource.feature.iptv.BaseHomeViewModel
import com.example.calmsource.feature.iptv.ObserveHomeDataUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    application: Application,
    observeHomeDataUseCase: ObserveHomeDataUseCase? = null,
    sessionManager: ProfileSessionManager? = null
) : BaseHomeViewModel(
    application = application,
    maxRows = null,
    observeHomeDataUseCase = observeHomeDataUseCase,
    sessionManager = sessionManager
)
