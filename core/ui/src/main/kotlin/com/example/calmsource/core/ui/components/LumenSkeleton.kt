package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun LumenSkeleton(
    modifier: Modifier = Modifier,
    shimmer: Boolean = true
) {
    val t = LocalLumenTokens.current
    val isReduced = t.motion.reducedMotion

    if (!shimmer || isReduced) {
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(t.colors.muted)
        )
    } else {
        val transition = rememberInfiniteTransition(label = "lumen-skeleton")
        val x by transition.animateFloat(
            initialValue = -400f,
            targetValue = 800f,
            animationSpec = infiniteRepeatable(
                animation = tween(1400, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer-offset"
        )
        Box(
            modifier = modifier
                .clip(MaterialTheme.shapes.medium)
                .background(t.colors.muted)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            startX = x,
                            endX = x + 400f
                        )
                    )
            )
        }
    }
}
