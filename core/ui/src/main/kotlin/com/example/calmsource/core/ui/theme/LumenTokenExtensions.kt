package com.example.calmsource.core.ui.theme

import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Color

/** @deprecated Use [LumenTokens.Space] or [LumenSpacing] from [LocalLumenTokens]. */
@Deprecated("Use LumenTokens.Space or t.spacing from LocalLumenTokens")
object LumenLegacySpace {
    val xxs get() = LumenTokens.Space.s1
    val xs get() = LumenTokens.Space.s2
    val sm get() = LumenTokens.Space.s3
    val sm2 get() = LumenTokens.Space.s4
    val md get() = LumenTokens.Space.s5
    val lg get() = LumenTokens.Space.s6
    val xl get() = LumenTokens.Space.s7
    val xxl get() = LumenTokens.Space.s8
    val xxxl get() = LumenTokens.Space.s9
    val xxxxl get() = LumenTokens.Space.s10
    val xxxxxl get() = LumenTokens.Space.s11
    val xxxxxxl get() = LumenTokens.Space.s12
}

/** Legacy color aliases */
val LumenTokens.Color.surfaceHi get() = surfaceMuted
val LumenTokens.Color.brandHi get() = brandGlow
val LumenTokens.Color.ratingGold get() = Color(0xFFFBBF24)
val LumenTokens.Color.controlScrimDark get() = Color(0xCC0B0B10)
val LumenTokens.Color.controlScrimLight get() = Color(0xCCFAFAFA)
val LumenTokens.Color.cyan get() = Color(0xFF22D3EE)
val LumenTokens.Color.violet get() = Color(0xFFA78BFA)
val LumenTokens.Color.debridTint get() = Color(0x3D10B981)
val LumenTokens.Color.debridPanel get() = Color(0xFF1C1B2A)
val LumenTokens.Color.errorSoft get() = Color(0xFFE57373)
val LumenTokens.Color.warningSurface get() = Color(0xFFFEF3C7)
val LumenTokens.Color.warningText get() = Color(0xFFD97706)

// Centralized font sizes and line heights to avoid raw .sp literals in screen/section files

val LumenType.size10 get() = 10.sp
val LumenType.size10_5 get() = 10.5.sp
val LumenType.size11 get() = 11.sp
val LumenType.size11_5 get() = 11.5.sp
val LumenType.size12 get() = 12.sp
val LumenType.size12_5 get() = 12.5.sp
val LumenType.size13 get() = 13.sp
val LumenType.size14 get() = 14.sp
val LumenType.size15 get() = 15.sp
val LumenType.size16 get() = 16.sp
val LumenType.size17 get() = 17.sp
val LumenType.size18 get() = 18.sp
val LumenType.size20 get() = 20.sp
val LumenType.size24 get() = 24.sp
val LumenType.size28 get() = 28.sp
val LumenType.size32 get() = 32.sp
val LumenType.size36 get() = 36.sp
val LumenType.size38 get() = 38.sp
val LumenType.size44 get() = 44.sp
val LumenType.size110 get() = 110.sp

val LumenType.size1 get() = 1.sp
val LumenType.size1_4 get() = 1.4.sp
val LumenType.size1_6 get() = 1.6.sp
val LumenType.size2 get() = 2.sp
val LumenType.size4 get() = 4.sp
val LumenType.size22 get() = 22.sp
val LumenType.size26 get() = 26.sp
val LumenType.size40 get() = 40.sp
val LumenType.size48 get() = 48.sp


val LumenType.line1 get() = 1.sp
val LumenType.line1_6 get() = 1.6.sp
val LumenType.line2 get() = 2.sp

/** Decorative rank numeral used in Top 10 rows. */
fun LumenType.rankNumeralStyle(): TextStyle =
    Display.toTextStyle(size48.value / 44f)

/** Large pairing / device codes in setup flows. */
fun LumenType.pinCodeStyle(): TextStyle = H1.toTextStyle()

/** Compact tab-bar labels under icons. */
fun LumenType.tabLabelStyle(): TextStyle = Meta.toTextStyle()


val LumenLayout.width480 get() = 480.dp
val LumenLayout.width400 get() = 400.dp
val LumenLayout.epgPxPerMinute get() = 8.dp




