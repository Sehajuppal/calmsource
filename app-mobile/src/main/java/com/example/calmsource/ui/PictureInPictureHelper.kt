package com.example.calmsource.ui

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.pm.PackageManager
import android.os.Build
import android.util.Rational
import androidx.annotation.RequiresApi
import androidx.media3.common.Player

object PictureInPictureHelper {
    fun isSupported(context: android.content.Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val activity = context as? Activity ?: return false
        return activity.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterPictureInPicture(activity: Activity, player: Player?): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (player == null) return false
        if (player.playbackState != Player.STATE_READY && player.playbackState != Player.STATE_BUFFERING) {
            return false
        }
        return try {
            val params = buildParams()
            activity.enterPictureInPictureMode(params)
        } catch (_: IllegalStateException) {
            false
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private fun buildParams(): PictureInPictureParams {
        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(false)
        }
        return builder.build()
    }
}
