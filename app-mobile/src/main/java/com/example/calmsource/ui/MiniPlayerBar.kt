package com.example.calmsource.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import com.example.calmsource.core.model.PlayerState
import com.example.calmsource.core.ui.theme.LocalLumenTokens
import com.example.calmsource.core.ui.theme.LumenLayout
import com.example.calmsource.core.ui.theme.LumenTokens
import com.example.calmsource.core.ui.theme.LumenType

@Composable
fun MiniPlayerBar(
    title: String,
    playerState: PlayerState,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val t = LocalLumenTokens.current
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .border(
                width = 1.dp,
                color = t.colors.border,
                shape = LumenTokens.Shape.xl,
            )
            .background(
                color = LumenTokens.Color.surfaceMuted,
                shape = LumenTokens.Shape.xl,
            )
            .semantics {
                contentDescription = title
            }
            .clickable(onClick = onExpand),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = LumenType.Body.toTextStyle(),
                    color = t.colors.foreground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = if (playerState == PlayerState.PLAYING) {
                        stringResource(com.example.calmsource.core.ui.R.string.mini_player_status_playing)
                    } else {
                        stringResource(com.example.calmsource.core.ui.R.string.mini_player_status_paused)
                    },
                    style = LumenType.Caption.toTextStyle(),
                    color = t.colors.mutedForeground,
                )
            }
            IconButton(onClick = onPlayPause, modifier = Modifier.size(LumenLayout.iconXl)) {
                Icon(
                    imageVector = if (playerState == PlayerState.PLAYING) Icons.Default.Pause else Icons.Default.PlayArrow,
                    contentDescription = if (playerState == PlayerState.PLAYING) {
                        stringResource(com.example.calmsource.core.ui.R.string.mini_player_desc_pause)
                    } else {
                        stringResource(com.example.calmsource.core.ui.R.string.mini_player_desc_play)
                    },
                    tint = t.colors.foreground,
                )
            }
            IconButton(onClick = onClose, modifier = Modifier.size(LumenLayout.iconXl)) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = stringResource(com.example.calmsource.core.ui.R.string.mini_player_desc_close),
                    tint = t.colors.mutedForeground,
                )
            }
        }
    }
}
