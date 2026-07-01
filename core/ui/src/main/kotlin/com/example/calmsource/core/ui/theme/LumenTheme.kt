package com.example.calmsource.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.text.TextStyle

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
    val scale = if (isTv) LumenType.TV_SCALE else 1f
    val reducedMotion = isReducedMotionEnabled(androidx.compose.ui.platform.LocalContext.current)

    val material = androidx.compose.material3.darkColorScheme(
        background = colors.background,
        onBackground = colors.foreground,
        surface = colors.card,
        onSurface = colors.cardForeground,
        surfaceVariant = colors.muted,
        onSurfaceVariant = colors.mutedForeground,
        primary = colors.brand,
        onPrimary = colors.brandForeground,
        secondary = colors.accent,
        onSecondary = colors.foreground,
        error = colors.destructive,
        onError = colors.foreground,
        outline = colors.border,
    )

    CompositionLocalProvider(
        LocalLumenTokens provides tokens,
        LocalLumenIsTv provides isTv,
        LocalReducedMotion provides reducedMotion,
        androidx.compose.material3.LocalContentColor provides LumenTokens.Color.textPrimary,
    ) {
        val shapes = androidx.compose.material3.Shapes(
            small = LumenTokens.Shape.sm,
            medium = LumenTokens.Shape.md,
            large = LumenTokens.Shape.lg,
            extraLarge = LumenTokens.Shape.xl,
        )
        androidx.compose.material3.MaterialTheme(
            colorScheme = material,
            typography = lumenTypography(scale),
            shapes = shapes,
            content = content,
        )
    }
}

private fun lumenTypography(scale: Float): Typography {
    fun TextStyle.scaled() = copy(
        fontSize = fontSize * scale,
        lineHeight = lineHeight * scale,
        letterSpacing = letterSpacing * scale,
    )
    return Typography(
        displayLarge = LumenType.Display.toTextStyle(scale),
        displayMedium = LumenType.H1.toTextStyle(scale),
        displaySmall = LumenType.H2.toTextStyle(scale),
        headlineLarge = LumenType.H2.toTextStyle(scale),
        headlineMedium = LumenType.Title.toTextStyle(scale),
        headlineSmall = LumenType.Title.toTextStyle(scale),
        titleLarge = LumenType.Title.toTextStyle(scale),
        titleMedium = LumenType.RowTitle.toTextStyle(scale),
        bodyLarge = LumenType.Body.toTextStyle(scale),
        bodyMedium = LumenType.Caption.toTextStyle(scale),
        labelLarge = LumenType.Caption.toTextStyle(scale),
        labelSmall = LumenType.Eyebrow.toTextStyle(scale),
    )
}
