// SINGLE SOURCE OF TRUTH — DO NOT FORK
package com.example.calmsource.core.ui.theme

import androidx.compose.foundation.background
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Semantic Lumen tokens — core values generated from tokens/lumen.json (see LumenTokensGenerated).
 * App-only extensions (profile swatches, layout dims) stay here.
 */
object LumenTokens {

    object Color {
        val bg get() = LumenTokensGenerated.Color.bgBase
        val surface get() = LumenTokensGenerated.Color.surfaceCard
        val surfaceHi get() = LumenTokensGenerated.Color.surfaceMuted
        val border get() = LumenTokensGenerated.Color.borderDefault
        val textPrimary get() = LumenTokensGenerated.Color.textPrimary
        val textSecondary get() = LumenTokensGenerated.Color.textSecondary
        val textMuted get() = LumenTokensGenerated.Color.textMuted
        val brand get() = LumenTokensGenerated.Color.brandBase
        val brandHi get() = LumenTokensGenerated.Color.brandGlow
        val focusHalo get() = LumenTokensGenerated.Color.focusHalo
        val focusRingWidth get() = LumenTokensGenerated.FocusRing.stroke
        val focusGlowWidth get() = LumenTokensGenerated.FocusRing.outerGlow
        val danger get() = LumenTokensGenerated.Color.danger
        val success get() = LumenTokensGenerated.Color.success
        val warning get() = LumenTokensGenerated.Color.warning
        val glassOverlay get() = LumenTokensGenerated.Color.surfaceGlass
        val glassOverlayFaint get() = LumenTokensGenerated.Color.borderSubtle

        val errorBright = Color(0xFFEF4444)
        val statusHealthy = Color(0xFF10B981)
        val ratingGold = Color(0xFFFBBF24)
        val info = Color(0xFF3B82F6)
        val cyan = Color(0xFF22D3EE)
        val violet = Color(0xFFA78BFA)
        val controlScrimDark = Color(0xCC0B0B10)
        val controlScrimLight = Color(0xCCFAFAFA)
        val debridTint = Color(0x3D10B981)
        val warningSurface = Color(0xFFFEF3C7)
        val warningText = Color(0xFFD97706)
        val debridPanel = Color(0xFF1C1B2A)
        val errorSoft = Color(0xFFE57373)
        val dangerContainer = Color(0x33E5484D)
        val successContainer = Color(0x1F10B981)

        val profilePink = Color(0xFFEC4899)
        val profileBlue = Color(0xFF3B82F6)
        val profileGreen = Color(0xFF10B981)
        val profileAmber = Color(0xFFF59E0B)
        val profilePurple = Color(0xFF8B5CF6)
        val profileRed = Color(0xFFEF4444)
        val profileIndigo = Color(0xFF818CF8)
        val profileFuchsia = Color(0xFFD946EF)
        val profileYellow = Color(0xFFFCD34D)
        val profileRose = Color(0xFFF43F5E)
        val profileEmerald = Color(0xFF6EE7B7)
        val profileCyan = Color(0xFF06B6D4)
        val profileSky = Color(0xFF7DD3FC)
        val profileViolet = Color(0xFF8B5CF6)
        val profilePeach = Color(0xFFFDA4AF)
        val profileOrange = Color(0xFFF97316)
        val profileLilac = Color(0xFFC084FC)
        val profileMagenta = Color(0xFFEC4899)
    }

    object Radius {
        val xs get() = LumenTokensGenerated.Radius.xs
        val sm get() = LumenTokensGenerated.Radius.sm
        val md get() = LumenTokensGenerated.Radius.md
        val lg get() = LumenTokensGenerated.Radius.lg
        val xl get() = LumenTokensGenerated.Radius.xl
        val xxl get() = LumenTokensGenerated.Radius.xxl
        val pill get() = LumenTokensGenerated.Radius.pill
    }

