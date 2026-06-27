// AUTO-GENERATED — DO NOT EDIT. Source: tokens/lumen.json
package com.example.calmsource.core.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class GeneratedLumenTypeScale(
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

fun generatedLumenTypeScale(isTv: Boolean): GeneratedLumenTypeScale {
    return if (isTv) GeneratedLumenTypeScale(
        // web: 44px/52px/-0.018em
        display = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 50.599999999999994.sp,
            lineHeight = 59.8.sp,
            letterSpacing = -0.9107999999999998.sp,
        ),
        // web: 32px/40px/-0.018em
        h1 = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 36.8.sp,
            lineHeight = 46.sp,
            letterSpacing = -0.6623999999999999.sp,
        ),
        // web: 24px/32px/-0.012em
        h2 = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 27.599999999999998.sp,
            lineHeight = 36.8.sp,
            letterSpacing = -0.3312.sp,
        ),
        // web: 20px/28px/-0.01em
        title = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(600),
            fontSize = 23.sp,
            lineHeight = 32.199999999999996.sp,
            letterSpacing = -0.23.sp,
        ),
        // web: 17px/22px/-0.012em
        rowTitle = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 19.549999999999997.sp,
            lineHeight = 25.299999999999997.sp,
            letterSpacing = -0.23459999999999998.sp,
        ),
        // web: 15px/22px/-0.011em
        body = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(400),
            fontSize = 17.25.sp,
            lineHeight = 25.299999999999997.sp,
            letterSpacing = -0.18975.sp,
        ),
        // web: 13px/18px/0em
        caption = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(400),
            fontSize = 14.95.sp,
            lineHeight = 20.7.sp,
            letterSpacing = 0.sp,
        ),
        // web: 12px/16px/0.016em
        meta = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(500),
            fontSize = 13.799999999999999.sp,
            lineHeight = 18.4.sp,
            letterSpacing = 0.2208.sp,
        ),
        // web: 11px/14px/0.136em
        eyebrow = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(700),
            fontSize = 12.649999999999999.sp,
            lineHeight = 16.099999999999998.sp,
            letterSpacing = 1.7204.sp,
        ),
    ) else GeneratedLumenTypeScale(
        // web: 44px/52px/-0.018em
        display = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 44.sp,
            lineHeight = 52.sp,
            letterSpacing = -0.7919999999999999.sp,
        ),
        // web: 32px/40px/-0.018em
        h1 = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 32.sp,
            lineHeight = 40.sp,
            letterSpacing = -0.576.sp,
        ),
        // web: 24px/32px/-0.012em
        h2 = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            letterSpacing = -0.28800000000000003.sp,
        ),
        // web: 20px/28px/-0.01em
        title = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(600),
            fontSize = 20.sp,
            lineHeight = 28.sp,
            letterSpacing = -0.2.sp,
        ),
        // web: 17px/22px/-0.012em
        rowTitle = TextStyle(
            fontFamily = InterTightFamily,
            fontWeight = FontWeight(700),
            fontSize = 17.sp,
            lineHeight = 22.sp,
            letterSpacing = -0.20400000000000001.sp,
        ),
        // web: 15px/22px/-0.011em
        body = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(400),
            fontSize = 15.sp,
            lineHeight = 22.sp,
            letterSpacing = -0.16499999999999998.sp,
        ),
        // web: 13px/18px/0em
        caption = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(400),
            fontSize = 13.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
        // web: 12px/16px/0.016em
        meta = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(500),
            fontSize = 12.sp,
            lineHeight = 16.sp,
            letterSpacing = 0.192.sp,
        ),
        // web: 11px/14px/0.136em
        eyebrow = TextStyle(
            fontFamily = InterFamily,
            fontWeight = FontWeight(700),
            fontSize = 11.sp,
            lineHeight = 14.sp,
            letterSpacing = 1.496.sp,
        ),
    )
}
