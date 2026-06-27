package com.example.calmsource.core.playback

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build

internal class PlaybackAudioManager(
    private val context: Context,
    private val onPauseRequired: (abandonFocus: Boolean) -> Unit,
    private val onResumeRequired: () -> Unit,
    private val isPlayingProvider: () -> Boolean
) {
    private var audioManager: AudioManager? = null
    private var activeAudioFocusRequest: AudioFocusRequest? = null
    private var resumeOnFocusGain = false
    private var noisyReceiverRegistered = false

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                resumeOnFocusGain = false
                onPauseRequired(true)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                resumeOnFocusGain = isPlayingProvider()
                onPauseRequired(false)
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> { /* allow ducking */ }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    onResumeRequired()
                }
            }
        }
    }

    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                onPauseRequired(true)
            }
        }
    }

    fun requestFocusAndRegister() {
        val am = audioManager ?: run {
            val mgr = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            audioManager = mgr
            mgr
        }
        if (am != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
                            .build()
                    )
                    .setOnAudioFocusChangeListener(audioFocusListener)
                    .build()
                activeAudioFocusRequest = focusRequest
                am.requestAudioFocus(focusRequest)
            } else {
                @Suppress("DEPRECATION")
                am.requestAudioFocus(
                    audioFocusListener,
                    AudioManager.STREAM_MUSIC,
                    AudioManager.AUDIOFOCUS_GAIN
                )
            }
        }
        if (!noisyReceiverRegistered) {
            val noisyFilter = IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(becomingNoisyReceiver, noisyFilter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("UnspecifiedRegisterReceiverFlag")
                context.registerReceiver(becomingNoisyReceiver, noisyFilter)
            }
            noisyReceiverRegistered = true
        }
    }

    fun abandonAudioFocus() {
        val am = audioManager
        if (am != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                activeAudioFocusRequest?.let {
                    am.abandonAudioFocusRequest(it)
                }
                activeAudioFocusRequest = null
            } else {
                @Suppress("DEPRECATION")
                am.abandonAudioFocus(audioFocusListener)
            }
        }
    }

    fun release() {
        abandonAudioFocus()
        audioManager = null
        if (noisyReceiverRegistered) {
            runCatching { context.unregisterReceiver(becomingNoisyReceiver) }
            noisyReceiverRegistered = false
        }
    }
}
