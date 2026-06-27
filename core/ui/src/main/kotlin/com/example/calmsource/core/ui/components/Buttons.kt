package com.example.calmsource.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val t = LocalLumenTokens.current
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(t.radii.pill),
        colors = ButtonDefaults.buttonColors(containerColor = t.colors.foreground, contentColor = t.colors.background),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 15.sp) }
}

@Composable
fun GhostButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val t = LocalLumenTokens.current
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(t.radii.pill),
        border = BorderStroke(1.dp, t.colors.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent, contentColor = t.colors.foreground),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 15.sp) }
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
    Button(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        colors = ButtonDefaults.buttonColors(containerColor = bg, contentColor = fg),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { Text(text, fontSize = 15.sp) }
}
