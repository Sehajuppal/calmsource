package com.example.calmsource.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

/**
 * Inter for UI; Instrument Serif for display headers only.
 * Bundle font files under core/ui/src/main/res/font/ when available.
 */
val InterFamily: FontFamily = FontFamily.SansSerif
val InstrumentSerifFamily: FontFamily = FontFamily.Serif

@Immutable
data class LumenTypeScale(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val title: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val eyebrow: TextStyle,
)

val LocalLumenTypeScale = compositionLocalOf<LumenTypeScale> {
    error("LumenTheme not installed")
}

fun buildLumenTypeScale(isTv: Boolean): LumenTypeScale {
    val m = if (isTv) 1.15f else 1f
    fun bodySp(n: Float) = (n * m).sp
    fun displaySp(n: Float) = n.sp

    val displayFamily = InstrumentSerifFamily
    val uiFamily = InterFamily

    return LumenTypeScale(
        display = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = displaySp(if (isTv) 52f else 44f),
            lineHeight = displaySp(if (isTv) 56f else 48f),
        ),
        h1 = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = displaySp(if (isTv) 40f else 32f),
            lineHeight = displaySp(if (isTv) 44f else 36f),
        ),
        h2 = TextStyle(
            fontFamily = displayFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = displaySp(if (isTv) 32f else 24f),
            lineHeight = displaySp(if (isTv) 36f else 28f),
        ),
        title = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = displaySp(if (isTv) 28f else 20f),
            lineHeight = displaySp(if (isTv) 32f else 24f),
        ),
        body = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = bodySp(15f),
            lineHeight = bodySp(22f),
        ),
        caption = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Normal,
            fontSize = bodySp(13f),
            lineHeight = bodySp(18f),
        ),
        eyebrow = TextStyle(
            fontFamily = uiFamily,
            fontWeight = FontWeight.Medium,
            fontSize = bodySp(11f),
            lineHeight = bodySp(14f),
            letterSpacing = 0.12.em,
        ),
    )
}

/** Token-backed text styles — screens use these instead of raw sp. */
object LumenType {
    val Display: TextStyle @Composable get() = LocalLumenTypeScale.current.display
    val H1: TextStyle @Composable get() = LocalLumenTypeScale.current.h1
    val H2: TextStyle @Composable get() = LocalLumenTypeScale.current.h2
    val Title: TextStyle @Composable get() = LocalLumenTypeScale.current.title
    val Body: TextStyle @Composable get() = LocalLumenTypeScale.current.body
    val Caption: TextStyle @Composable get() = LocalLumenTypeScale.current.caption
    val Eyebrow: TextStyle @Composable get() = LocalLumenTypeScale.current.eyebrow
}

@Composable
fun lumenTypography(isTv: Boolean): Typography {
    val scale = buildLumenTypeScale(isTv)
    return Typography(
        displayLarge = scale.display,
        displayMedium = scale.h1,
        displaySmall = scale.h2,
        headlineLarge = scale.h2,
        headlineMedium = scale.title,
        headlineSmall = scale.title,
        titleLarge = scale.title,
        titleMedium = scale.body,
        bodyLarge = scale.body,
        bodyMedium = scale.caption,
        labelLarge = scale.caption,
        labelSmall = scale.eyebrow,
    )
}
