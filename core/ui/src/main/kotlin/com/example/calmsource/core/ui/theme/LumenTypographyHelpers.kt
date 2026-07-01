package com.example.calmsource.core.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/** Platform-aware typography scale (1.15× on TV). */
@Composable
fun lumenTextScale(): Float = if (LocalLumenIsTv.current) LumenType.TV_SCALE else 1f

@Composable
fun lumenScaledStyle(style: LumenTokens.Style): TextStyle = style.toTextStyle(lumenTextScale())

@Composable
fun lumenSp(baseSp: Float): TextUnit = (baseSp * lumenTextScale()).sp

@Composable
fun lumenTitleStyle(): TextStyle = LumenType.Title.toTextStyle(lumenTextScale())

@Composable
fun lumenBodyStyle(): TextStyle = LumenType.Body.toTextStyle(lumenTextScale())

@Composable
fun lumenCaptionStyle(): TextStyle = LumenType.Caption.toTextStyle(lumenTextScale())

@Composable
fun lumenRowTitleStyle(): TextStyle = LumenType.RowTitle.toTextStyle(lumenTextScale())

@Composable
fun lumenH2Style(): TextStyle = LumenType.H2.toTextStyle(lumenTextScale())
