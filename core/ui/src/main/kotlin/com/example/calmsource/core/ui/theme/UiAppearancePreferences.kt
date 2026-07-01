package com.example.calmsource.core.ui.theme

import android.content.Context

object UiAppearancePreferences {
    private const val PREFS_NAME = "ui_appearance"
    private const val KEY_OLED_THEME = "oled_theme"

    fun isOledTheme(context: Context, default: Boolean = false): Boolean {
        return context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_OLED_THEME, default)
    }

    fun setOledTheme(context: Context, enabled: Boolean) {
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_OLED_THEME, enabled)
            .apply()
    }
}
