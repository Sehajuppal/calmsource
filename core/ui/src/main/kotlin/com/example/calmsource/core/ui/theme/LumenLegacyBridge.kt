package com.example.calmsource.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Screen layout dimensions — not in tokens/lumen.json. */
object LumenLayout {
    val hairline: Dp = 0.5.dp
    val heroHeightMobile: Dp = 450.dp
    val heroStripHeight: Dp = 180.dp
    val bottomNavPadding: Dp = 72.dp
    val posterTileWidth: Dp = 160.dp
    val posterTileHeightTv: Dp = 210.dp
    val posterHeightTv: Dp = 270.dp
    val channelPanelWidth: Dp = 200.dp
    val sheetMaxWidth: Dp = 400.dp
    val panelWidthTv: Dp = 380.dp
    val buttonHeight: Dp = 50.dp
    val skeletonTitleWidth: Dp = 150.dp
    val skeletonChipWidth: Dp = 70.dp
    val detailsSkeletonHero: Dp = 260.dp
    val clearButtonWidthTv: Dp = 110.dp
    val inputWidthSm: Dp = 100.dp
    val inputWidthXs: Dp = 90.dp
    val epgMinBlockWidth: Dp = 120.dp
    val epgMinBlockWidthTv: Dp = 140.dp
    val avatarLg: Dp = 64.dp
    val iconMd: Dp = 18.dp
    val iconXl: Dp = 48.dp
    val offsetLg: Dp = 36.dp
    val spacerMd: Dp = 30.dp
    val progressHeight: Dp = 5.dp
    val guideRowHeight: Dp = 100.dp
    val extensionPreviewHeight: Dp = 150.dp
    val playerSheetMaxWidth: Dp = 500.dp
    val heroHeightLg: Dp = 520.dp
    val detailsHeroHeight: Dp = 340.dp
    val detailsContentTop: Dp = 240.dp
    val tileWidthMd: Dp = 220.dp
    val pinSheetWidth: Dp = 420.dp
    val settingsNavWidth: Dp = 240.dp
    val discoveryPanelWidth: Dp = 520.dp
    val playerMenuWidth: Dp = 320.dp
    val playerControlSize: Dp = 56.dp
    val playerControlIcon: Dp = 26.dp
    val epgTimeColumnWidth: Dp = 68.dp
    val epgRowHeight: Dp = 44.dp
    val epgBlockHeight: Dp = 84.dp
    val epgBlockHeightTv: Dp = 68.dp
    val channelLogoSize: Dp = 84.dp
    val channelLogoInner: Dp = 54.dp
    val channelRowHeight: Dp = 96.dp
    val avatarMd: Dp = 42.dp
    val avatarPicker: Dp = 54.dp
    val iconSm: Dp = 22.dp
    val qrSize: Dp = 90.dp
}

object LumenMotion {
    const val standardMs: Int = LumenTokens.Duration.standard
    const val emphasizedMs: Int = LumenTokens.Duration.emphasized
    const val focusScaleMs: Int = LumenTokens.Duration.focus
    const val maxFadeMs: Int = LumenTokens.Duration.cinematic
    const val springStiffness: Float = 400f
    const val springDamping: Float = 0.7f
    const val focusScale: Float = LumenTokens.Focus.scale
}

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
    val brand: Color,
    val brandGlow: Color,
    val brandForeground: Color,
    val destructive: Color,
    val focusHalo: Color,
    val success: Color,
    val warning: Color,
)

val LumenColors.surface: Color get() = card
val LumenColors.borderStrong: Color get() = LumenTokens.Color.borderStrong


@Immutable
data class LumenRadii(
    val sm: Dp = LumenTokens.Radius.sm,
    val md: Dp = LumenTokens.Radius.md,
    val lg: Dp = LumenTokens.Radius.lg,
    val xl: Dp = LumenTokens.Radius.xl,
    val xxl: Dp = LumenTokens.Radius.xxl,
    val pill: Dp = LumenTokens.Radius.pill,
)

@Immutable
data class LumenSpacing(
    val xs: Dp = LumenTokens.Space.xs,
    val sm: Dp = LumenTokens.Space.sm,
    val md: Dp = LumenTokens.Space.md,
    val lg: Dp = LumenTokens.Space.lg,
    val xl: Dp = LumenTokens.Space.xl,
    val xxl: Dp = LumenTokens.Space.xxl,
    val xxxl: Dp = LumenTokens.Space.xl,
    val huge: Dp = LumenTokens.Space.xxl,
)

