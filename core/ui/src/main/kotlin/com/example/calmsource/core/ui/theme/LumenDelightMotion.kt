package com.example.calmsource.core.ui.theme

import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect

/**
 * Compose transition specs for Lumen screen transitions and chrome micro-delight.
 * All timings honor [LocalReducedMotion].
 */
object LumenDelightMotion {
    fun screenEnterTransition(reducedMotion: Boolean): EnterTransition {
        if (reducedMotion) return fadeIn(snap())
        return fadeIn(
            tween(
                durationMillis = LumenTokens.Duration.cinematic,
                easing = LumenTokens.Easing.AppleOut,
            ),
        )
    }

    fun screenExitTransition(reducedMotion: Boolean): ExitTransition {
        if (reducedMotion) return fadeOut(snap())
        return fadeOut(
            tween(
                durationMillis = LumenTokens.Duration.focus,
                easing = LumenTokens.Easing.AppleOut,
            ),
        )
    }

    fun sharedBoundsSpec(reducedMotion: Boolean): FiniteAnimationSpec<Rect> {
        if (reducedMotion) return snap()
        return tween(
            durationMillis = LumenTokens.Duration.cinematic,
            easing = LumenTokens.Easing.AppleOut,
        )
    }

    fun detailsBackdropFadeSpec(reducedMotion: Boolean): AnimationSpec<Float> {
        if (reducedMotion) return snap()
        return tween(
            durationMillis = LumenTokens.Duration.focus,
            easing = LumenTokens.Easing.AppleOut,
        )
    }

    fun playerMinimizeSpec(reducedMotion: Boolean): AnimationSpec<Float> {
        if (reducedMotion) return snap()
        return tween(
            durationMillis = LumenTokens.Duration.emphasized,
            easing = LumenTokens.Easing.AppleOut,
        )
    }

    fun miniPlayerEnterTransition(reducedMotion: Boolean): EnterTransition {
        if (reducedMotion) return fadeIn(snap())
        return slideInVertically(
            initialOffsetY = { fullHeight -> fullHeight },
            animationSpec = spring(
                dampingRatio = 0.85f,
                stiffness = 220f,
            ),
        ) + fadeIn(
            tween(
                durationMillis = LumenTokens.Duration.focus,
                easing = LumenTokens.Easing.AppleOut,
            ),
        )
    }

    fun miniPlayerExitTransition(reducedMotion: Boolean): ExitTransition {
        if (reducedMotion) return fadeOut(snap())
        return slideOutVertically(
            targetOffsetY = { fullHeight -> fullHeight },
            animationSpec = tween(
                durationMillis = LumenTokens.Duration.focus,
                easing = LumenTokens.Easing.AppleOut,
            ),
        ) + fadeOut(
            tween(
                durationMillis = LumenTokens.Duration.fast,
                easing = LumenTokens.Easing.AppleOut,
            ),
        )
    }
}

@Composable
fun rememberLumenSharedBoundsTransform(): BoundsTransform {
    val reducedMotion = LocalReducedMotion.current
    return remember(reducedMotion) {
        BoundsTransform { _, _ -> LumenDelightMotion.sharedBoundsSpec(reducedMotion) }
    }
}
