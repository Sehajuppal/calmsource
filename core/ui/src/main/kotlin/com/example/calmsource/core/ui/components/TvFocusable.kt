package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.example.calmsource.core.ui.theme.LumenElevation
import com.example.calmsource.core.ui.theme.LumenMotion
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LumenTokens.Radius.lg,
    onFocused: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val t = LocalLumenTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) LumenMotion.focusScale else 1f,
        animationSpec = LumenTokens.Springs.Emphasized,
        label = "tv-focus-lift",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (focused) 0.6f else 0f,
        animationSpec = LumenTokens.Springs.Snappy,
        label = "tv-focus-glow",
    )
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(if (focused) LumenElevation.xl else LumenElevation.md, shape, clip = false)
            .clip(shape)
            .background(
                if (focused) t.colors.focusHalo.copy(alpha = glowAlpha) else Color.Transparent,
            )
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick),
    ) { content() }
}
