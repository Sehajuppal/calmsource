package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens

data class TabItem(val key: String, val label: String, val icon: ImageVector)

@Composable
fun GlassTabBar(
    items: List<TabItem>,
    selected: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = t.spacing.lg, vertical = t.spacing.md)
            .shadow(20.dp, RoundedCornerShape(t.radii.pill), clip = false)
            .clip(RoundedCornerShape(t.radii.pill))
            .background(Brush.verticalGradient(0f to Color(0x99151520), 1f to Color(0xCC0F0F18)))
            .height(64.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = t.spacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            items.forEach { item ->
                val isSel = item.key == selected
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.clickable { onSelect(item.key) }.padding(t.spacing.sm),
                ) {
                    Icon(item.icon, contentDescription = item.label, tint = if (isSel) t.colors.brand else t.colors.mutedForeground, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.height(2.dp))
                    Text(item.label, style = MaterialTheme.typography.labelSmall, color = if (isSel) t.colors.foreground else t.colors.mutedForeground)
                }
            }
        }
    }
}
