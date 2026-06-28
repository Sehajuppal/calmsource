package com.example.calmsource.core.ui.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.graphics.Color

/** Legacy spacing aliases for screens (maps to tokens/lumen.json s-scale). */
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

