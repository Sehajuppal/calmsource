// core/ui/src/main/kotlin/com/calmsource/core/ui/tv/TvFocusMemory.kt
//
// TV focus restoration: remembers the last focused item per "scope" (e.g. row id,
// screen route) and restores it when the user navigates back. Survives recomposition
// and config changes via SavedStateHandle / rememberSaveable.
//
// Usage:
//   val memory = rememberTvFocusMemory()
//   TvFocusScope(memory, scopeId = "home/row/$rowId") { restorer ->
//       LazyRow(state = restorer.listState) {
//           items(items, key = { it.id }) { item ->
//               TvFocusable(
//                   modifier = restorer.itemModifier(item.id),
//                   onFocused = { restorer.remember(item.id) },
//               ) { PosterCard(item) }
//           }
//       }
//   }
//
// Restore order:
//   1. Last remembered id within scope (if still present in the list).
//   2. First item in the list.
//   3. No-op (empty list).

package com.example.calmsource.core.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.flow.MutableStateFlow

@Stable
class TvFocusMemory internal constructor(
    initial: Map<String, String> = emptyMap(),
) {
    private val map = HashMap(initial)
    private val pending = MutableStateFlow<String?>(null)

    fun lastFocusedId(scope: String): String? = map[scope]
    fun remember(scope: String, id: String) { map[scope] = id }
    fun forget(scope: String) { map.remove(scope) }
    fun snapshot(): Map<String, String> = map.toMap()
}

private val TvFocusMemorySaver: Saver<TvFocusMemory, List<String>> = Saver(
    save = { mem -> mem.snapshot().flatMap { (k, v) -> listOf(k, v) } },
    restore = { flat ->
        val pairs = flat.chunked(2).filter { it.size == 2 }.associate { it[0] to it[1] }
        TvFocusMemory(pairs)
    }
)

@Composable
fun rememberTvFocusMemory(): TvFocusMemory = rememberSaveable(saver = TvFocusMemorySaver) { TvFocusMemory() }

@Stable
class TvFocusRestorer internal constructor(
    val listState: LazyListState,
    private val memory: TvFocusMemory,
    private val scope: String,
) {
    // FocusRequester per item id — created lazily so we only pay for visible rows.
    private val requesters = HashMap<String, FocusRequester>()
    internal val attempted = HashMap<String, Boolean>()

    fun requester(id: String): FocusRequester =
        requesters.getOrPut(id) { FocusRequester() }

    fun remember(id: String) {
        memory.remember(scope, id)
    }

    fun itemModifier(id: String): Modifier = Modifier
        .focusRequester(requester(id))
        .onFocusChanged { if (it.isFocused) memory.remember(scope, id) }

    fun targetId(allIds: List<String>): String? {
        val last = memory.lastFocusedId(scope)
        return if (last != null && allIds.contains(last)) last else allIds.firstOrNull()
    }

    @Composable
    internal fun RestoreEffect(allIds: List<String>) {
        val target = targetId(allIds) ?: return
        // Wait until the requester is attached to its node before requesting.
        LaunchedEffect(target, allIds.size) {
            repeat(8) { attempt ->
                if (attempt > 0) kotlinx.coroutines.delay(16L * attempt)
                runCatching { requester(target).requestFocus() }
            }
        }
    }
}

@Composable
fun TvFocusScope(
    memory: TvFocusMemory,
    scopeId: String,
    itemIds: List<String>,
    listState: LazyListState = rememberLazyListState(),
    content: @Composable (TvFocusRestorer) -> Unit,
) {
    val restorer = remember(scopeId) { TvFocusRestorer(listState, memory, scopeId) }
    content(restorer)
    restorer.RestoreEffect(itemIds)
}

/** Default focusable wrapper. For full halo + scale, prefer `TvFocusable` from core/ui/components. */
fun Modifier.tvFocusableDefault(onFocused: (() -> Unit)? = null): Modifier =
    this.onFocusChanged { if (it.isFocused) onFocused?.invoke() }.focusable()
