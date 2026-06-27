package com.example.calmsource.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Lumen design tokens, ported 1:1 from src/styles.css of the web app.
 * Colors are converted from oklch() to sRGB hex for Compose.
 */
@Immutable
data class LumenColors(
    val background: Color,
    val foreground: Color,
    val card: Color,
    val cardForeground: Color,
    val muted: Color,
    val mutedForeground: Color,
    val accent: Color,
    val border: Color,
    val brand: Color,             // Electric Cobalt
    val brandGlow: Color,
    val brandForeground: Color,
    val destructive: Color,
    val focusHalo: Color,         // TV focus ring
)

@Immutable
data class LumenRadii(
    val sm: Dp = 12.dp,
    val md: Dp = 14.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val pill: Dp = 999.dp,
)

@Immutable
data class LumenSpacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
    val huge: Dp = 48.dp,
)

@Immutable
data class LumenMotion(
    val fast: Int = 160,
    val base: Int = 240,
    val slow: Int = 420,
    val cinematic: Int = 520,
    val springStiffness: Float = 380f,
    val springDamping: Float = 28f,
    val reducedMotion: Boolean = false,
)

@Immutable
data class LumenTokens(
    val colors: LumenColors,
    val radii: LumenRadii = LumenRadii(),
    val spacing: LumenSpacing = LumenSpacing(),
    val motion: LumenMotion = LumenMotion(),
)

fun LumenTokens.scrimGradient(): Brush {
    return Brush.verticalGradient(
        0f to Color.Transparent,
        0.55f to Color.Transparent,
        1f to this.colors.background
    )
}

fun LumenTokens.glassSurface(
    modifier: Modifier = Modifier,
    dropBlur: Boolean = false
): Modifier {
    // Basic blur is handled on Android 12+ dynamically, on older/low-end we fall back to translucent fill
    val baseModifier = if (dropBlur) modifier else {
        // On Android 12+, we could apply Modifier.blur(12.dp), but since we support older Android versions
        // and low-end ram devices, we fallback safely to translucent fill.
        modifier
    }
    return baseModifier.background(
        Brush.verticalGradient(
            listOf(
                Color(0x99151520),
                Color(0xCC0F0F18)
            )
        )
    )
}

val LocalLumenTokens = compositionLocalOf<LumenTokens> { error("LumenTheme not installed") }

object LumenPalette {
    // Standard (dark default)
    val Standard = LumenColors(
        background      = Color(0xFF0B0B10),
        foreground      = Color(0xFFFAFAFA),
        card            = Color(0xFF17171F),
        cardForeground  = Color(0xFFFAFAFA),
        muted           = Color(0xFF1F1F28),
        mutedForeground = Color(0xFFA3A3AD),
        accent          = Color(0xFF272731),
        border          = Color(0x14FFFFFF), // white 8%
        brand           = Color(0xFF3D6BFF), // Electric Cobalt
        brandGlow       = Color(0xFF6E8DFF),
        brandForeground = Color(0xFFFCFCFC),
        destructive     = Color(0xFFE5484D),
        focusHalo       = Color(0x803D6BFF),
    )

    // OLED true-black variant (matches :root[data-theme="oled"])
    val Oled = Standard.copy(
        background = Color(0xFF000000),
        card       = Color(0xFF0B0B12),
        muted      = Color(0xFF14141B),
    )
}

object LumenShapes {
    val sm get() = RoundedCornerShape(12.dp)
    val md get() = RoundedCornerShape(14.dp)
    val lg get() = RoundedCornerShape(16.dp)
    val xl get() = RoundedCornerShape(20.dp)
    val xxl get() = RoundedCornerShape(24.dp)
}
