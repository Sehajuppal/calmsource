package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.ui.theme.LocalLumenIsTv
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType
import com.example.calmsource.core.ui.theme.size10
import com.example.calmsource.core.ui.theme.size12

enum class SourceBadgeKind {
    IPTV,
    EXTENSION,
    DEBRID,
}

@Composable
fun SourceBadge(
    kind: SourceBadgeKind,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    val isTv = LocalLumenIsTv.current
    val fontSize = if (isTv) LumenType.size12 else LumenType.size10
    val (label, bg, fg) = when (kind) {
        SourceBadgeKind.IPTV -> Triple("IPTV", t.colors.brand.copy(alpha = 0.2f), t.colors.brandGlow)
        SourceBadgeKind.EXTENSION -> Triple("ADDON", t.colors.muted, t.colors.foreground)
        SourceBadgeKind.DEBRID -> Triple("DEBRID", LumenExtendedColors.debridTint, LumenTokens.Color.success)
    }
    Box(
        modifier = modifier
            .clip(LumenTokens.Shape.xs)
            .background(bg)
            .padding(horizontal = LumenTokens.Space.sm, vertical = LumenTokens.Space.xs),
    ) {
        Text(
            text = label,
            color = fg,
            fontSize = fontSize,
            fontWeight = FontWeight.Black,
            lineHeight = fontSize,
        )
    }
}
