package com.example.calmsource.core.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

enum class LumenVariant { Standard, Oled }

@Composable
fun LumenTheme(
    variant: LumenVariant = LumenVariant.Standard,
    isTv: Boolean = false,
    content: @Composable () -> Unit,
) {
    val colors = when (variant) {
        LumenVariant.Standard -> LumenPalette.Standard
        LumenVariant.Oled     -> LumenPalette.Oled
    }
    val tokens = LumenTokens(colors = colors)

    val material = darkColorScheme(
        background          = colors.background,
        onBackground        = colors.foreground,
        surface             = colors.card,
        onSurface           = colors.cardForeground,
        surfaceVariant      = colors.muted,
        onSurfaceVariant    = colors.mutedForeground,
        primary             = colors.brand,
        onPrimary           = colors.brandForeground,
        secondary           = colors.accent,
        onSecondary         = colors.foreground,
        error               = colors.destructive,
        onError             = colors.foreground,
        outline             = colors.border,
    )

    CompositionLocalProvider(LocalLumenTokens provides tokens) {
        MaterialTheme(
            colorScheme = material,
            typography  = lumenTypography(isTv = isTv),
            shapes      = androidx.compose.material3.Shapes(
                small      = LumenShapes.sm,
                medium     = LumenShapes.md,
                large      = LumenShapes.lg,
                extraLarge = LumenShapes.xl,
            ),
            content = content,
        )
    }
}
