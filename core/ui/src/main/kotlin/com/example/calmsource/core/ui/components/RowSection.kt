package com.example.calmsource.core.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.example.calmsource.core.ui.theme.LocalLumenTokens

@Composable
fun RowSection(
    title: String,
    modifier: Modifier = Modifier,
    onSeeAll: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val t = LocalLumenTokens.current
    Column(modifier.fillMaxWidth()) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = t.spacing.xxxl),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(title, style = MaterialTheme.typography.titleLarge, color = t.colors.foreground)
            if (onSeeAll != null) {
                TextButton(onClick = onSeeAll) { Text("See all", color = t.colors.brand) }
            }
        }
        Spacer(Modifier.height(t.spacing.md))
        content()
        Spacer(Modifier.height(t.spacing.xxl))
    }
}
