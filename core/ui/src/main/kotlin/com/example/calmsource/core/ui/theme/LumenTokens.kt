// SINGLE SOURCE OF TRUTH — DO NOT FORK
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
 * Canonical Lumen design tokens — values match the web Lumen app exactly.
 * Screens and components must read from here; never fork literals.
 */
object LumenTokens {

    object Color {
        val bg = androidx.compose.ui.graphics.Color(0xFF06070B)
        val surface = androidx.compose.ui.graphics.Color(0xFF0E1117)
        val surfaceHi = androidx.compose.ui.graphics.Color(0xFF151A22)
        val border = androidx.compose.ui.graphics.Color(0xFF1F2630)
        val textPrimary = androidx.compose.ui.graphics.Color(0xFFF5F7FA)
        val textSecondary = androidx.compose.ui.graphics.Color(0xFFA6ADBB)
        val textMuted = androidx.compose.ui.graphics.Color(0xFF6B7280)
        val brand = androidx.compose.ui.graphics.Color(0xFF3D6BFF)
        val brandHi = androidx.compose.ui.graphics.Color(0xFF5C86FF)
        val focusHalo = androidx.compose.ui.graphics.Color(0x993D6BFF)
        val focusRingWidth = 3.dp
        val focusGlowWidth = 6.dp
        val danger = androidx.compose.ui.graphics.Color(0xFFFF4D5E)
        val success = androidx.compose.ui.graphics.Color(0xFF34D399)
        val warning = androidx.compose.ui.graphics.Color(0xFFF59E0B)
        val errorBright = androidx.compose.ui.graphics.Color(0xFFEF4444)
        val statusHealthy = androidx.compose.ui.graphics.Color(0xFF10B981)
        val ratingGold = androidx.compose.ui.graphics.Color(0xFFFBBF24)
        val info = androidx.compose.ui.graphics.Color(0xFF3B82F6)
        val cyan = androidx.compose.ui.graphics.Color(0xFF22D3EE)
        val violet = androidx.compose.ui.graphics.Color(0xFFA78BFA)
        val glassOverlay = androidx.compose.ui.graphics.Color(0x1AFFFFFF)
        val glassOverlayFaint = androidx.compose.ui.graphics.Color(0x0DFFFFFF)
        val controlScrimDark = androidx.compose.ui.graphics.Color(0xCC0B0B10)
        val controlScrimLight = androidx.compose.ui.graphics.Color(0xCCFAFAFA)
        val debridTint = androidx.compose.ui.graphics.Color(0x3D10B981)
        val warningSurface = androidx.compose.ui.graphics.Color(0xFFFEF3C7)
        val warningText = androidx.compose.ui.graphics.Color(0xFFD97706)
        val debridPanel = androidx.compose.ui.graphics.Color(0xFF1C1B2A)
        val errorSoft = androidx.compose.ui.graphics.Color(0xFFE57373)
        val dangerContainer = androidx.compose.ui.graphics.Color(0x33FF4D5E)
        val successContainer = androidx.compose.ui.graphics.Color(0x1F10B981)

        val profilePink = androidx.compose.ui.graphics.Color(0xFFEC4899)
        val profileBlue = androidx.compose.ui.graphics.Color(0xFF3B82F6)
        val profileGreen = androidx.compose.ui.graphics.Color(0xFF10B981)
        val profileAmber = androidx.compose.ui.graphics.Color(0xFFF59E0B)
        val profilePurple = androidx.compose.ui.graphics.Color(0xFF8B5CF6)
        val profileRed = androidx.compose.ui.graphics.Color(0xFFEF4444)
        val profileIndigo = androidx.compose.ui.graphics.Color(0xFF818CF8)
        val profileFuchsia = androidx.compose.ui.graphics.Color(0xFFD946EF)
        val profileYellow = androidx.compose.ui.graphics.Color(0xFFFCD34D)
        val profileRose = androidx.compose.ui.graphics.Color(0xFFF43F5E)
        val profileEmerald = androidx.compose.ui.graphics.Color(0xFF6EE7B7)
        val profileCyan = androidx.compose.ui.graphics.Color(0xFF06B6D4)
        val profileSky = androidx.compose.ui.graphics.Color(0xFF7DD3FC)
        val profileViolet = androidx.compose.ui.graphics.Color(0xFF8B5CF6)
        val profilePeach = androidx.compose.ui.graphics.Color(0xFFFDA4AF)
        val profileOrange = androidx.compose.ui.graphics.Color(0xFFF97316)
        val profileLilac = androidx.compose.ui.graphics.Color(0xFFC084FC)
        val profileMagenta = androidx.compose.ui.graphics.Color(0xFFEC4899)
    }

    object Radius {
        val xs: Dp = 6.dp
        val sm: Dp = 10.dp
        val md: Dp = 14.dp
        val lg: Dp = 20.dp
        val xl: Dp = 28.dp
        val pill: Dp = 999.dp
    }

