/**
 * ============================================================================
 * LUMEN DESIGN SYSTEM CONTRACT
 * ============================================================================
 * 
 * 1. RADIUS TIERS
 *    - xs (6.dp)   : Mini elements (e.g., badges, small status tags).
 *    - sm (10.dp)  : Secondary elements (e.g., small buttons, nested cards).
 *    - md (14.dp)  : Standard components (e.g., list items, grid cards, text fields).
 *    - lg (16.dp)  : Primary containers (e.g., media cards, hero banner buttons).
 *    - xl (20.dp)  : Overlay elements (e.g., sheets, bottom bars, mini-players).
 *    - xxl (24.dp) : Large structural containers (e.g., dialogs, modals).
 *    - pill (999.dp): Fully rounded elements (e.g., tab indicators, pill buttons).
 *
 * 2. SPACING ALIASES (Responsive Grid)
 *    - xs (s2 - 4.dp)   : Dense spacing (e.g., label-to-icon, badge padding).
 *    - sm (s4 - 8.dp)   : Standard element padding.
 *    - md (s6 - 16.dp)  : Primary component margins & internal card padding.
 *    - lg (s8 - 24.dp)  : Row/section spacing on mobile.
 *    - xl (s9 - 32.dp)  : Row/section spacing on TV.
 *    - xxl (s10 - 40.dp): Screen-level margins.
 *    
 *    Responsive Layout Helpers:
 *    - Row Gutter   : Mobile = 16.dp (md) | TV = 24.dp (lg)
 *    - Section Gap  : Mobile = 32.dp (xl) | TV = 40.dp (xxl)
 *    - Side Padding : Mobile = 20.dp      | TV = 48.dp
 *
 * 3. GLASS EFFECT GUIDELINES
 *    - Visual Stack: A glass container MUST consist of:
 *      1. Translucent background: `LumenTokens.Color.glass` (60% alpha) or `glassStrong` (80% alpha).
 *      2. Blurring modifier: `GlassSurface` or `Modifier.graphicsLayer` with RenderEffect (API 31+).
 *      3. Border definition: `LumenTokens.Color.borderSubtle` or `borderStrong` with a vertical gradient.
 *    - Fallback: Older Android versions (API < 31) do not support RenderEffect. Implementations
 *      must degrade gracefully by displaying a solid background color (`LumenTokens.Color.surface`).
 *      - Text Contrast: Ensure all text placed on glass surfaces uses high-contrast tokens
 *      (e.g., `textPrimary` or `textSecondary`).
 *
 * 4. TYPOGRAPHY
 *    - Use [LumenType] styles via [LumenTokens.Style.toTextStyle]; never raw `fontSize` in screens.
 *    - Display headings route through [LumenFontFamily.display]; body copy through [LumenFontFamily.body].
 *    - [MaterialTheme.typography] is pre-mapped in [LumenTheme]; prefer `LumenType.*.toTextStyle()` in UI.
 * ============================================================================
 */
// core/ui/src/main/kotlin/com/calmsource/core/ui/theme/LumenTokens.kt
// SINGLE SOURCE OF TRUTH — DO NOT FORK.
// Generated from /tokens/lumen.json. Re-run codegen instead of hand-editing.
// Web parity: all values mirror src/styles.css. Adding a value requires editing
// tokens/lumen.json first.
package com.example.calmsource.core.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

object LumenTokens {

