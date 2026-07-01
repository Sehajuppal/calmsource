package com.example.calmsource.core.ui.theme

import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/**
 * Lumen typography families — mirrors [tokens/lumen.json] `font.display` and `font.sans`.
 *
 * Uses the platform sans stack until bundled Inter / Inter Tight assets land in `res/font`.
 * All [LumenTokens.Style.toTextStyle] output routes through [forStyle].
 */
object LumenFontFamily {
    /** Inter Tight / display headings */
    val display: FontFamily = FontFamily.SansSerif

    /** Inter / UI body copy */
    val body: FontFamily = FontFamily.SansSerif

    fun forStyle(style: LumenTokens.Style): FontFamily = when (style) {
        LumenType.Display,
        LumenType.H1,
        LumenType.H2,
        LumenType.Title,
        LumenType.RowTitle,
        -> display
        else -> body
    }

    fun displayWeight(style: LumenTokens.Style): FontWeight = style.weight
}
