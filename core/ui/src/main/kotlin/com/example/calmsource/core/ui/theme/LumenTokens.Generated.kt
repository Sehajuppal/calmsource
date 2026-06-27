// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
// Regenerate: npm run tokens:build
package com.example.calmsource.core.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Generated Lumen token values from tokens/lumen.json (web source of truth).
 * Semantic aliases and screen extensions live in LumenTokens.kt.
 */
internal object LumenTokensGenerated {

    object Color {
        val bgBase = Color(0xFF0B0B12)
        val bgOled = Color(0xFF000000)
        val bgGraphite = Color(0xFF1F1F26)
        val surfaceCard = Color(0xFF1A1A22)
        val surfaceMuted = Color(0xFF252531)
        val surfaceAccent = Color(0xFF2C2C38)
        val surfaceGlass = Color(0x991A1A22)
        val surfaceGlassStrong = Color(0xCC1A1A22)
        val textPrimary = Color(0xFFFAFAFA)
        val textSecondary = Color(0xFFA3A3AD)
        val textMuted = Color(0xFF6B7280)
        val textOnBrand = Color(0xFFFCFCFC)
        val brandBase = Color(0xFF3D6BFF)
        val brandGlow = Color(0xFF5C86FF)
        val brandForeground = Color(0xFFFCFCFC)
        val borderSubtle = Color(0x14FFFFFF)
        val borderDefault = Color(0x1AFFFFFF)
        val borderStrong = Color(0x2EFFFFFF)
        val focusHalo = Color(0x993D6BFF)
        val danger = Color(0xFFE5484D)
        val success = Color(0xFF34D399)
        val warning = Color(0xFFFBBF24)
    }

    object Radius {
        val xs: Dp = 6.dp
        val sm: Dp = 10.dp
        val md: Dp = 14.dp
        val lg: Dp = 16.dp
        val xl: Dp = 20.dp
        val xxl: Dp = 24.dp
        val pill: Dp = 999.dp
    }

    object Shape {
        val xs = RoundedCornerShape(Radius.xs)
        val sm = RoundedCornerShape(Radius.sm)
        val md = RoundedCornerShape(Radius.md)
        val lg = RoundedCornerShape(Radius.lg)
        val xl = RoundedCornerShape(Radius.xl)
        val xxl = RoundedCornerShape(Radius.xxl)
        val pill = RoundedCornerShape(Radius.pill)
        val poster = lg
        val hero = xxl
        val chip = pill
        val button = md
    }

    object Space {
        val s0: Dp = 0.dp
        val s1: Dp = 2.dp
        val s2: Dp = 4.dp
        val s3: Dp = 6.dp
        val s4: Dp = 8.dp
        val s5: Dp = 12.dp
        val s6: Dp = 16.dp
        val s7: Dp = 20.dp
        val s8: Dp = 24.dp
        val s9: Dp = 32.dp
        val s10: Dp = 40.dp
        val s11: Dp = 56.dp
        val s12: Dp = 72.dp
        val xxs: Dp = s1
        val xs: Dp = s2
        val sm: Dp = s3
        val sm2: Dp = s4
        val md: Dp = s5
        val lg: Dp = s6
        val xl: Dp = s7
        val xxl: Dp = s8
        val xxxl: Dp = s9
        val xxxxl: Dp = s10
        val xxxxxl: Dp = s11
        val xxxxxxl: Dp = s12
        val rowGutterMobile: Dp = 16.dp
        val rowGutterTv: Dp = 24.dp
        val sectionGapMobile: Dp = 32.dp
        val sectionGapTv: Dp = 40.dp
        val sidePaddingMobile: Dp = 20.dp
        val sidePaddingTv: Dp = 48.dp
        fun rowGutter(isTv: Boolean): Dp = if (isTv) rowGutterTv else rowGutterMobile
        fun sectionVertical(isTv: Boolean): Dp = if (isTv) sectionGapTv else sectionGapMobile
    }

    object Motion {
        const val fastMs: Int = 160
        const val standardMs: Int = 220
        const val baseMs: Int = 240
        const val emphasizedMs: Int = 320
        const val focusMs: Int = 180
        const val tileMs: Int = 420
        const val cinematicMs: Int = 520
        const val springStiffness: Float = 400f
        const val springDamping: Float = 0.7f
        const val focusScale: Float = 1.06f
        const val hoverScale: Float = 1.055f
    }

    object FocusRing {
        val stroke: Dp = 3.dp
        val outerGlow: Dp = 6.dp
        const val alpha: Float = 0.6f
    }

    object AspectRatio {
        const val heroMobile: Float = 16f / 9f
        const val heroTv: Float = 16f / 7f
        const val posterPortraitMobile: Float = 150f / 220f
        const val posterPortraitTv: Float = 220f / 320f
        const val landscapeMobile: Float = 260f / 150f
        const val landscapeTv: Float = 380f / 215f
    }

    object Hero {
        val minHeightPhone: Dp = 480.dp
        val minHeightPhoneDetails: Dp = 360.dp
        val minHeightTv: Dp = 720.dp
        val minHeightTvDetails: Dp = 540.dp
    }
}
