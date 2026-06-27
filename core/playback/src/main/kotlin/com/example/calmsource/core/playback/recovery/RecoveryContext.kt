package com.example.calmsource.core.playback.recovery

import com.example.calmsource.core.model.AutoFallbackPolicy
import com.example.calmsource.core.model.PlaybackError
import com.example.calmsource.core.model.PlaybackSource

/**
 * Immutable snapshot of everything [PlaybackRecoveryEngine] needs to choose the
 * next recovery step. Built by [com.example.calmsource.core.playback.PlaybackManager]
 * after any codec-pruning mutation on [com.example.calmsource.core.playback.FallbackManager].
 */
data class RecoveryContext(
  val error: PlaybackError,
  val currentSource: PlaybackSource,
  val policy: AutoFallbackPolicy,
  /** [PlaybackManager]'s consecutive fallback counter *before* this failure is applied. */
  val consecutiveFailureCount: Int,
  val mimeRetryIndex: Int,
  val mimeRetrySequenceSize: Int,
  val vlcAlreadyAttemptedForSource: Boolean,
  val remainingCandidates: List<PlaybackSource>,
  val fallbackAllowed: Boolean,
  val safeProfileRetryEnabled: Boolean,
  val safeProfileAlreadyRetried: Boolean,
  val vlcRuntimeAvailable: Boolean,
  /** False in JVM unit tests where VLC surface init is unavailable. */
  val allowVlcInThisEnvironment: Boolean,
  val terminalFailureThreshold: Int = PlaybackRecoveryEngine.TERMINAL_FALLBACK_THRESHOLD,
)
