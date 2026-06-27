package com.example.calmsource.core.ui.components

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import com.example.calmsource.core.ui.theme.LumenTokens

/**
 * Frosted glass surface — blur on API 31+, translucent gradient fallback below.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = LumenTokens.Shape.lg,
    blurRadius: Dp = LumenTokens.Radius.lg,
    content: @Composable BoxScope.() -> Unit,
) {
    val glassBrush = Brush.verticalGradient(
        listOf(
            LumenTokens.Color.surfaceHi.copy(alpha = 0.55f),
            LumenTokens.Color.surface.copy(alpha = 0.88f),
        ),
    )
    val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        Modifier.blur(blurRadius)
    } else {
        Modifier
    }
    Box(
        modifier = modifier
            .clip(shape)
            .then(blurred)
            .background(glassBrush)
            .background(LumenTokens.Color.glassOverlay),
        content = content,
    )
}
