package com.example.calmsource.feature.iptv

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object IptvOptimizationStore {
    private const val TAG = "IptvOptimizationStore"
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

    fun update(transform: (IptvOptimizationPreferences) -> IptvOptimizationPreferences) = synchronized(this) {
        val updated = transform(_preferences.value)
        _preferences.value = updated
        persist(updated)
    }

    fun reset() = synchronized(this) {
        val defaults = defaultPreferences()
        _preferences.value = defaults
        appContext?.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)?.edit()?.clear()?.apply()
    }

    private fun readPreferences(): IptvOptimizationPreferences = synchronized(this) {
        val sharedPreferences = appContext
            ?.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)
            ?: return defaultPreferences()
        val defaults = defaultPreferences()

        val preferredLanguages = try {
            sharedPreferences.getStringSet(LANGUAGES, defaults.preferredLanguages)?.toSet() ?: defaults.preferredLanguages
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to read preferredLanguages; using defaults", e)
            defaults.preferredLanguages
        }

        val preferredCountry = try {
            sharedPreferences.getString(COUNTRY, defaults.preferredCountry).orEmpty()
        } catch (_: Exception) {
            defaults.preferredCountry
        }

        val favoriteCategories = try {
            sharedPreferences.getStringSet(CATEGORIES, defaults.favoriteCategories)?.toSet() ?: defaults.favoriteCategories
        } catch (_: Exception) {
            defaults.favoriteCategories
        }

        val hideAdult = try {
            sharedPreferences.getBoolean(HIDE_ADULT, defaults.hideAdult)
        } catch (_: Exception) {
            defaults.hideAdult
        }

        val hideUnsupported = try {
            sharedPreferences.getBoolean(HIDE_UNSUPPORTED, defaults.hideUnsupported)
        } catch (_: Exception) {
            defaults.hideUnsupported
        }

        val preferHighQuality = try {
            sharedPreferences.getBoolean(PREFER_HIGH_QUALITY, defaults.preferHighQuality)
        } catch (_: Exception) {
            defaults.preferHighQuality
        }

        val removeDuplicates = try {
            sharedPreferences.getBoolean(REMOVE_DUPLICATES, defaults.removeDuplicates)
        } catch (_: Exception) {
            defaults.removeDuplicates
        }

        val groupMode = try {
            IptvGroupMode.valueOf(
                sharedPreferences.getString(GROUP_MODE, defaults.groupMode.name)
                    ?: defaults.groupMode.name
            )
        } catch (e: Exception) {
            android.util.Log.w(TAG, "Failed to read groupMode; using defaults", e)
            defaults.groupMode
        }

        return IptvOptimizationPreferences(
            preferredLanguages = preferredLanguages,
            preferredCountry = preferredCountry,
            favoriteCategories = favoriteCategories,
            hideAdult = hideAdult,
            hideUnsupported = hideUnsupported,
            preferHighQuality = preferHighQuality,
            removeDuplicates = removeDuplicates,
            groupMode = groupMode
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
