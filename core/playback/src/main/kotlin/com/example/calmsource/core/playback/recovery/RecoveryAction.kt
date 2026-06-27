package com.example.calmsource.core.playback.recovery

import com.example.calmsource.core.model.PlaybackError

/**
 * Side-effect-free recovery decision produced by [PlaybackRecoveryEngine].
 * [com.example.calmsource.core.playback.PlaybackManager] executes these actions.
 */
sealed interface RecoveryAction {
  /** Retry the current source with the next explicit MIME container hint. */
  data object TryMimeRetry : RecoveryAction

  /** Hand off the current source to the VLC backend. */
  data object SwitchToVlc : RecoveryAction

  /** Re-prepare on the current source using the fallback-safe decoder profile. */
  data object RetrySafeProfile : RecoveryAction

  /** Surface the error and wait for the user to approve source fallback. */
  data class ShowFallbackPrompt(val error: PlaybackError) : RecoveryAction

  /**
   * Automatically advance to the next fallback candidate.
   * [message] is shown briefly in the player UI.
   */
  data class AutoFallback(val error: PlaybackError, val message: String) : RecoveryAction

  /** Consecutive failure threshold reached — stop trying silently. */
  data class Terminal(val error: PlaybackError.TerminalError) : RecoveryAction

  /** No further recovery is permitted; show [error] (optionally enriched). */
  data class FailInPlace(val error: PlaybackError) : RecoveryAction

  /** No active source — fail with the raw error. */
  data class FailWithoutSource(val error: PlaybackError) : RecoveryAction
}

/**
 * Bundles the chosen [action] with side-effect hints for the orchestrator.
 */
data class RecoveryDecision(
  val action: RecoveryAction,
  /**
   * When true the orchestrator should mark the current source failed, bump the
   * consecutive-failure counter, and record health before applying UI effects.
   */
  val recordSourceFailure: Boolean = false,
  /** When true the orchestrator should record an active tunneling key as failed. */
  val recordTunnelingFailure: Boolean = false,
)
