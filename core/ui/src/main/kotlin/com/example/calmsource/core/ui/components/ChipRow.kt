package com.example.calmsource.core.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenTokens

@Composable
fun ChipRow(
    items: List<String>,
    selected: String?,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    LazyRow(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = t.spacing.xxxl),
        horizontalArrangement = Arrangement.spacedBy(t.spacing.sm),
    ) {
        items(items) { label ->
            val isSel = label == selected
            FilterChip(
                selected = isSel,
                onClick  = { onSelect(label) },
                label    = { Text(label) },
                shape    = androidx.compose.foundation.shape.RoundedCornerShape(999.dp),
                border   = BorderStroke(1.dp, if (isSel) LumenTokens.Color.borderStrong else t.colors.border),
                colors   = FilterChipDefaults.filterChipColors(
                    containerColor         = t.colors.muted.copy(alpha = 0.45f),
                    selectedContainerColor = t.colors.foreground.copy(alpha = 0.12f),
                    labelColor             = t.colors.mutedForeground,
                    selectedLabelColor     = t.colors.foreground,
                ),
            )
        }
    }
}
