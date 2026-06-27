package com.example.calmsource.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable

enum class LumenVariant { Standard, Oled }

@Composable
fun LumenTheme(
    variant: LumenVariant = LumenVariant.Standard,
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when (variant) {
        LumenVariant.Standard -> LumenPalette.Standard
        LumenVariant.Oled -> LumenPalette.Oled
    }
    val tokens = LumenTokenBundle(colors = colors)
    val typeScale = buildLumenTypeScale(isTv = isTv)

    val material = darkColorScheme(
        background = LumenTokens.Color.bg,
        onBackground = LumenTokens.Color.textPrimary,
        surface = LumenTokens.Color.surface,
        onSurface = LumenTokens.Color.textPrimary,
        surfaceVariant = LumenTokens.Color.surfaceHi,
        onSurfaceVariant = LumenTokens.Color.textSecondary,
        primary = LumenTokens.Color.brand,
        onPrimary = LumenTokens.Color.textPrimary,
        secondary = LumenTokens.Color.surfaceHi,
        onSecondary = LumenTokens.Color.textPrimary,
        error = LumenTokens.Color.danger,
        onError = LumenTokens.Color.textPrimary,
        outline = LumenTokens.Color.border,
    )

    CompositionLocalProvider(
        LocalLumenTokens provides tokens,
        LocalLumenIsTv provides isTv,
        LocalLumenTypeScale provides typeScale,
        androidx.compose.material3.LocalContentColor provides LumenTokens.Color.textPrimary,
    ) {
        MaterialTheme(
            colorScheme = material,
            typography = lumenTypography(isTv = isTv),
            shapes = androidx.compose.material3.Shapes(
                small = LumenTokens.Shape.sm,
                medium = LumenTokens.Shape.md,
                large = LumenTokens.Shape.lg,
                extraLarge = LumenTokens.Shape.xl,
            ),
            content = content,
        )
    }
}
