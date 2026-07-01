// core/ui/src/main/kotlin/com/calmsource/core/ui/components/GlassSurface.kt
//
// Lumen glass surface. Uses RenderEffect.createBlurEffect on API 31+ for true
// background blur. On older devices falls back to a tinted translucent surface
// (still legible, no perf cost). Honors `perfMode == LOW` to skip the blur even
// on capable devices when battery saver or low-end hint is set.
//
// Usage:
//   GlassSurface(
//       modifier = Modifier.fillMaxWidth(),
//       shape = LumenTokens.Shape.lg,
//       strong = false,
//   ) { Row(...) }

package com.example.calmsource.core.ui.components

import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.RenderEffect as ComposeRenderEffect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LumenTokens

enum class PerfMode { Auto, Low }
val LocalPerfMode = compositionLocalOf { PerfMode.Auto }

/**
 * @param blurRadius platform blur radius in dp. Ignored on API < 31 or when perf=Low.
 * @param strong use stronger tint+blur for sheet/dialog surfaces.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: androidx.compose.ui.graphics.Shape = LumenTokens.Shape.lg,
    strong: Boolean = false,
    blurRadius: Dp = if (strong) 40.dp else 24.dp,
    borderColor: Color = LumenTokens.Color.borderSubtle,
    content: @Composable () -> Unit,
) {
    val perf = LocalPerfMode.current
    val canBlur = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && perf != PerfMode.Low
    val tint = if (strong) LumenTokens.Color.glassStrong else LumenTokens.Color.glass
    // When blur is unavailable, push tint opacity up so legibility stays equivalent.
    val effectiveTint = if (canBlur) tint else tint.copy(alpha = (tint.alpha + 0.18f).coerceAtMost(0.96f))

    val blurModifier = if (canBlur) {
        // Pre-allocate the RenderEffect in the composition phase instead of
        // creating a new one on every frame inside graphicsLayer.
        val blurEffect = remember(blurRadius) {
            val radiusPx = blurRadius.value * android.content.res.Resources.getSystem().displayMetrics.density
            if (radiusPx > 0f) {
                RenderEffect
                    .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                    .asComposeRenderEffect()
            } else {
                null
            }
        }
        Modifier.graphicsLayer {
            renderEffect = blurEffect
        }
    } else Modifier

    Box(modifier = modifier.clip(shape)) {
        // Background-blur layer sits behind content. The blur reads what is composited
        // beneath this Box (the parent painter / image), so place GlassSurface above
        // the content you want to blur (e.g. above the hero artwork).
        Box(
            Modifier
                .matchParentSize()
                .then(blurModifier)
                .background(effectiveTint)
        )
        Box(
            Modifier
                .matchParentSize()
                .border(width = 1.dp, color = borderColor, shape = shape)
        )
        content()
    }
}

/** Vertical scrim used under hero text — pure Compose, no blur required. */
@Composable
fun HeroBottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.verticalGradient(
                0.45f to Color.Transparent,
                0.92f to Color(0xD908080C),
                1.0f  to LumenTokens.Color.bg,
            )
        )
    )
}

@Composable
fun HeroLeftScrim(modifier: Modifier = Modifier) {
    Box(
        modifier.background(
            Brush.horizontalGradient(
                0.0f  to Color(0xC7000000),
                0.35f to Color(0x59000000),
                0.62f to Color.Transparent,
            )
        )
    )
}

/** Wrap your root content tree so any GlassSurface can opt into low-perf mode. */
@Composable
fun ProvidePerfMode(mode: PerfMode, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPerfMode provides mode) { content() }
}
