// core/ui/src/main/kotlin/com/example/calmsource/core/ui/components/TvFocusable.kt
package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

/**
 * Wrap any composable to give it Lumen TV focus treatment:
 *   - scale 1.06 lift with spring
 *   - 2dp brand-colored ring
 *   - elevated shadow
 *
 * IMPORTANT: this extends the existing TvPressable helper used by Mission27LiveParityTest —
 * do not delete TvPressable when adopting this; call TvFocusable from within it instead,
 * keeping the existing test contract.
 */
@Composable
fun TvFocusable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    cornerRadius: androidx.compose.ui.unit.Dp = 16.dp,
    content: @Composable () -> Unit,
) {
    val t = LocalLumenTokens.current
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val scale by animateFloatAsState(
        targetValue = if (focused) 1.06f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = t.motion.springStiffness),
        label = "tv-focus-lift",
    )
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .shadow(if (focused) 22.dp else 6.dp, shape, clip = false)
            .clip(shape)
            .then(if (focused) Modifier.border(2.dp, t.colors.brand, shape) else Modifier)
            .focusable(interactionSource = interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .background(Color.Transparent),
    ) { content() }
}