@Immutable
data class LumenMotionBundle(
    val fast: Int = LumenMotion.focusScaleMs,
    val base: Int = LumenMotion.standardMs,
    val slow: Int = LumenMotion.emphasizedMs,
    val cinematic: Int = LumenTokens.Duration.cinematic,
    val springStiffness: Float = LumenMotion.springStiffness,
    val springDamping: Float = LumenMotion.springDamping,
    val reducedMotion: Boolean = false,
)

@Immutable
data class LumenTokenBundle(
    val colors: LumenColors,
    val radii: LumenRadii = LumenRadii(),
    val spacing: LumenSpacing = LumenSpacing(),
    val motion: LumenMotionBundle = LumenMotionBundle(),
)

fun LumenTokenBundle.scrimGradient(): Brush {
    return Brush.verticalGradient(
        0f to Color.Transparent,
        0.55f to Color.Transparent,
        1f to colors.background,
    )
}

fun LumenTokenBundle.glassSurface(
    modifier: Modifier = Modifier,
    dropBlur: Boolean = false,
): Modifier {
    val baseModifier = if (dropBlur) modifier else modifier
    return baseModifier.background(
        Brush.verticalGradient(
            listOf(
                LumenTokens.Color.surfaceMuted.copy(alpha = 0.6f),
                LumenTokens.Color.surface.copy(alpha = 0.9f),
            ),
        ),
    )
}

val LocalLumenTokens = compositionLocalOf<LumenTokenBundle> { error("LumenTheme not installed") }

val LocalLumenIsTv = compositionLocalOf { false }

object LumenPalette {
    val Standard = LumenColors(
        background = LumenTokens.Color.bg,
        foreground = LumenTokens.Color.textPrimary,
        card = LumenTokens.Color.surface,
        cardForeground = LumenTokens.Color.textPrimary,
        muted = LumenTokens.Color.surfaceMuted,
        mutedForeground = LumenTokens.Color.textSecondary,
        accent = LumenTokens.Color.surfaceMuted,
        border = LumenTokens.Color.border,
        brand = LumenTokens.Color.brand,
        brandGlow = LumenTokens.Color.brandGlow,
        brandForeground = LumenTokens.Color.onBrand,
        destructive = LumenTokens.Color.danger,
        focusHalo = LumenTokens.Color.focusHalo,
        success = LumenTokens.Color.success,
        warning = LumenTokens.Color.warning,
    )

    val Oled = Standard.copy(
        background = LumenTokens.Color.bgOled,
        card = LumenTokens.Color.bg,
        muted = LumenTokens.Color.surface,
    )
}

object LumenShapes {
    val sm get() = LumenTokens.Shape.sm
    val md get() = LumenTokens.Shape.md
    val lg get() = LumenTokens.Shape.lg
    val xl get() = LumenTokens.Shape.xl
    val xxl get() = LumenTokens.Shape.xxl
}

object LumenElevation {
    val none = 0.dp
    val sm = 2.dp
    val md = 6.dp
    val lg = 12.dp
    val xl = 20.dp
}

object LumenExtendedColors {
    val dangerContainer = Color(0x33E5484D)
    val successContainer = Color(0x1F10B981)
    val errorBright = Color(0xFFEF4444)
    val statusHealthy = Color(0xFF10B981)
    val info = Color(0xFF3B82F6)
    val warning = Color(0xFFFBBF24)
    val ratingGold = Color(0xFFFBBF24)
    val cyan = Color(0xFF22D3EE)
    val violet = Color(0xFFA78BFA)
    val debridTint = Color(0x3D10B981)
    val controlScrimDark = Color(0xCC0B0B10)
    val controlScrimLight = Color(0xCCFAFAFA)
    val warningText = Color(0xFFD97706)
    val warningSurface = Color(0xFFFEF3C7)
    val errorSoft = Color(0xFFE57373)
    val debridPanel = Color(0xFF1C1B2A)
    val focusRingWidth = LumenTokens.Focus.ringStroke
}
object LumenProfileColors {
    val pink = Color(0xFFEC4899)
    val blue = Color(0xFF3B82F6)
    val green = Color(0xFF10B981)
    val amber = Color(0xFFF59E0B)
    val purple = Color(0xFF8B5CF6)
    val red = Color(0xFFEF4444)
    val indigo = Color(0xFF818CF8)
    val fuchsia = Color(0xFFD946EF)
    val yellow = Color(0xFFFCD34D)
    val rose = Color(0xFFF43F5E)
    val emerald = Color(0xFF6EE7B7)
    val cyan = Color(0xFF06B6D4)
    val sky = Color(0xFF7DD3FC)
    val violet = Color(0xFF8B5CF6)
    val peach = Color(0xFFFDA4AF)
    val orange = Color(0xFFF97316)
    val lilac = Color(0xFFC084FC)
    val magenta = Color(0xFFEC4899)
}