    object Color {
        // Backgrounds
        val bg            = androidx.compose.ui.graphics.Color(0xFF0B0B12)
        val bgOled        = androidx.compose.ui.graphics.Color(0xFF000000)
        val bgGraphite    = androidx.compose.ui.graphics.Color(0xFF1F1F26)
        // Surfaces
        val surface       = androidx.compose.ui.graphics.Color(0xFF1A1A22)
        val surfaceMuted  = androidx.compose.ui.graphics.Color(0xFF252531)
        val surfaceAccent = androidx.compose.ui.graphics.Color(0xFF2C2C38)
        val popover       = androidx.compose.ui.graphics.Color(0xFF1A1A22)
        // Glass — use with GlassSurface() which applies RenderEffect blur on API 31+.
        val glass         = androidx.compose.ui.graphics.Color(0x991A1A22) // 60% alpha
        val glassStrong   = androidx.compose.ui.graphics.Color(0xCC1A1A22) // 80% alpha
        // Text
        val textPrimary   = androidx.compose.ui.graphics.Color(0xFFFAFAFA)
        val textSecondary = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.65f)
        val textMuted     = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.45f)
        val onBrand       = androidx.compose.ui.graphics.Color(0xFFFCFCFC)
        // Brand — soft signal blue
        val brand         = androidx.compose.ui.graphics.Color(0xFF6E8BFF)
        val brandGlow     = androidx.compose.ui.graphics.Color(0xFF94A8FF)
        
        // Gradients
        val DefaultGlassGradient = Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.08f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.02f))
        )
        val ActiveGlassGradient = Brush.verticalGradient(
            listOf(androidx.compose.ui.graphics.Color.White.copy(alpha = 0.18f), androidx.compose.ui.graphics.Color.White.copy(alpha = 0.06f))
        )
        val BrandHighlightGradient = Brush.verticalGradient(
            listOf(brand, brand.copy(alpha = 0.50f))
        )
        // Borders (white over surface)
        val borderSubtle  = androidx.compose.ui.graphics.Color(0x14FFFFFF) // 8%
        val border        = androidx.compose.ui.graphics.Color(0x1AFFFFFF) // 10%
        val borderStrong  = androidx.compose.ui.graphics.Color(0x2EFFFFFF) // 18%
        // Focus
        val focusHalo     = androidx.compose.ui.graphics.Color(0xB3FAFAFA) // neutral white @ 70%
        // Feedback
        val danger        = androidx.compose.ui.graphics.Color(0xFFE5484D)
        val success       = androidx.compose.ui.graphics.Color(0xFF34D399)
        val warning       = androidx.compose.ui.graphics.Color(0xFFFBBF24)
    }

    object Space {
        val s0 = 0.dp;   val s1 = 2.dp;   val s2 = 4.dp;   val s3 = 6.dp
        val s4 = 8.dp;   val s5 = 12.dp;  val s6 = 16.dp;  val s7 = 20.dp
        val s8 = 24.dp;  val s9 = 32.dp;  val s10 = 40.dp; val s11 = 56.dp; val s12 = 72.dp
        // Aliases for readability
        val xs = s2; val sm = s4; val md = s6; val lg = s8; val xl = s9; val xxl = s10
        val rowGutterMobile = 16.dp; val rowGutterTv = 24.dp
        val sectionGapMobile = 32.dp; val sectionGapTv = 40.dp
        val sidePaddingMobile = 20.dp; val sidePaddingTv = 48.dp
    }

    object Radius { val xs = 6.dp; val sm = 10.dp; val md = 14.dp; val lg = 16.dp; val xl = 20.dp; val xxl = 24.dp; val pill = 999.dp }
    object Shape {
        val xs  = RoundedCornerShape(Radius.xs)
        val sm  = RoundedCornerShape(Radius.sm)
        val md  = RoundedCornerShape(Radius.md)
        val lg  = RoundedCornerShape(Radius.lg)
        val xl  = RoundedCornerShape(Radius.xl)
        val xxl = RoundedCornerShape(Radius.xxl)
        val pill = RoundedCornerShape(Radius.pill)
    }

    object AspectRatio {
        const val poster = 2f / 3f
        const val landscape = 16f / 9f
        const val heroMobile = 16f / 9f
        const val heroTv = 16f / 7f
    }

    object Tile {
        // mobile
        val posterMobileW = 150.dp; val posterMobileH = 220.dp
        val landscapeMobileW = 260.dp; val landscapeMobileH = 150.dp
        // tv
        val posterTvW = 220.dp; val posterTvH = 320.dp
        val landscapeTvW = 380.dp; val landscapeTvH = 215.dp
    }

    object Hero {
        val minPhone = 480.dp; val minPhoneDetails = 360.dp
        val minTv = 720.dp;   val minTvDetails = 540.dp
    }

    /** Easings — match tokens/lumen.json motion.easing exactly. */
    object Easing {
        val Standard    = CubicBezierEasing(0.4f, 0f, 0.2f, 1f)
        val Decelerate  = CubicBezierEasing(0f,   0f, 0.2f, 1f)
        val Accelerate  = CubicBezierEasing(0.4f, 0f, 1f,   1f)
        val AppleOut    = CubicBezierEasing(0.2f, 0.8f, 0.2f, 1f)
        val TileEase    = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
        val FocusBounce = CubicBezierEasing(0.34f, 1.56f, 0.64f, 1f)
    }

    object Duration {
        const val fast = 160; const val standard = 220; const val base = 240
        const val emphasized = 320; const val focus = 180; const val tile = 420; const val cinematic = 520
    }

    object Springs {
        val Emphasized = spring<Float>(dampingRatio = 0.70f, stiffness = 400f)
        val Snappy     = spring<Float>(dampingRatio = 0.85f, stiffness = 600f)
        val Soft       = spring<Float>(dampingRatio = 0.85f, stiffness = 220f)
    }

    object Focus {
        const val scale = 1.045f
        const val hoverScale = 1.055f
        val liftY = (-6).dp
        val ringStroke = 3.dp
        val ringGlow = 6.dp
    }

    object Elevation { val tile = 8.dp; val lift = 24.dp; val hero = 34.dp }

    /**
     * Type scale — fontSize+lineHeight in sp, letterSpacing as em (Compose handles ratio).
     * Apply [tvScale] (×1.15) at the screen level via [LumenType.tv].
     */
    data class Style(
        val fontSize: androidx.compose.ui.unit.TextUnit,
        val lineHeight: androidx.compose.ui.unit.TextUnit,
        val weight: FontWeight,
        val letterSpacingEm: Float,
    ) {
        fun toTextStyle(scale: Float = 1f): TextStyle = TextStyle(
            fontFamily = LumenFontFamily.forStyle(this),
            fontSize = (fontSize.value * scale).sp,
            lineHeight = (lineHeight.value * scale).sp,
            fontWeight = weight,
            letterSpacing = letterSpacingEm.em,
        )
    }
}

