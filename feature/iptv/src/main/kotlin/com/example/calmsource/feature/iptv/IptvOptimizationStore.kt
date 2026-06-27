package com.example.calmsource.feature.iptv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IptvOptimizationStore {
    private const val FILE_NAME = "iptv_optimization"
    private const val LANGUAGES = "languages"
    private const val COUNTRY = "country"
    private const val CATEGORIES = "categories"
    private const val HIDE_ADULT = "hide_adult"
    private const val HIDE_UNSUPPORTED = "hide_unsupported"
    private const val PREFER_HIGH_QUALITY = "prefer_high_quality"
    private const val REMOVE_DUPLICATES = "remove_duplicates"
    private const val GROUP_MODE = "group_mode"

    private val _preferences = MutableStateFlow(defaultPreferences())
    val preferences: StateFlow<IptvOptimizationPreferences> = _preferences.asStateFlow()

    private var appContext: Context? = null

    fun init(context: Context) {
        appContext = context.applicationContext
        _preferences.value = readPreferences()
    }

    fun update(transform: (IptvOptimizationPreferences) -> IptvOptimizationPreferences) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        persist(updated)
    }

    fun reset() {
        val defaults = defaultPreferences()
        _preferences.value = defaults
        appContext?.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private fun readPreferences(): IptvOptimizationPreferences {
        val sharedPreferences = appContext
            ?.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            ?: return defaultPreferences()
        val defaults = defaultPreferences()
        return IptvOptimizationPreferences(
            preferredLanguages = sharedPreferences
                .getStringSet(LANGUAGES, defaults.preferredLanguages)
                ?.toSet()
                .orEmpty(),
            preferredCountry = sharedPreferences.getString(COUNTRY, "").orEmpty(),
            favoriteCategories = sharedPreferences.getStringSet(CATEGORIES, emptySet())?.toSet().orEmpty(),
            hideAdult = sharedPreferences.getBoolean(HIDE_ADULT, true),
            hideUnsupported = sharedPreferences.getBoolean(HIDE_UNSUPPORTED, true),
            preferHighQuality = sharedPreferences.getBoolean(PREFER_HIGH_QUALITY, true),
            removeDuplicates = sharedPreferences.getBoolean(REMOVE_DUPLICATES, true),
            groupMode = runCatching {
                IptvGroupMode.valueOf(
                    sharedPreferences.getString(GROUP_MODE, IptvGroupMode.CATEGORY.name)
                        ?: IptvGroupMode.CATEGORY.name
                )
            }.getOrDefault(IptvGroupMode.CATEGORY)
        )
    }

    private fun persist(preferences: IptvOptimizationPreferences) {
        appContext?.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            ?.edit()
            ?.putStringSet(LANGUAGES, preferences.preferredLanguages)
            ?.putString(COUNTRY, preferences.preferredCountry)
            ?.putStringSet(CATEGORIES, preferences.favoriteCategories)
            ?.putBoolean(HIDE_ADULT, preferences.hideAdult)
            ?.putBoolean(HIDE_UNSUPPORTED, preferences.hideUnsupported)
            ?.putBoolean(PREFER_HIGH_QUALITY, preferences.preferHighQuality)
            ?.putBoolean(REMOVE_DUPLICATES, preferences.removeDuplicates)
            ?.putString(GROUP_MODE, preferences.groupMode.name)
            ?.apply()
    }

    private fun defaultPreferences(): IptvOptimizationPreferences {
        // Language filtering is opt-in: applying the device's language by default
        // silently hides Xtream providers whose category names (e.g. "Hindi",
        // "Tamil") don't match the device locale, producing a "zero channels"
        // experience for users who never explicitly chose a filter.
        return IptvOptimizationPreferences(
            preferredLanguages = emptySet()
        )
    }
}
