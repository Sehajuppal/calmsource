package com.example.calmsource.core.playback.watchdog

import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource
import com.example.calmsource.core.model.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger

/**
 * Owns startup timeouts, buffering-stall timers, first-frame freeze detection, and the
 * debounced success signal. All timers are session-aware and disarmed via [cancelAll].
 */
internal class PlaybackWatchdogController(
    private val scope: CoroutineScope,
    private val currentSessionId: () -> Long,
    private val isSessionCurrent: (Long) -> Boolean,
    private val onFailure: (PlaybackError, String) -> Unit,
    private val onRecordSuccess: (PlaybackSource) -> Unit,
    private val startupTimeoutMs: () -> Long,
    private val bufferingStallDelayMs: () -> Long,
    private val currentPlayerState: () -> PlayerState,
    private val currentSource: () -> PlaybackSource?,
    private val hasRenderedFirstFrame: () -> Boolean,
    private val shouldScheduleFreezeWatchdog: () -> Boolean,
    private val isFreezeWatchdogStillValid: () -> Boolean,
) {
    private val successGeneration = AtomicInteger(0)
    private val timeoutGeneration = AtomicInteger(0)

    private var successJob: Job? = null
    private var timeoutJob: Job? = null
    private var freezeWatchdogJob: Job? = null
    private var bufferingWatchdogJob: Job? = null

    var successRecorded: Boolean = false
        private set

    fun onUiStateChanged(playerState: PlayerState) {
        trackPlayingSuccess(playerState)
        trackStartupTimeout(playerState)
    }

    fun scheduleFreezeWatchdogIfNeeded() {
        if (!shouldScheduleFreezeWatchdog()) return
        val sessionId = currentSessionId()
        freezeWatchdogJob?.cancel()
        freezeWatchdogJob = scope.launch(Dispatchers.Main) {
            delay(FREEZE_WATCHDOG_MS)
            if (!isSessionCurrent(sessionId)) return@launch
            if (isFreezeWatchdogStillValid()) {
                onFailure(
                    PlaybackError.DecoderError(
                        cause = Exception(
                            "Video playback frozen or audio-only stall detected " +
                                "(first frame not rendered within ${FREEZE_WATCHDOG_MS / 1000}s)"
                        )
                    ),
                    "VIDEO_FREEZE"
                )
            }
        }
    }

    fun startBufferingWatchdog() {
        val sessionId = currentSessionId()
        bufferingWatchdogJob?.cancel()
        val stallDelayMs = bufferingStallDelayMs()
        bufferingWatchdogJob = scope.launch(Dispatchers.Main) {
            delay(stallDelayMs)
            if (!isSessionCurrent(sessionId)) return@launch
            if (currentPlayerState() == PlayerState.BUFFERING) {
                val isInitialBuffering = !hasRenderedFirstFrame()
                onFailure(
                    PlaybackError.Timeout(
                        cause = Exception(
                            if (isInitialBuffering) "Initial buffering stall watchdog (${stallDelayMs / 1000}s)"
                            else "Mid-stream buffering stall watchdog (${stallDelayMs / 1000}s)"
                        )
                    ),
                    if (isInitialBuffering) "BUFFERING_STALL" else "MIDSTREAM_BUFFERING_STALL"
                )
            }
        }
    }

    fun cancelFreezeWatchdog() {
        freezeWatchdogJob?.cancel()
        freezeWatchdogJob = null
    }

    fun cancelBufferingWatchdog() {
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
    }

    fun cancelAll() {
        timeoutGeneration.incrementAndGet()
        timeoutJob?.cancel()
        timeoutJob = null
        freezeWatchdogJob?.cancel()
        freezeWatchdogJob = null
        bufferingWatchdogJob?.cancel()
        bufferingWatchdogJob = null
        disarmSuccessTracking()
    }

    fun onSourceChanged() {
        successRecorded = false
        disarmSuccessTracking()
        disarmStartupTimeout()
    }

    private fun trackPlayingSuccess(playerState: PlayerState) {
        if (playerState == PlayerState.PLAYING) {
            if (successJob == null && !successRecorded) {
                val sessionId = currentSessionId()
                val currentGen = successGeneration.incrementAndGet()
                successJob = scope.launch(Dispatchers.Main) {
                    delay(SUCCESS_DEBOUNCE_MS)
                    if (successGeneration.get() != currentGen) return@launch
                    if (!isSessionCurrent(sessionId)) return@launch
                    val source = currentSource() ?: return@launch
                    onRecordSuccess(source)
                    successRecorded = true
                }
            }
        } else {
            disarmSuccessTracking()
        }
    }

    private fun trackStartupTimeout(playerState: PlayerState) {
        if (playerState == PlayerState.PREPARING || playerState == PlayerState.BUFFERING) {
            if (timeoutJob?.isActive != true) {
                val sessionId = currentSessionId()
                val currentGen = timeoutGeneration.incrementAndGet()
                val timeoutMs = startupTimeoutMs()
                timeoutJob = scope.launch(Dispatchers.Main) {
                    delay(timeoutMs)
                    if (timeoutGeneration.get() != currentGen) return@launch
                    if (!isSessionCurrent(sessionId)) return@launch
                    timeoutJob = null
                    if (currentSource() != null) {
                        onFailure(
                            PlaybackError.Timeout(
                                cause = Exception(
                                    "Playback startup timed out after ${timeoutMs / 1000} seconds"
                                )
                            ),
                            "PLAYBACK_TIMEOUT"
                        )
                    }
                }
            }
        } else {
            disarmStartupTimeout()
        }
    }

    private fun disarmSuccessTracking() {
        if (successJob != null) {
            successGeneration.incrementAndGet()
            successJob?.cancel()
            successJob = null
        }
    }

    private fun disarmStartupTimeout() {
        timeoutGeneration.incrementAndGet()
        timeoutJob?.cancel()
        timeoutJob = null
    }

    companion object {
        private const val FREEZE_WATCHDOG_MS = 4_000L
        private const val SUCCESS_DEBOUNCE_MS = 5_000L
    }
}
