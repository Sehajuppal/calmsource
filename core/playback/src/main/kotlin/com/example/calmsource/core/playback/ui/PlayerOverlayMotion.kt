package com.example.calmsource.core.playback.ui

import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

/**
 * Shared, lightweight fade timings for player overlay show/hide.
 */
object PlayerOverlayMotion {
  const val FadeInMs = 160
  const val FadeOutMs = 200

  val fadeIn = fadeIn(animationSpec = tween(FadeInMs))
  val fadeOut = fadeOut(animationSpec = tween(FadeOutMs))
}
