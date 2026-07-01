package com.example.calmsource.core.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.components.GlassSurface
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LocalLumenTokens

import androidx.compose.ui.draw.clip

data class TabItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun GlassTabBar(
    items: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = t.spacing.lg, vertical = t.spacing.md),
        shape = LumenTokens.Shape.pill,
        strong = true,
    ) {
        Row(
            Modifier.fillMaxWidth().height(64.dp).padding(horizontal = t.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val isSel = item.key == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(LumenTokens.Shape.sm)
                        .clickable { onSelect(item.key) }
                        .padding(vertical = t.spacing.sm),
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = if (isSel) t.colors.brand else t.colors.mutedForeground, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (isSel) t.colors.foreground else t.colors.mutedForeground)
                }
            }
        }
    }
}
