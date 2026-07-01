package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun Modifier.shimmer(
    visible: Boolean = true,
    shape: Shape = MaterialTheme.shapes.medium
): Modifier {
    if (!visible) return this
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer-translate"
    )
    
    val shimmerColors = listOf(
        Color.White.copy(alpha = 0.0f),
        Color.White.copy(alpha = 0.08f),
        Color.White.copy(alpha = 0.0f),
    )
    
    // Pre-allocate the shimmer brush in the composition phase instead of creating
    // a new Brush.linearGradient on every frame inside drawWithContent.
    val shimmerBrush = remember(translateAnim) {
        Brush.linearGradient(
            colors = shimmerColors,
            start = Offset(translateAnim - 300f, 0f),
            end = Offset(translateAnim, 300f)
        )
    }
    
    return this
        .clip(shape)
        .drawWithContent {
            drawContent()
            drawRect(brush = shimmerBrush)
        }
}

@Composable
fun Skeleton(modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    Box(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(t.colors.muted)
            .shimmer()
    )
}
