package com.example.calmsource.tv.ui

import android.app.Application
import com.example.calmsource.feature.search.BaseSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class TvSearchViewModel @Inject constructor(application: Application) : BaseSearchViewModel(application)