    object Shape {
        val xs = RoundedCornerShape(Radius.xs)
        val sm = RoundedCornerShape(Radius.sm)
        val md = RoundedCornerShape(Radius.md)
        val lg = RoundedCornerShape(Radius.lg)
        val xl = RoundedCornerShape(Radius.xl)
        val pill = RoundedCornerShape(Radius.pill)
        val poster = lg
        val hero = xl
        val chip = pill
        val button = md
    }

  /** Spacing scale: 2, 4, 6, 8, 12, 16, 20, 24, 32, 40, 56, 72 */
    object Space {
        val xxs: Dp = 2.dp
        val xs: Dp = 4.dp
        val sm: Dp = 6.dp
        val sm2: Dp = 8.dp
        val md: Dp = 12.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 24.dp
        val xxxl: Dp = 32.dp
        val xxxxl: Dp = 40.dp
        val xxxxxl: Dp = 56.dp
        val xxxxxxl: Dp = 72.dp

        fun rowGutter(isTv: Boolean): Dp = if (isTv) xxl else lg
        fun sectionVertical(isTv: Boolean): Dp = if (isTv) xxxxl else xxxl
    }

    object Motion {
        const val standardMs: Int = 220
        const val emphasizedMs: Int = 320
        const val focusScaleMs: Int = 180
        const val maxFadeMs: Int = 400
        const val springStiffness: Float = 380f
        const val springDamping: Float = 32f
        const val focusScale: Float = 1.06f
    }

    object AspectRatio {
        const val heroMobile: Float = 16f / 9f
        const val heroTv: Float = 16f / 7f
        const val poster: Float = 2f / 3f
        const val landscapeCard: Float = 16f / 9f
    }

    object Elevation {
        val none: Dp = 0.dp
        val sm: Dp = 2.dp
        val md: Dp = 6.dp
        val lg: Dp = 12.dp
        val xl: Dp = 20.dp
    }

    /** Layout-specific dimensions not in the spacing scale — screens must use these, not raw dp. */
    object Layout {
        val hairline: Dp = 0.5.dp
        val heroHeightMobile: Dp = 450.dp
        val heroStripHeight: Dp = 180.dp
        val bottomNavPadding: Dp = 80.dp
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
}

// --- Legacy bridge (core/ui components + gradual migration) ---

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
)

@Immutable
data class LumenRadii(
    val sm: Dp = LumenTokens.Radius.sm,
    val md: Dp = LumenTokens.Radius.md,
    val lg: Dp = LumenTokens.Radius.lg,
    val xl: Dp = LumenTokens.Radius.xl,
    val xxl: Dp = LumenTokens.Radius.xl,
    val pill: Dp = LumenTokens.Radius.pill,
)

@Immutable
data class LumenSpacing(
    val xs: Dp = LumenTokens.Space.xs,
    val sm: Dp = LumenTokens.Space.sm2,
    val md: Dp = LumenTokens.Space.md,
    val lg: Dp = LumenTokens.Space.lg,
    val xl: Dp = LumenTokens.Space.xl,
    val xxl: Dp = LumenTokens.Space.xxl,
    val xxxl: Dp = LumenTokens.Space.xxxl,
    val huge: Dp = LumenTokens.Space.xxxxl,
)

@Immutable
data class LumenMotion(
    val fast: Int = LumenTokens.Motion.focusScaleMs,
    val base: Int = LumenTokens.Motion.standardMs,
    val slow: Int = LumenTokens.Motion.emphasizedMs,
    val cinematic: Int = 520,
    val springStiffness: Float = LumenTokens.Motion.springStiffness,
    val springDamping: Float = LumenTokens.Motion.springDamping,
    val reducedMotion: Boolean = false,
)

@Immutable
data class LumenTokenBundle(
    val colors: LumenColors,
    val radii: LumenRadii = LumenRadii(),
    val spacing: LumenSpacing = LumenSpacing(),
    val motion: LumenMotion = LumenMotion(),
)

typealias LumenTokensLegacy = LumenTokenBundle

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
                LumenTokens.Color.surfaceHi.copy(alpha = 0.6f),
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
        muted = LumenTokens.Color.surfaceHi,
        mutedForeground = LumenTokens.Color.textSecondary,
        accent = LumenTokens.Color.surfaceHi,
        border = LumenTokens.Color.border,
        brand = LumenTokens.Color.brand,
        brandGlow = LumenTokens.Color.brandHi,
        brandForeground = LumenTokens.Color.textPrimary,
        destructive = LumenTokens.Color.danger,
        focusHalo = LumenTokens.Color.focusHalo,
    )

    val Oled = Standard.copy(
        background = Color(0xFF000000),
        card = LumenTokens.Color.bg,
        muted = LumenTokens.Color.surface,
    )
}

object LumenShapes {
    val sm get() = LumenTokens.Shape.sm
    val md get() = LumenTokens.Shape.md
    val lg get() = LumenTokens.Shape.lg
    val xl get() = LumenTokens.Shape.xl
    val xxl get() = LumenTokens.Shape.xl
}
