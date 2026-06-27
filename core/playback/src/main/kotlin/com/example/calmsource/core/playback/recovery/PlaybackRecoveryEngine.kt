package com.example.calmsource.core.playback.recovery

import androidx.media3.common.PlaybackException
import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackError

/**
 * Pure recovery policy: given a failure and session snapshot, returns the next
 * step in the chain MIME → VLC → safe profile → next source → terminal.
 *
 * No I/O, coroutines, or player mutations — [com.example.calmsource.core.playback.PlaybackManager]
 * executes the returned [RecoveryDecision].
 */
object PlaybackRecoveryEngine {

  const val TERMINAL_FALLBACK_THRESHOLD = 5

  fun decide(context: RecoveryContext): RecoveryDecision {
    val error = context.error

    if (error is PlaybackError.UnsupportedFormat &&
      context.mimeRetryIndex < context.mimeRetrySequenceSize
    ) {
      return RecoveryDecision(action = RecoveryAction.TryMimeRetry)
    }

    if (isVlcEligible(context)) {
      return RecoveryDecision(action = RecoveryAction.SwitchToVlc)
    }

    if (error is PlaybackError.DecoderError &&
      context.safeProfileRetryEnabled &&
      !context.safeProfileAlreadyRetried
    ) {
      return RecoveryDecision(action = RecoveryAction.RetrySafeProfile)
    }

    return decideAfterLocalRetriesExhausted(context)
  }

  fun decideWithoutSource(error: PlaybackError): RecoveryDecision {
    return RecoveryDecision(action = RecoveryAction.FailWithoutSource(error))
  }

  private fun isVlcEligible(context: RecoveryContext): Boolean {
    if (!context.allowVlcInThisEnvironment || !context.vlcRuntimeAvailable) return false
    if (context.vlcAlreadyAttemptedForSource) return false

    val error = context.error
    val mimeRetriesExhausted =
      error !is PlaybackError.UnsupportedFormat || context.mimeRetryIndex >= context.mimeRetrySequenceSize

    return mimeRetriesExhausted && when (error) {
      is PlaybackError.UnsupportedFormat,
      is PlaybackError.Timeout -> true
      is PlaybackError.DecoderError ->
        isAudioDecoderError(error) || context.remainingCandidates.isEmpty()
      else -> false
    }
  }

  internal fun isAudioDecoderError(error: PlaybackError): Boolean {
    if (error !is PlaybackError.DecoderError) return false
    val ex = playbackExceptionFrom(error) ?: return false
    if (ex.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_INIT_FAILED ||
      ex.errorCode == PlaybackException.ERROR_CODE_AUDIO_TRACK_WRITE_FAILED
    ) {
      return true
    }
    val message = buildString {
      append(ex.message.orEmpty())
      var cause = ex.cause
      while (cause != null) {
        append(' ')
        append(cause.message.orEmpty())
        cause = cause.cause
      }
    }.lowercase()
    return message.contains("mediacodecaudiorenderer") ||
      message.contains("audio/eac3") ||
      message.contains("audio/ac3") ||
      message.contains("audio/vnd.dts")
  }

  private fun playbackExceptionFrom(error: PlaybackError): PlaybackException? {
    var cause: Throwable? = error.cause
    while (cause != null) {
      if (cause is PlaybackException) return cause
      cause = cause.cause
    }
    return null
  }

  private fun decideAfterLocalRetriesExhausted(context: RecoveryContext): RecoveryDecision {
    val error = context.error
    val nextConsecutiveCount = context.consecutiveFailureCount + 1

    if (nextConsecutiveCount >= context.terminalFailureThreshold) {
      val terminalError = PlaybackError.TerminalError(
        message = "Multiple sources failed consecutively. Please pick a manual link.",
        cause = error.cause ?: Exception(error.message)
      )
      return RecoveryDecision(
        action = RecoveryAction.Terminal(terminalError),
        recordSourceFailure = true,
        recordTunnelingFailure = true,
      )
    }

    if (context.fallbackAllowed) {
      return when (context.policy) {
        AutoFallbackPolicy.ASK_BEFORE_FALLBACK -> RecoveryDecision(
          action = RecoveryAction.ShowFallbackPrompt(error),
          recordSourceFailure = true,
          recordTunnelingFailure = true,
        )
        AutoFallbackPolicy.AUTO_FALLBACK_ONCE,
        AutoFallbackPolicy.AUTO_FALLBACK_LIMITED -> {
          val message = if (error is PlaybackError.UnsupportedFormat || error is PlaybackError.DecoderError) {
            "Source incompatible. Trying alternative track..."
          } else {
            "Source offline. Trying alternative track..."
          }
          RecoveryDecision(
            action = RecoveryAction.AutoFallback(error, message),
            recordSourceFailure = true,
            recordTunnelingFailure = true,
          )
        }
        AutoFallbackPolicy.OFF -> RecoveryDecision(
          action = RecoveryAction.FailInPlace(error),
          recordSourceFailure = true,
          recordTunnelingFailure = true,
        )
      }
    }

    val finalError = if (error is PlaybackError.UnsupportedFormat) {
      PlaybackError.UnsupportedFormat(
        message = error.message,
        cause = error.cause,
        retryableSources = context.remainingCandidates,
      )
    } else {
      error
    }
    return RecoveryDecision(
      action = RecoveryAction.FailInPlace(finalError),
      recordSourceFailure = true,
      recordTunnelingFailure = true,
    )
  }
}
