package com.example.calmsource.core.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenExtendedColors

enum class ProviderHealthVisual {
    HEALTHY,
    SLOW,
    FAILED,
    DISABLED,
}

@Composable
fun providerHealthColor(health: ProviderHealthVisual): Color {
    val t = LocalLumenTokens.current
    return when (health) {
        ProviderHealthVisual.HEALTHY -> t.colors.success
        ProviderHealthVisual.SLOW -> LumenExtendedColors.warning
        ProviderHealthVisual.FAILED -> LumenExtendedColors.errorBright
        ProviderHealthVisual.DISABLED -> t.colors.mutedForeground
    }
}
