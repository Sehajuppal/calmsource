package com.example.calmsource.core.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Inter for UI body copy; Inter Tight for display/headline (matches web lumen.json). */
val InterFamily: FontFamily = FontFamily.SansSerif
val InterTightFamily: FontFamily = FontFamily.SansSerif

@Immutable
data class LumenTypeScale(
    val display: TextStyle,
    val h1: TextStyle,
    val h2: TextStyle,
    val title: TextStyle,
    val rowTitle: TextStyle,
    val body: TextStyle,
    val caption: TextStyle,
    val meta: TextStyle,
    val eyebrow: TextStyle,
)

val LocalLumenTypeScale = compositionLocalOf<LumenTypeScale> {
    error("LumenTheme not installed")
}

fun buildLumenTypeScale(isTv: Boolean): LumenTypeScale {
    val generated = generatedLumenTypeScale(isTv)
    return LumenTypeScale(
        display = generated.display,
        h1 = generated.h1,
        h2 = generated.h2,
        title = generated.title,
        rowTitle = generated.rowTitle,
        body = generated.body,
        caption = generated.caption,
        meta = generated.meta,
        eyebrow = generated.eyebrow,
    )
}

/** Token-backed text styles — screens use these instead of raw sp. */
object LumenType {
    val Display: TextStyle @Composable get() = LocalLumenTypeScale.current.display
    val H1: TextStyle @Composable get() = LocalLumenTypeScale.current.h1
    val H2: TextStyle @Composable get() = LocalLumenTypeScale.current.h2
    val Title: TextStyle @Composable get() = LocalLumenTypeScale.current.title
    val RowTitle: TextStyle @Composable get() = LocalLumenTypeScale.current.rowTitle
    val Body: TextStyle @Composable get() = LocalLumenTypeScale.current.body
    val Caption: TextStyle @Composable get() = LocalLumenTypeScale.current.caption
    val Meta: TextStyle @Composable get() = LocalLumenTypeScale.current.meta
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
        titleMedium = scale.rowTitle,
        bodyLarge = scale.body,
        bodyMedium = scale.caption,
        labelLarge = scale.caption,
        labelSmall = scale.eyebrow,
    )
}
