/**
 * Shared UI components and design system for the CalmSource mobile app.
 *
 * Contains reusable composable building blocks and the color palette used
 * across all mobile screens:
 * - [AppColors] — Color palette and gradient definitions for the dark theme
 * - [GlassCard] — Glassmorphism-styled card with gradient background and border
 * - [SourceBadge] — Colored badge showing source type (IPTV / Extension / Debrid)
 * - [ResolutionBadge] — Badge displaying video resolution (4K, 1080P, etc.)
 * - [PremiumButton] — Full-width gradient-background call-to-action button
 *
 * All mobile screens import these components for consistent visual styling.
 */
package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.calmsource.core.model.SourceType


/**
 * Color palette and gradient definitions for the CalmSource mobile dark theme.
 *
 * All color tokens follow a dark-mode-first approach with purple accent colors.
 * [GlassGradient] and [PrimaryGradient] are pre-built [Brush] instances for
 * use in card backgrounds and call-to-action buttons respectively.
 */
object AppColors {
    val Background = Color(0xFF0F0E17)
    val Surface = Color(0x1F2A283E)
    val Border = Color(0x3D8B5CF6)
    val Primary = Color(0xFF8B5CF6)
    val Secondary = Color(0xFFD946EF)
    val TextMain = Color(0xFFFFFEFE)
    val TextSub = Color(0xFFA7A9BE)

    val GlassGradient = Brush.verticalGradient(
        colors = listOf(
            Color(0x332A283E),
            Color(0x1A0F0E17)
        )
    )
    
    val PrimaryGradient = Brush.horizontalGradient(
        colors = listOf(Primary, Secondary)
    )
}

/**
 * Glassmorphism-styled card container with a gradient background and border.
 *
 * Used across all mobile screens as the primary card component. Provides a
 * translucent layered look with rounded corners and an optional click handler.
 *
 * @param modifier Modifier applied to the outer card container.
 * @param onClick Optional click callback; when non-null the card becomes clickable.
 * @param content Column-scoped content block rendered inside the card with 16dp padding.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    var cardModifier = modifier
        .clip(RoundedCornerShape(16.dp))
        .background(AppColors.GlassGradient)
        .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))

    if (onClick != null) {
        cardModifier = cardModifier.clickable { onClick() }
    }

    Column(
        modifier = cardModifier.padding(16.dp),
        content = content
    )
}

/**
 * Colored badge chip indicating the source type of a watch option.
 *
 * Background and text colors are determined by the [SourceType]:
 * - [SourceType.IPTV] → green
 * - [SourceType.EXTENSION] → blue
 * - [SourceType.DEBRID] → amber/yellow
 *
 * @param type The [SourceType] to render.
 */
@Composable
fun SourceBadge(type: SourceType) {
    val bgColor = when (type) {
        SourceType.IPTV -> Color(0x3D10B981)
        SourceType.EXTENSION -> Color(0x3D3B82F6)
        SourceType.DEBRID -> Color(0x3DF59E0B)
    }
    val textColor = when (type) {
        SourceType.IPTV -> Color(0xFF34D399)
        SourceType.EXTENSION -> Color(0xFF60A5FA)
        SourceType.DEBRID -> Color(0xFFFBBF24)
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bgColor)
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = type.name,
            color = textColor,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

/**
 * Badge chip displaying a video resolution label (e.g., "4K", "1080P").
 *
 * Rendered with a subtle white background and border for contrast
 * against dark card surfaces.
 *
 * @param resolution The resolution string to display (uppercased automatically).
 */
@Composable
fun ResolutionBadge(resolution: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0x1AFFFFFF))
            .border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(
            text = resolution.uppercase(),
            color = AppColors.TextMain,
            fontSize = 10.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

/**
 * Full-width gradient call-to-action button used for primary actions.
 *
 * Uses [AppColors.PrimaryGradient] (purple → magenta) as the background
 * with bold white text. Commonly used for "Play Best Match" actions.
 *
 * @param text Button label text.
 * @param onClick Callback invoked when the button is pressed.
 * @param modifier Modifier applied to the outer button container.
 */
@Composable
fun PremiumButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
        contentPadding = PaddingValues(),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
            .height(50.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AppColors.PrimaryGradient, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = text,
                color = AppColors.TextMain,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }
    }
}


