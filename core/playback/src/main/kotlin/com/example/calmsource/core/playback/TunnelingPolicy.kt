package com.example.calmsource.core.playback

import android.content.Context
import android.os.Build
import androidx.media3.exoplayer.DefaultRenderersFactory
import com.example.calmsource.core.model.PlaybackSource

enum class TunnelingRejectReason {
    NONE,
    MODE_OFF,
    SDK_TOO_LOW,
    PROFILE_DISABLED,
    DEVICE_NOT_ALLOWLISTED,
    VIDEO_ONLY,
    UNSUPPORTED_CODECS,
    BLACKLISTED,
    RENDERER_API_UNAVAILABLE
}

data class TunnelingDecision(
    val enabled: Boolean,
    val reason: TunnelingRejectReason,
    val key: TunnelingBlacklistKey?
) {
    val compatibilityKey: String
        get() = if (enabled && key != null) {
            "tunnel:on:${key.storageKey}"
        } else {
            "tunnel:off:${reason.name}"
        }
}

object TunnelingPolicy {
    private val SUPPORTED_VIDEO_CODECS = setOf(
        "h264",
        "h.264",
        "avc",
        "h265",
        "h.265",
        "hevc"
    )
    private val SUPPORTED_AUDIO_CODECS = setOf(
        "aac",
        "ac3",
        "eac3",
        "e-ac3",
        "ec3",
        "mp4a"
    )

    val defaultSupportedDeviceModels: Set<String> = setOf(
        "adt-3",
        "chromecast with google tv",
        "google tv streamer",
        "mi box",
        "nvidia shield",
        "onn 4k",
        "onn google tv",
        "shield android tv",
        "shield android tv pro"
    )

    fun decisionFor(
        context: Context,
        source: PlaybackSource?,
        profile: PlaybackResourceProfile,
        mode: TunnelingMode = TunnelingPreferences.mode
    ): TunnelingDecision {
        return decisionFor(
            source = source,
            profile = profile,
            mode = mode,
            sdkInt = Build.VERSION.SDK_INT,
            deviceModel = Build.MODEL ?: "unknown",
            supportedDeviceModels = defaultSupportedDeviceModels,
            rendererFactorySupportsTunneling = rendererFactorySupportsTunneling(),
            isBlacklisted = { key -> TunnelingBlacklist.isBlacklistedBestEffort(context, key) }
        )
    }

    fun decisionFor(
        source: PlaybackSource?,
        profile: PlaybackResourceProfile,
        mode: TunnelingMode,
        sdkInt: Int,
        deviceModel: String,
        supportedDeviceModels: Set<String> = defaultSupportedDeviceModels,
        rendererFactorySupportsTunneling: Boolean = true,
        isBlacklisted: (TunnelingBlacklistKey) -> Boolean = { false }
    ): TunnelingDecision {
        if (mode == TunnelingMode.OFF) return disabled(TunnelingRejectReason.MODE_OFF)
        if (!profile.tunnelingEnabled) return disabled(TunnelingRejectReason.PROFILE_DISABLED)
        if (sdkInt < MIN_TUNNELING_SDK) return disabled(TunnelingRejectReason.SDK_TOO_LOW)
        if (!rendererFactorySupportsTunneling) return disabled(TunnelingRejectReason.RENDERER_API_UNAVAILABLE)
        if (!isDeviceAllowlisted(deviceModel, supportedDeviceModels)) {
            return disabled(TunnelingRejectReason.DEVICE_NOT_ALLOWLISTED)
        }

        val metadata = source?.metadata ?: return disabled(TunnelingRejectReason.UNSUPPORTED_CODECS)
        val audioCodec = metadata.audioCodec?.takeIf { it.isNotBlank() }
            ?: return disabled(TunnelingRejectReason.VIDEO_ONLY)
        val videoCodec = metadata.videoCodec?.takeIf { it.isNotBlank() }
            ?: return disabled(TunnelingRejectReason.UNSUPPORTED_CODECS)

        if (!isCompatibleAudioCodec(audioCodec) || !isCompatibleVideoCodec(videoCodec)) {
            return disabled(TunnelingRejectReason.UNSUPPORTED_CODECS)
        }

        val key = TunnelingBlacklist.keyFor(
            deviceModel = deviceModel,
            audioCodec = audioCodec,
            videoCodec = videoCodec
        )
        if (isBlacklisted(key)) {
            return TunnelingDecision(
                enabled = false,
                reason = TunnelingRejectReason.BLACKLISTED,
                key = key
            )
        }

        return TunnelingDecision(
            enabled = true,
            reason = TunnelingRejectReason.NONE,
            key = key
        )
    }

    fun applyToRenderersFactory(
        factory: DefaultRenderersFactory,
        decision: TunnelingDecision
    ): Boolean {
        if (!decision.enabled) return false
        return invokeBooleanSetter(factory, "setEnableTunneling", true)
    }

    fun rendererFactorySupportsTunneling(): Boolean = rendererFactorySupportsTunnelingCached

    fun isCompatibleAudioCodec(codec: String): Boolean {
        val normalized = normalizeCodec(codec)
        return SUPPORTED_AUDIO_CODECS.any { normalized.contains(it) }
    }

    fun isCompatibleVideoCodec(codec: String): Boolean {
        val normalized = normalizeCodec(codec)
        return SUPPORTED_VIDEO_CODECS.any { normalized.contains(it) }
    }

    private fun disabled(reason: TunnelingRejectReason): TunnelingDecision {
        return TunnelingDecision(
            enabled = false,
            reason = reason,
            key = null
        )
    }

    private fun isDeviceAllowlisted(
        deviceModel: String,
        supportedDeviceModels: Set<String>
    ): Boolean {
        val normalized = deviceModel.trim().lowercase()
        return supportedDeviceModels.any { supported ->
            val normalizedSupported = supported.trim().lowercase()
            normalized == normalizedSupported ||
                normalized.contains(normalizedSupported)
        }
    }

    private fun normalizeCodec(codec: String): String {
        return codec.trim().lowercase()
    }

    private val rendererFactorySupportsTunnelingCached: Boolean by lazy {
        hasBooleanSetter("setEnableTunneling")
    }

    private fun hasBooleanSetter(name: String): Boolean {
        return DefaultRenderersFactory::class.java.methods.any { method ->
            method.name == name &&
                method.parameterTypes.size == 1 &&
                method.parameterTypes[0] == java.lang.Boolean.TYPE
        }
    }

    private fun invokeBooleanSetter(
        factory: DefaultRenderersFactory,
        name: String,
        value: Boolean
    ): Boolean {
        val method = factory.javaClass.methods.firstOrNull { candidate ->
            candidate.name == name &&
                candidate.parameterTypes.size == 1 &&
                candidate.parameterTypes[0] == java.lang.Boolean.TYPE
        } ?: return false
        return runCatching {
            method.invoke(factory, value)
            true
        }.getOrDefault(false)
    }

    private const val MIN_TUNNELING_SDK = 30
}
