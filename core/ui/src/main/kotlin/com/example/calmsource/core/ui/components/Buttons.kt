package com.example.calmsource.core.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LocalReducedMotion

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val t = LocalLumenTokens.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed) 0.96f else 1.0f,
        animationSpec = if (reducedMotion) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "button-press-scale"
    )

    Button(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(t.radii.pill),
        colors = ButtonDefaults.buttonColors(
            containerColor = t.colors.foreground,
            contentColor = t.colors.background
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LumenPrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    PrimaryButton(text, onClick, modifier, enabled)
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed) 0.96f else 1.0f,
        animationSpec = if (reducedMotion) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "button-press-scale"
    )

    OutlinedButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(t.radii.pill),
        border = BorderStroke(1.dp, t.colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = t.colors.foreground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun LumenGhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    GhostButton(text, onClick, modifier)
}

/**
 * Adaptive contrast button — caller supplies a sampled luminance (0f..1f)
 * from the hero image (use coil + Palette#generateAsync on success).
 *  - lum > 0.55  -> dark text on translucent dark bg
 *  - lum <= 0.55 -> light text on translucent light bg
 *
 * This replaces the web's `mix-blend-mode: difference` trick.
 */
@Composable
fun AdaptiveButton(
    text: String,
    onClick: () -> Unit,
    backdropLuminance: Float,
    modifier: Modifier = Modifier,
) {
    val isLightBackdrop = backdropLuminance > 0.55f
    val bg = if (isLightBackdrop) Color(0xCC0B0B10) else Color(0xCCFAFAFA)
    val fg = if (isLightBackdrop) Color(0xFFFAFAFA) else Color(0xFF0B0B10)
    val reducedMotion = LocalReducedMotion.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (!reducedMotion && isPressed) 0.96f else 1.0f,
        animationSpec = if (reducedMotion) {
            androidx.compose.animation.core.snap()
        } else {
            spring(dampingRatio = 0.72f, stiffness = 380f)
        },
        label = "button-press-scale"
    )

    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier
            .defaultMinSize(minHeight = 48.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}
