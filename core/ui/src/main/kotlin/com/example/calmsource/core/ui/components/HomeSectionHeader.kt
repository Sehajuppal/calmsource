package com.example.calmsource.core.ui.components

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import com.example.calmsource.core.ui.theme.*

@Composable
fun HomeSectionHeader(
    title: String,
    isTv: Boolean,
    isFirstSection: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    val sidePadding = if (isTv) LumenTokens.Space.sidePaddingTv else LumenTokens.Space.sidePaddingMobile
    val topPadding = if (isFirstSection) {
        if (isTv) LumenTokens.Space.sectionGapTv else LumenTokens.Space.sectionGapMobile
    } else {
        if (isTv) LumenTokens.Space.rowGutterTv else LumenTokens.Space.rowGutterMobile
    }
    val bottomPadding = if (isTv) LumenTokens.Space.rowGutterTv else LumenTokens.Space.md

    Text(
        text = title,
        style = LumenType.RowTitle.toTextStyle(if (isTv) LumenType.TV_SCALE else 1f),
        color = t.colors.foreground,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier.padding(
            start = sidePadding,
            end = sidePadding,
            top = topPadding,
            bottom = bottomPadding,
        ),
    )
}
