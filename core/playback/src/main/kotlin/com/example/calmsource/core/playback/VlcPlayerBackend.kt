package com.example.calmsource.core.playback

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.example.calmsource.core.model.PlaybackAudioTrack
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlaybackSubtitleTrack
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * LibVLC-based [PlayerBackend] used as fallback when ExoPlayer cannot
 * parse a media container. VLC's FFmpeg backend supports a vastly wider
 * range of formats including raw streams, damaged files, and uncommon
 * containers.
 *
 * **VLC must be present on the device as a runtime library.** This class
 * uses reflection to call VLC APIs, so the app compiles without the
 * libvlc dependency on the compile classpath. If VLC is not available
 * at runtime (ClassNotFoundException), the fallback silently degrades —
 * ExoPlayer's source-switching fallback handles the failure instead.
 *
 * **To ship VLC:** Add the Videolan Maven repository and libvlc-all
 * dependency to core/playback/build.gradle.kts:
 * ```
 * repositories {
 *     maven { url = uri("https://artifactory.videolan.org/artifactory/public") }
 * }
 * dependencies {
 *     implementation("org.videolan.android:libvlc-all:3.6.3")
 * }
 * ```
 */
internal class VlcPlayerBackend : PlayerBackend {

    private val _state = MutableStateFlow(PlayerBackendState(PlayerState.IDLE))
    override val state: StateFlow<PlayerBackendState> = _state.asStateFlow()

    @Volatile
    private var _playerView: androidx.media3.ui.PlayerView? = null
    override var playerView: androidx.media3.ui.PlayerView?
        get() = synchronized(this) { _playerView }
        set(value) {
            synchronized(this) {
                val oldView = _playerView
                if (oldView === value) return
                _playerView = value
                val mp = mediaPlayer
                if (mp != null) {
                    runCatching {
                        val vout = mp.javaClass.getMethod("getVLCVout").invoke(mp)
                        if (vout != null) {
                            val voutClass = vout.javaClass
                            // Always detach first to release old surface
                            voutClass.getMethod("detachViews").invoke(vout)
                            
                            if (value != null) {
                                val surfaceView = findSurfaceView(value)
                                if (surfaceView != null) {
                                    voutClass.getMethod(
                                        "setVideoSurface",
                                        android.view.Surface::class.java,
                                        android.view.SurfaceHolder::class.java
                                    ).invoke(vout, surfaceView.holder.surface, surfaceView.holder)
                                    voutClass.getMethod("attachViews").invoke(vout)
                                    Log.d(TAG, "Attached new VLC surface on playerView update")
                                }
                            } else {
                                Log.d(TAG, "Detached VLC surface on playerView set to null")
                            }
                        }
                    }.onFailure { e ->
                        Log.w(TAG, "Failed to dynamically rebind playerView surface: ${e.message}", e)
                    }
                }
            }
        }
    override val isSurfaceRequired: Boolean = true

    @Volatile
    private var libVLC: Any? = null
    @Volatile
    private var mediaPlayer: Any? = null
    @Volatile
    private var currentMedia: Any? = null
    @Volatile
    private var hasPlayed = false
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile
    private var generation = 0

    private var vlcEventBuffering = VLC_EVENT_UNRESOLVED
    private var vlcEventPlaying = VLC_EVENT_UNRESOLVED
    private var vlcEventPaused = VLC_EVENT_UNRESOLVED
    private var vlcEventStopped = VLC_EVENT_UNRESOLVED
    private var vlcEventEndReached = VLC_EVENT_UNRESOLVED
    private var vlcEventEncounteredError = VLC_EVENT_UNRESOLVED
    private var vlcEventVout = VLC_EVENT_UNRESOLVED

    private fun mergePlayerState(
        playerState: PlayerState,
        firstFrameRendered: Boolean? = null,
        clearError: Boolean = false,
    ): PlayerBackendState {
        val current = _state.value
        return current.copy(
            playerState = playerState,
            firstFrameRendered = firstFrameRendered ?: current.firstFrameRendered,
            error = if (clearError) null else current.error,
        )
    }

