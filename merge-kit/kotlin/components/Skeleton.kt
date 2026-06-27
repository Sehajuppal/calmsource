// core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/Skeleton.kt
package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    val transition = rememberInfiniteTransition(label = "skeleton")
    val x by transition.animateFloat(
        initialValue = -400f, targetValue = 800f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart),
        label = "shimmer",
    )
    androidx.compose.foundation.layout.Box(
        modifier
            .clip(MaterialTheme.shapes.medium)
            .background(t.colors.muted),
    ) {
        androidx.compose.foundation.layout.Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.5f to Color.White.copy(alpha = 0.06f),
                    1f to Color.Transparent,
                    startX = x, endX = x + 400f,
                )
            )
        )
    }
}
