package com.example.calmsource.core.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.example.calmsource.core.ui.theme.LocalReducedMotion
import com.example.calmsource.core.ui.theme.LumenDelightMotion

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun <T> LumenScreenTransition(
    targetState: T,
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.(AnimatedVisibilityScope, T) -> Unit,
) {
    val reducedMotion = LocalReducedMotion.current
    SharedTransitionLayout(modifier = modifier) {
        AnimatedContent(
            targetState = targetState,
            transitionSpec = {
                ContentTransform(
                    targetContentEnter = LumenDelightMotion.screenEnterTransition(reducedMotion),
                    initialContentExit = LumenDelightMotion.screenExitTransition(reducedMotion),
                )
            },
            label = "lumen_screen_transition",
        ) { state ->
            content(this@SharedTransitionLayout, this, state)
        }
    }
}
