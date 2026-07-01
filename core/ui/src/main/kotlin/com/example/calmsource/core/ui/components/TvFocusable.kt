package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LumenElevation
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = LumenTokens.Radius.lg,
    showFocusRing: Boolean = true,
    onFocused: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val t = LocalLumenTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = when {
            pressed -> 1.015f
            focused -> LumenTokens.Focus.scale
            else -> 1f
        },
        animationSpec = LumenTokens.Springs.Emphasized,
        label = "tv-focus-lift",
    )
    val lift by animateDpAsState(
        targetValue = if (focused && !pressed) LumenTokens.Focus.liftY else 0.dp,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = 600f),
        label = "tv-focus-translation",
    )
    val glowAlpha by animateFloatAsState(
        targetValue = if (focused) 1f else 0f,
        animationSpec = LumenTokens.Springs.Snappy,
        label = "tv-focus-glow",
    )
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationY = lift.toPx()
            }
            .shadow(if (focused && showFocusRing) LumenElevation.xl else LumenElevation.md, shape, clip = false)
            .drawBehind {
                if (showFocusRing && glowAlpha > 0f) {
                    val glowRadiusPx = LumenTokens.Focus.ringGlow.toPx()
                    val ringColor = t.colors.brand.copy(alpha = glowAlpha)
                    val cornerRadiusPx = cornerRadius.toPx()
                    drawRoundRect(
                        color = ringColor.copy(alpha = ringColor.alpha * 0.18f),
                        topLeft = androidx.compose.ui.geometry.Offset(-glowRadiusPx / 2f, -glowRadiusPx / 2f),
                        size = androidx.compose.ui.geometry.Size(
                            width = size.width + glowRadiusPx,
                            height = size.height + glowRadiusPx,
                        ),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx + glowRadiusPx / 2f),
                        style = Stroke(width = glowRadiusPx),
                    )
                    drawRoundRect(
                        color = ringColor,
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadiusPx),
                        style = Stroke(width = LumenTokens.Focus.ringStroke.toPx()),
                    )
                }
            }
            .clip(shape)
            .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .focusable(interactionSource = interaction),
    ) { content() }
}
