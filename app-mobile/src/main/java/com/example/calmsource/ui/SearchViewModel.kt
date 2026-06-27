package com.example.calmsource.ui

import android.app.Application
import com.example.calmsource.feature.search.BaseSearchViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(application: Application) : BaseSearchViewModel(application)
