// core/ui/src/main/kotlin/com/example/calmsource/core/ui/theme/LumenType.kt
package com.example.calmsource.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Place font files in core/ui/src/main/res/font/:
 *   inter_regular.ttf, inter_medium.ttf, inter_semibold.ttf, inter_bold.ttf
 *   inter_tight_semibold.ttf, inter_tight_bold.ttf
 * Then uncomment the FontFamily builders below.
 */
val InterFamily: FontFamily = FontFamily.SansSerif
val InterTightFamily: FontFamily = FontFamily.SansSerif
// val InterFamily = FontFamily(
//   Font(R.font.inter_regular,  FontWeight.Normal),
//   Font(R.font.inter_medium,   FontWeight.Medium),
//   Font(R.font.inter_semibold, FontWeight.SemiBold),
//   Font(R.font.inter_bold,     FontWeight.Bold),
// )

@Composable
fun lumenTypography(isTv: Boolean): Typography {
    val scale = if (isTv) 1.15f else 1.0f
    fun sp(n: Float) = (n * scale).sp

    val display = InterTightFamily
    val body    = InterFamily

    return Typography(
        displayLarge   = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold,     fontSize = sp(56f), lineHeight = sp(60f)),
        displayMedium  = TextStyle(fontFamily = display, fontWeight = FontWeight.Bold,     fontSize = sp(44f), lineHeight = sp(48f)),
        displaySmall   = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = sp(32f), lineHeight = sp(36f)),
        headlineLarge  = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = sp(28f), lineHeight = sp(32f)),
        headlineMedium = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = sp(22f), lineHeight = sp(26f)),
        titleLarge     = TextStyle(fontFamily = display, fontWeight = FontWeight.SemiBold, fontSize = sp(18f), lineHeight = sp(22f)),
        titleMedium    = TextStyle(fontFamily = body,    fontWeight = FontWeight.SemiBold, fontSize = sp(16f), lineHeight = sp(20f)),
        bodyLarge      = TextStyle(fontFamily = body,    fontWeight = FontWeight.Normal,   fontSize = sp(16f), lineHeight = sp(22f)),
        bodyMedium     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Normal,   fontSize = sp(14f), lineHeight = sp(20f)),
        labelLarge     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,   fontSize = sp(14f), lineHeight = sp(18f)),
        labelSmall     = TextStyle(fontFamily = body,    fontWeight = FontWeight.Medium,   fontSize = sp(12f), lineHeight = sp(16f)),
    )
}
