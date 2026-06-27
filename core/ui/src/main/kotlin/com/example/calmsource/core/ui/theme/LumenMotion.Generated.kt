// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
package com.example.calmsource.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween

object LumenMotionTokens {
    val standardEasing = FastOutSlowInEasing
    val focusEasing = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    val appleOut = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
    val tileEase = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    fun focusTween() = tween<Float>(durationMillis = LumenTokensGenerated.Motion.focusMs, easing = focusEasing)
    fun emphasizedSpring() = spring<Float>(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = LumenTokensGenerated.Motion.springStiffness)
}