    object Shape {
        val xs get() = LumenTokensGenerated.Shape.xs
        val sm get() = LumenTokensGenerated.Shape.sm
        val md get() = LumenTokensGenerated.Shape.md
        val lg get() = LumenTokensGenerated.Shape.lg
        val xl get() = LumenTokensGenerated.Shape.xl
        val xxl get() = LumenTokensGenerated.Shape.xxl
        val pill get() = LumenTokensGenerated.Shape.pill
        val poster get() = LumenTokensGenerated.Shape.poster
        val hero get() = LumenTokensGenerated.Shape.hero
        val chip get() = LumenTokensGenerated.Shape.chip
        val button get() = LumenTokensGenerated.Shape.button
    }

    object Space {
        val xxs get() = LumenTokensGenerated.Space.xxs
        val xs get() = LumenTokensGenerated.Space.xs
        val sm get() = LumenTokensGenerated.Space.sm
        val sm2 get() = LumenTokensGenerated.Space.sm2
        val md get() = LumenTokensGenerated.Space.md
        val lg get() = LumenTokensGenerated.Space.lg
        val xl get() = LumenTokensGenerated.Space.xl
        val xxl get() = LumenTokensGenerated.Space.xxl
        val xxxl get() = LumenTokensGenerated.Space.xxxl
        val xxxxl get() = LumenTokensGenerated.Space.xxxxl
        val xxxxxl get() = LumenTokensGenerated.Space.xxxxxl
        val xxxxxxl get() = LumenTokensGenerated.Space.xxxxxxl

        fun rowGutter(isTv: Boolean): Dp = LumenTokensGenerated.Space.rowGutter(isTv)
        fun sectionVertical(isTv: Boolean): Dp = LumenTokensGenerated.Space.sectionVertical(isTv)
        fun sidePadding(isTv: Boolean): Dp =
            if (isTv) LumenTokensGenerated.Space.sidePaddingTv else LumenTokensGenerated.Space.sidePaddingMobile
    }

    object Motion {
        const val standardMs: Int = LumenTokensGenerated.Motion.standardMs
        const val emphasizedMs: Int = LumenTokensGenerated.Motion.emphasizedMs
        const val focusScaleMs: Int = LumenTokensGenerated.Motion.focusMs
        const val maxFadeMs: Int = LumenTokensGenerated.Motion.cinematicMs
        const val springStiffness: Float = LumenTokensGenerated.Motion.springStiffness
        const val springDamping: Float = LumenTokensGenerated.Motion.springDamping
        const val focusScale: Float = LumenTokensGenerated.Motion.focusScale
    }

    object AspectRatio {
        const val heroMobile: Float = LumenTokensGenerated.AspectRatio.heroMobile
        const val heroTv: Float = LumenTokensGenerated.AspectRatio.heroTv
        const val posterPortraitMobile: Float = LumenTokensGenerated.AspectRatio.posterPortraitMobile
        const val posterPortraitTv: Float = LumenTokensGenerated.AspectRatio.posterPortraitTv
        const val landscapeMobile: Float = LumenTokensGenerated.AspectRatio.landscapeMobile
        const val landscapeTv: Float = LumenTokensGenerated.AspectRatio.landscapeTv
    }

    object Hero {
        val minHeightPhone get() = LumenTokensGenerated.Hero.minHeightPhone
        val minHeightPhoneDetails get() = LumenTokensGenerated.Hero.minHeightPhoneDetails
        val minHeightTv get() = LumenTokensGenerated.Hero.minHeightTv
        val minHeightTvDetails get() = LumenTokensGenerated.Hero.minHeightTvDetails
    }

    object FocusRing {
        const val alpha: Float = LumenTokensGenerated.FocusRing.alpha
        val stroke get() = LumenTokensGenerated.FocusRing.stroke
        val outerGlow get() = LumenTokensGenerated.FocusRing.outerGlow
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
    val xxl: Dp = LumenTokens.Radius.xxl,
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
        background = LumenTokensGenerated.Color.bgOled,
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