    private fun resolveEventConstants(eventClass: Class<*>) {
        runCatching {
            vlcEventBuffering = eventClass.getField("Buffering").get(null) as Int
        }
        runCatching {
            vlcEventPlaying = eventClass.getField("Playing").get(null) as Int
        }
        runCatching {
            vlcEventPaused = eventClass.getField("Paused").get(null) as Int
        }
        runCatching {
            vlcEventStopped = eventClass.getField("Stopped").get(null) as Int
        }
        runCatching {
            vlcEventEndReached = eventClass.getField("EndReached").get(null) as Int
        }
        runCatching {
            vlcEventEncounteredError = eventClass.getField("EncounteredError").get(null) as Int
        }
        runCatching {
            vlcEventVout = eventClass.getField("Vout").get(null) as Int
        }
        if (vlcEventBuffering == VLC_EVENT_UNRESOLVED || vlcEventPlaying == VLC_EVENT_UNRESOLVED) {
            Log.w(TAG, "VLC event constants could not be resolved — VLC fallback may behave incorrectly")
        }
    }

    companion object {
        private const val TAG = "VlcPlayerBackend"
        private const val VLC_EVENT_UNRESOLVED = -1
        private const val VLC_PREFS = "vlc_fallback_state"
        private const val KEY_INIT_FAILED = "init_failed"
        private const val KEY_VERSION_CODE = "version_code"
        private const val KEY_LAST_FAILURE_TIMESTAMP = "last_failure_timestamp"

        private val SENSITIVE_HEADER_KEYS = setOf(
            "authorization", "x-api-key", "token", "cookie",
            "set-cookie", "x-auth-token", "proxy-authorization"
        )

        /** Set to true after the first constructor-level init failure.
         *  Backed by SharedPreferences so it survives process kills
         *  (Fire TV Stick OOM-kills during IPTV sync). */
        @Volatile
        private var initFailed = false

        @Volatile
        internal var isAvailableOverride: Boolean? = null

        /** True if the VLC runtime classes are reachable on the classpath AND
         *  no previous init has failed at the constructor level. Once init fails
         *  (e.g., NoSuchMethodException for a mismatched libvlc version), this
         *  returns false for the rest of the process AND persists to disk so
         *  future process starts also skip VLC. */
        val isAvailable: Boolean
            get() {
                isAvailableOverride?.let { return it }
                if (initFailed) return false
                return try {
                    Class.forName("org.videolan.libvlc.LibVLC")
                    true
                } catch (_: Throwable) {
                    Log.w(TAG, "LibVLC not found on classpath — VLC fallback disabled")
                    false
                }
            }

        /** Called by [prepare] when the VLC constructor is not found or any
         *  other non-recoverable init error occurs. Marks VLC as permanently
         *  unavailable — persists to SharedPreferences so even process
         *  restarts won't retry the broken fallback. */
        fun markInitFailed(context: Context) {
            initFailed = true
            try {
                val appContext = try { context.applicationContext ?: context } catch (_: Exception) { context }
                appContext.getSharedPreferences(VLC_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_INIT_FAILED, true)
                    .putLong(KEY_LAST_FAILURE_TIMESTAMP, System.currentTimeMillis())
                    .apply()
            } catch (_: Exception) {
                // Best-effort — process-level flag still works
            }
        }

        fun resetInitFailed(context: Context) {
            initFailed = false
            try {
                val appContext = try { context.applicationContext ?: context } catch (_: Exception) { context }
                appContext.getSharedPreferences(VLC_PREFS, Context.MODE_PRIVATE)
                    .edit()
                    .putBoolean(KEY_INIT_FAILED, false)
                    .putLong(KEY_LAST_FAILURE_TIMESTAMP, 0L)
                    .apply()
            } catch (_: Exception) {
                // Best-effort
            }
        }

        /** Load the persisted VLC failure state from SharedPreferences.
         *  Must be called once early in app startup before any playback. */
        fun restoreInitFailedState(context: Context) {
            try {
                val appContext = try { context.applicationContext ?: context } catch (_: Exception) { context }
                val prefs = appContext.getSharedPreferences(VLC_PREFS, Context.MODE_PRIVATE)

                // R1: App version update check
                val currentVersion = getCurrentVersionCode(appContext)
                val storedVersion = prefs.getLong(KEY_VERSION_CODE, -1L)
                if (storedVersion == -1L || currentVersion > storedVersion) {
                    prefs.edit()
                        .putBoolean(KEY_INIT_FAILED, false)
                        .putLong(KEY_VERSION_CODE, currentVersion)
                        .putLong(KEY_LAST_FAILURE_TIMESTAMP, 0L)
                        .apply()
                    initFailed = false
                    return
                }

                // R2: Time-based retry check (24 hours)
                val isFailed = prefs.getBoolean(KEY_INIT_FAILED, false)
                if (isFailed) {
                    val lastFailureTime = prefs.getLong(KEY_LAST_FAILURE_TIMESTAMP, 0L)
                    if (lastFailureTime > 0L && System.currentTimeMillis() - lastFailureTime > 24 * 60 * 60 * 1000) {
                        prefs.edit()
                            .putBoolean(KEY_INIT_FAILED, false)
                            .putLong(KEY_LAST_FAILURE_TIMESTAMP, 0L)
                            .apply()
                        initFailed = false
                    } else {
                        initFailed = true
                    }
                } else {
                    initFailed = false
                }
            } catch (_: Exception) {
                // No-op — default to false
                initFailed = false
            }
        }

        private fun getCurrentVersionCode(context: Context): Long {
            return try {
                val pm = context.packageManager ?: return -1L
                val packageName = context.packageName ?: ""
                val packageInfo = if (android.os.Build.VERSION.SDK_INT >= 33) {
                    pm.getPackageInfo(
                        packageName,
                        android.content.pm.PackageManager.PackageInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageInfo(packageName, 0)
                }
                if (android.os.Build.VERSION.SDK_INT >= 28) {
                    packageInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    packageInfo.versionCode.toLong()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to get version code", e)
                -1L
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun prepare(context: Context, source: PlaybackSource, resumePositionMs: Long) {
        val unsafeSchemes = setOf("file", "javascript", "data", "content", "ftp")
        val scheme = try { android.net.Uri.parse(source.rawUrl).scheme?.lowercase() } catch (e: Exception) { null }
        if (scheme in unsafeSchemes) {
            _state.value = PlayerBackendState(
                PlayerState.FAILED,
                PlaybackError.SourceUnavailable(message = "Unsafe protocol scheme blocked.")
            )
            return
        }

        val prepareGeneration = synchronized(this) {
            generation += 1
            generation
        }
        // Save playerView reference before release() nulls it
        val savedPlayerView = synchronized(this) { _playerView }
        releaseInternal(incrementGeneration = false)
        _state.value = PlayerBackendState(PlayerState.PREPARING, firstFrameRendered = false)

        if (!isAvailable) {
            _state.value = PlayerBackendState(
                PlayerState.FAILED,
                PlaybackError.SourceUnavailable(message = "VLC is not installed on this device.")
            )
            return
        }

        synchronized(this) {
            try {
                val surfaceView = savedPlayerView?.let { findSurfaceView(it) }
                if (surfaceView == null) {
                    _state.value = PlayerBackendState(
                        PlayerState.FAILED,
                        PlaybackError.SourceUnavailable(message = "No video surface available for VLC.")
                    )
                    return
                }

                val args = ArrayList<String>().apply {
                    add("--aout=opensles")
                    add("--audio-time-stretch")
                    add("--verbose=-1")
                    add("--no-drop-late-frames")
                    add("--no-skip-frames")
                    add("--network-caching=3000")
                    add("--file-caching=1000")
                }

                val libVlcClass = Class.forName("org.videolan.libvlc.LibVLC")
                val iLibVlcClass = Class.forName("org.videolan.libvlc.interfaces.ILibVLC")
                val mediaClass = Class.forName("org.videolan.libvlc.Media")
                val playerClass = Class.forName("org.videolan.libvlc.MediaPlayer")

                // LibVLC(context, args)
                val vlc = libVlcClass.getConstructor(Context::class.java, List::class.java)
                    .newInstance(context, args)
                libVLC = vlc

                // Media(libvlc, uri)
                val uri = android.net.Uri.parse(source.rawUrl)
                val media = mediaClass.getConstructor(iLibVlcClass, android.net.Uri::class.java)
                    .newInstance(vlc, uri)
                currentMedia = media

                try {
                    val addOptionMethod = mediaClass.getMethod("addOption", String::class.java)
                    source.headers.forEach { (key, value) ->
                        if (key.lowercase() in SENSITIVE_HEADER_KEYS) return@forEach
                        when (key.lowercase()) {
                            "user-agent" -> addOptionMethod.invoke(media, ":http-user-agent=$value")
                            "referer" -> addOptionMethod.invoke(media, ":http-referrer=$value")
                            else -> addOptionMethod.invoke(media, ":http-header-fields=$key: $value")
                        }
                    }
                } catch (e: Exception) {
                    Log.w("VlcPlayerBackend", "Failed to set custom HTTP headers: ${e.message}")
                }

                // MediaPlayer(libvlc)
                val player = playerClass.getConstructor(iLibVlcClass).newInstance(vlc)
                mediaPlayer = player

                // Set event listener via reflection-based proxy
                val eventListenerClass = Class.forName("org.videolan.libvlc.MediaPlayer\$EventListener")
                val eventClass = Class.forName("org.videolan.libvlc.MediaPlayer\$Event")
                resolveEventConstants(eventClass)

                val proxy = java.lang.reflect.Proxy.newProxyInstance(
                    eventListenerClass.classLoader,
                    arrayOf(eventListenerClass)
                ) { _, method, argsArr ->
                    if (method.name == "onEvent" && argsArr != null && argsArr.size == 1) {
                        if (prepareGeneration == generation) {
                            handleVlcEventReflective(argsArr[0], eventClass, source)
                        }
                    }
                    null
                }

                playerClass.getMethod("setEventListener", eventListenerClass).invoke(player, proxy)

                // player.media = media
                val iMediaClass = Class.forName("org.videolan.libvlc.interfaces.IMedia")
                playerClass.getMethod("setMedia", iMediaClass).invoke(player, media)

                // Store the Media reference so release() can explicitly call
                // media.release() to free native resources. VLC's native refcount
                // is incremented by setMedia() above, so it is safe to release
                // after the MediaPlayer has been stopped/released.

                // Attach video output
                val vout = playerClass.getMethod("getVLCVout").invoke(player)
                    ?: throw IllegalStateException("VLC video output (VLCVout) is null")
                val voutClass = vout.javaClass
                voutClass.getMethod(
                    "setVideoSurface",
                    android.view.Surface::class.java,
                    android.view.SurfaceHolder::class.java
                ).invoke(vout, surfaceView.holder.surface, surfaceView.holder)
                voutClass.getMethod("attachViews").invoke(vout)

                // Play first — VLC requires buffered media before setTime() takes effect
                playerClass.getMethod("play").invoke(player)

                // Seek after play starts (post-delayed to ensure VLC has buffered)
                if (resumePositionMs > 0L) {
                    mainHandler.postDelayed({
                        if (prepareGeneration != generation || mediaPlayer !== player) return@postDelayed
                        runCatching {
                            playerClass.getMethod("setTime", Long::class.javaPrimitiveType!!)
                                .invoke(player, resumePositionMs)
                        }
                    }, 300L)
                }
                _playerView = savedPlayerView

            } catch (e: ClassNotFoundException) {
                Log.e(TAG, "VLC classes not found at runtime", e)
                release()
                _state.value = PlayerBackendState(
                    PlayerState.FAILED,
                    PlaybackError.SourceUnavailable(message = "VLC runtime not available on this device.")
                )
            } catch (e: Throwable) {
                if (e is java.lang.OutOfMemoryError) {
                    release()
                    throw e
                }
                release()
                // Only a genuine VLC library/ABI incompatibility (missing reflected methods/fields
                // or a native linkage failure) should disable VLC for the rest of the process + 24h.
                // Per-stream failures — bad URI, null surface, a stream VLC simply can't open
                // (surfaced as InvocationTargetException/IllegalState/IllegalArgument) — must NOT
                // permanently disable the fallback for every other stream (#16).
                val brokenVlcLibrary = e is NoSuchMethodException ||
                    e is NoSuchFieldException ||
                    e is LinkageError // NoClassDefFoundError, UnsatisfiedLinkError, …
                if (brokenVlcLibrary) {
                    markInitFailed(context)
                } else if (e is java.lang.Error) {
                    // Unrecoverable JVM error unrelated to VLC availability — propagate.
                    throw e
                }
                Log.e(TAG, "VLC playback start failed: ${e.message}", e)
                _state.value = PlayerBackendState(
                    PlayerState.FAILED,
                    PlaybackError.Unknown(
                        message = "VLC player failed to start.",
                        cause = if (e is Exception) e else RuntimeException(e)
                    )
                )
            }
        }
    }

    private fun handleVlcEventReflective(event: Any, eventClass: Class<*>, source: PlaybackSource) {
        try {
            val type = eventClass.getField("type").get(event) as Int
            when (type) {
                vlcEventBuffering -> _state.value = mergePlayerState(PlayerState.BUFFERING, clearError = true)
                vlcEventPlaying -> {
                    hasPlayed = true
                    _state.value = mergePlayerState(PlayerState.PLAYING, clearError = true)
                }
                vlcEventPaused -> _state.value = mergePlayerState(PlayerState.PAUSED)
                vlcEventStopped -> _state.value = mergePlayerState(PlayerState.IDLE)
                vlcEventEndReached -> _state.value = mergePlayerState(PlayerState.ENDED)
                vlcEventVout -> {
                    val voutCount = runCatching {
                        eventClass.getMethod("getVoutCount").invoke(event) as Int
                    }.getOrDefault(0)
                    if (voutCount > 0) {
                        _state.value = mergePlayerState(
                            playerState = _state.value.playerState,
                            firstFrameRendered = true,
                        )
                    }
                }
                vlcEventEncounteredError -> {
                    Log.e(TAG, "VLC error for: ${source.displayUrl}")
                    _state.value = _state.value.copy(
                        playerState = PlayerState.FAILED,
                        error = PlaybackError.Unknown(
                            message = "VLC could not play this stream.",
                            cause = Exception("VLC EncounteredError")
                        ),
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "VLC event handler failed: ${e.message}", e)
        }
    }

    private fun findSurfaceView(): android.view.SurfaceView? {
        val view = _playerView ?: return null
        return findSurfaceView(view)
    }

    private fun findSurfaceView(view: android.view.ViewGroup, depth: Int = 0): android.view.SurfaceView? {
        if (depth > 5) return null
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            if (child is android.view.SurfaceView) {
                return child
            } else if (child is android.view.ViewGroup) {
                val found = findSurfaceView(child, depth + 1)
                if (found != null) return found
            }
        }
        return null
    }

    override fun play() {
        try { mediaPlayer?.javaClass?.getMethod("play")?.invoke(mediaPlayer) } catch (e: Exception) { Log.w(TAG, "play() failed: ${e.message}") }
    }

    override fun pause() {
        try { mediaPlayer?.javaClass?.getMethod("pause")?.invoke(mediaPlayer) } catch (e: Exception) { Log.w(TAG, "pause() failed: ${e.message}") }
    }

    override fun seekTo(positionMs: Long) {
        try {
            mediaPlayer?.javaClass?.getMethod("setTime", Long::class.javaPrimitiveType!!)
                ?.invoke(mediaPlayer, positionMs)
        } catch (e: Exception) { Log.w(TAG, "seekTo() failed: ${e.message}") }
    }

    override fun stop() {
        mainHandler.removeCallbacksAndMessages(null)
        try { mediaPlayer?.javaClass?.getMethod("stop")?.invoke(mediaPlayer) } catch (e: Exception) { Log.w(TAG, "stop() failed: ${e.message}") }
        hasPlayed = false
        _state.value = PlayerBackendState(PlayerState.IDLE, firstFrameRendered = false)
    }

    override fun release() = releaseInternal(incrementGeneration = true)

    private fun releaseInternal(incrementGeneration: Boolean) {
        synchronized(this) {
            if (incrementGeneration) generation += 1
            mainHandler.removeCallbacksAndMessages(null)
            val mp = mediaPlayer
            if (mp != null) {
                val pc = mp.javaClass
                runCatching { pc.getMethod("stop").invoke(mp) }
                runCatching {
                    val vout = pc.getMethod("getVLCVout").invoke(mp)
                    if (vout != null) vout.javaClass.getMethod("detachViews").invoke(vout)
                }
                runCatching { pc.getMethod("release").invoke(mp) }
            }
            // Release the Media object to free native resources and prevent
            // "VLCObject finalized but not natively released" finalizer errors.
            val media = currentMedia
            if (media != null) {
                runCatching { media.javaClass.getMethod("release").invoke(media) }
                currentMedia = null
            }
            runCatching { libVLC?.javaClass?.getMethod("release")?.invoke(libVLC) }
            mediaPlayer = null
            libVLC = null
            _playerView = null
            hasPlayed = false
            _state.value = PlayerBackendState(PlayerState.IDLE, firstFrameRendered = false)
        }
    }

    override fun currentPositionMs(): Long =
        try { (mediaPlayer?.javaClass?.getMethod("getTime")?.invoke(mediaPlayer) as? Long) ?: 0L } catch (e: Exception) { Log.w(TAG, "getTime() failed: ${e.message}"); 0L }

    override fun durationMs(): Long =
        try { (mediaPlayer?.javaClass?.getMethod("getLength")?.invoke(mediaPlayer) as? Long) ?: 0L } catch (e: Exception) { Log.w(TAG, "getLength() failed: ${e.message}"); 0L }

    override fun isPlaying(): Boolean =
        try { (mediaPlayer?.javaClass?.getMethod("isPlaying")?.invoke(mediaPlayer) as? Boolean) ?: false } catch (e: Exception) { Log.w(TAG, "isPlaying() failed: ${e.message}"); false }

    override fun forceNextFormatRetry() {}

    override fun selectAudioTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        runCatching {
            mediaPlayer?.javaClass?.getMethod("setAudioTrack", Int::class.javaPrimitiveType!!)
                ?.invoke(mediaPlayer, id)
        }.onFailure { Log.w(TAG, "setAudioTrack failed: ${it.message}") }
    }

    override fun selectSubtitleTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        runCatching {
            mediaPlayer?.javaClass?.getMethod("setSpuTrack", Int::class.javaPrimitiveType!!)
                ?.invoke(mediaPlayer, id)
        }.onFailure { Log.w(TAG, "setSpuTrack failed: ${it.message}") }
    }

    override fun disableSubtitles() {
        // VLC uses spu track id -1 to disable subtitles.
        runCatching {
            mediaPlayer?.javaClass?.getMethod("setSpuTrack", Int::class.javaPrimitiveType!!)
                ?.invoke(mediaPlayer, -1)
        }.onFailure { Log.w(TAG, "disableSubtitles failed: ${it.message}") }
    }

    override fun availableAudioTracks(): List<PlaybackAudioTrack> {
        return readTrackDescriptions("getAudioTracks", "getAudioTrack").map { (id, name, selected) ->
            PlaybackAudioTrack(
                id = id.toString(),
                name = name,
                language = null,
                channels = null,
                isSelected = selected
            )
        }
    }

    override fun availableSubtitleTracks(): List<PlaybackSubtitleTrack> {
        // VLC reports a synthetic "Disable" entry with id -1; drop it since the UI offers its own
        // "Off" control.
        return readTrackDescriptions("getSpuTracks", "getSpuTrack")
            .filter { it.first >= 0 }
            .map { (id, name, selected) ->
                PlaybackSubtitleTrack(
                    id = id.toString(),
                    name = name,
                    language = null,
                    isSelected = selected,
                    isSafeToLoad = true
                )
            }
    }

    /**
     * Best-effort reflective read of VLC's `MediaPlayer.TrackDescription[]` for [tracksMethod]
     * plus the currently selected id from [currentMethod]. Returns (id, name, isSelected) triples,
     * or an empty list on any reflection failure or when VLC is not initialised.
     */
    private fun readTrackDescriptions(
        tracksMethod: String,
        currentMethod: String
    ): List<Triple<Int, String, Boolean>> {
        val mp = mediaPlayer ?: return emptyList()
        return runCatching {
            val selectedId = runCatching {
                mp.javaClass.getMethod(currentMethod).invoke(mp) as? Int
            }.getOrNull() ?: Int.MIN_VALUE
            val array = mp.javaClass.getMethod(tracksMethod).invoke(mp) as? Array<*> ?: return emptyList()
            array.mapNotNull { td ->
                if (td == null) return@mapNotNull null
                val tdClass = td.javaClass
                val id = (tdClass.getField("id").get(td) as? Int) ?: return@mapNotNull null
                val name = (tdClass.getField("name").get(td) as? String) ?: "Track $id"
                Triple(id, name, id == selectedId)
            }
        }.getOrElse { emptyList() }
    }
}
