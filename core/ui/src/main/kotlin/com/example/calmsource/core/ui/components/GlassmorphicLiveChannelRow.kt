package com.example.calmsource.core.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.calmsource.core.ui.theme.LumenExtendedColors
import com.example.calmsource.core.ui.theme.LocalLumenIsTv

/**
 * EPG-style live row: channel tile + on-air program block (glass cards).
 */
@Composable
fun GlassmorphicLiveChannelRow(
    channelName: String,
    programTitle: String,
    timeRangeLabel: String,
    onChannelClick: () -> Unit,
    modifier: Modifier = Modifier,
    channelLogoUrl: String? = null,
    isLive: Boolean = true,
    isTv: Boolean = LocalLumenIsTv.current,
    channelModifier: Modifier = Modifier,
    programModifier: Modifier = Modifier,
    onProgramClick: () -> Unit = onChannelClick,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        GlassmorphicCard(
            modifier = channelModifier
                .width(if (isTv) 100.dp else 64.dp)
                .height(72.dp),
            isTv = isTv,
            onClick = onChannelClick,
        ) { isActive ->
            if (!channelLogoUrl.isNullOrBlank()) {
                AsyncImage(
                    model = channelLogoUrl,
                    contentDescription = channelName,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                )
            } else {
                Text(
                    text = channelName.take(4).uppercase(),
                    color = if (isActive) Color.Black else Color.White,
                    fontWeight = FontWeight.Black,
                    fontSize = if (isTv) 18.sp else 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        GlassmorphicCard(
            modifier = programModifier
                .weight(1f)
                .height(72.dp),
            isTv = isTv,
            onClick = onProgramClick,
        ) { isActive ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    if (isLive && isTv) {
                        Text(
                            text = "AIRING NOW",
                            color = if (isActive) Color.Black.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.45f),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            maxLines = 1,
                        )
                    }
                    Text(
                        text = programTitle,
                        color = if (isActive) Color.Black else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (timeRangeLabel.isNotBlank()) {
                        Text(
                            text = timeRangeLabel,
                            color = if (isActive) Color.Black.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (isLive) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isActive) Color.Black else LumenExtendedColors.errorBright),
                    )
                }
            }
        }
    }
}

/** Formats program start/end for EPG row subtitles. */
fun formatEpgTimeRange(startTimeMs: Long?, endTimeMs: Long?): String {
    if (startTimeMs == null || endTimeMs == null) return ""
    val formatter = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
    return "${formatter.format(java.util.Date(startTimeMs))} - ${formatter.format(java.util.Date(endTimeMs))}"
}