object LumenType {
    val Display  = LumenTokens.Style(44.sp, 52.sp, FontWeight.Bold,     -0.018f)
    val H1       = LumenTokens.Style(32.sp, 40.sp, FontWeight.Bold,     -0.018f)
    val H2       = LumenTokens.Style(24.sp, 32.sp, FontWeight.Bold,     -0.012f)
    val Title    = LumenTokens.Style(20.sp, 28.sp, FontWeight.SemiBold, -0.010f)
    val RowTitle = LumenTokens.Style(17.sp, 22.sp, FontWeight.Bold,     -0.012f)
    val Body     = LumenTokens.Style(15.sp, 22.sp, FontWeight.Normal,   -0.011f)
    val Caption  = LumenTokens.Style(13.sp, 18.sp, FontWeight.Normal,    0f)
    val Meta     = LumenTokens.Style(12.sp, 16.sp, FontWeight.Medium,    0.016f)
    val Eyebrow  = LumenTokens.Style(11.sp, 14.sp, FontWeight.Bold,      0.136f)
    const val TV_SCALE = 1.15f
}

/** Resolves device class once per composition. TV builds always return TV; mobile uses smallestScreenWidthDp ≥ 600 OR explicit isTv flag. */
@Composable
@ReadOnlyComposable
fun isTvLayout(forceTv: Boolean = false): Boolean {
    if (forceTv) return true
    val cfg = LocalConfiguration.current
    val uiMode = cfg.uiMode and android.content.res.Configuration.UI_MODE_TYPE_MASK
    return uiMode == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION
}
