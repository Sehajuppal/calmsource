package com.example.calmsource.core.playback

import android.content.Context

object PlaybackUserPreferences {
    private const val PREFS_NAME = "playback_settings"
    private const val KEY_AUTOPLAY_NEXT = "autoplay_next_episode"
    private const val KEY_BACKGROUND_PLAYBACK = "background_playback"

    fun isAutoplayNextEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_AUTOPLAY_NEXT, true)

    fun isBackgroundPlaybackEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_BACKGROUND_PLAYBACK, true)

    fun setAutoplayNextEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_AUTOPLAY_NEXT, enabled)
            .apply()
    }

    fun setBackgroundPlaybackEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_BACKGROUND_PLAYBACK, enabled)
            .apply()
    }
}
