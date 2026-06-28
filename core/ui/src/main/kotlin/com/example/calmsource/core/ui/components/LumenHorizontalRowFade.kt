package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun LumenHorizontalRowFade(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    val t = LocalLumenTokens.current
    val fadeWidth = 28.dp
    Box(modifier = modifier) {
        content()
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .width(fadeWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(t.colors.background, Color.Transparent),
                    ),
                ),
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(fadeWidth)
                .fillMaxHeight()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color.Transparent, t.colors.background),
                    ),
                ),
        )
    }
}
