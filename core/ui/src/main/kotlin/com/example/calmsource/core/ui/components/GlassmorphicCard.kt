package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenIsTv
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LocalReducedMotion

/**
 * Shared glass card shell for posters, hero banners, and live-TV tiles.
 * TV uses D-pad focus; mobile uses press — unified as [isActive] for child content.
 */
@Composable
fun GlassmorphicCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isTv: Boolean = LocalLumenIsTv.current,
    enabled: Boolean = true,
    applyGlassFill: Boolean = true,
    cornerRadius: Dp = 16.dp,
    onFocused: (() -> Unit)? = null,
    content: @Composable BoxScope.(isActive: Boolean) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isActive = if (isTv) isFocused else isPressed

    val scale by animateFloatAsState(
        targetValue = if (isActive && !reducedMotion) 1.06f else 1f,
        animationSpec = tween(durationMillis = 250),
        label = "GlassCardScale",
    )

    val t = LocalLumenTokens.current
    val cardShape = remember(cornerRadius) { RoundedCornerShape(cornerRadius) }
    val glassBg = remember(isActive, applyGlassFill) {
        if (!applyGlassFill) {
            null
        } else if (isActive) {
            Brush.verticalGradient(listOf(Color(0xFFFFFFFF), Color(0xFFEAEAEA)))
        } else {
            Brush.verticalGradient(listOf(Color(0x66332722), Color(0xB31A1412)))
        }
    }
    val borderBrush = remember(isActive, isTv) {
        if (isActive && isTv) {
            Brush.verticalGradient(
                listOf(t.colors.brand, t.colors.brand.copy(alpha = 0.45f)),
            )
        } else {
            Brush.verticalGradient(
                listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.02f)),
            )
        }
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                clip = false
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick,
                    )
                } else {
                    Modifier
                },
            )
            .then(
                if (isTv && enabled) {
                    Modifier
                        .onFocusChanged { if (it.isFocused) onFocused?.invoke() }
                        .focusable(interactionSource = interactionSource)
                } else {
                    Modifier
                },
            )
            .border(width = 1.dp, brush = borderBrush, shape = cardShape)
            .clip(cardShape)
            .then(
                if (glassBg != null) {
                    Modifier.background(glassBg)
                } else {
                    Modifier
                },
            ),
    ) {
        content(isActive)
    }
}
