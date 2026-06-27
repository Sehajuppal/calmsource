package com.example.calmsource.core.playback

import android.content.Context

object VlcFallbackHelper {
    fun resetInitFailed(context: Context) {
        VlcPlayerBackend.resetInitFailed(context)
    }
}
