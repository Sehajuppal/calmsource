package com.example.calmsource.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenTokens

@Composable
fun HomeFeedSkeleton(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    rowCount: Int = 2,
    tilesPerRow: Int = 5,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        item(key = "skeleton_hero") {
            LumenSkeleton(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(LumenLayout.heroHeightLg),
            )
            Spacer(modifier = Modifier.height(LumenTokens.Space.lg))
        }
        item(key = "skeleton_rows") {
            repeat(rowCount) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LumenTokens.Space.lg, vertical = LumenTokens.Space.s5),
                ) {
                    LumenSkeleton(
                        modifier = Modifier
                            .width(LumenLayout.epgMinBlockWidthTv)
                            .height(LumenTokens.Space.lg),
                    )
                    Spacer(modifier = Modifier.height(LumenTokens.Space.s5))
                    Row(horizontalArrangement = Arrangement.spacedBy(LumenTokens.Space.s5)) {
                        repeat(tilesPerRow) {
                            LumenSkeleton(
                                modifier = Modifier
                                    .width(LumenLayout.epgMinBlockWidth)
                                    .height(LumenLayout.heroStripHeight),
                            )
                        }
                    }
                }
            }
        }
    }
}
