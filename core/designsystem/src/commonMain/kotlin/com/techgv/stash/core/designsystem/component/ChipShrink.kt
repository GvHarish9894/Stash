package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.semantics.clearAndSetSemantics
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** An item of [rememberWithLeaving]: [leaving] while it shrinks out after it left the list. */
@Immutable
data class Leaving<T>(val value: T, val leaving: Boolean)

private class Departures<T>(var shown: List<T>, var leaving: List<IndexedValue<T>> = emptyList())

/**
 * [items], plus each item that just left it, kept at its old place and flagged [Leaving.leaving] for the shrink-out
 * ([shrinkOut], [StashMotion.ROW_COLLAPSE]). Under Reduce motion items leave at once. [key] identifies an item across
 * lists; an item that comes back stops leaving. For the filter chips: removed source chips shrink instead of fading.
 */
@Composable
fun <T> rememberWithLeaving(items: List<T>, key: (T) -> Any): List<Leaving<T>> {
    val reduceMotion = Stash.reduceMotion
    val departures = remember { Departures(items) }
    var expired by remember { mutableIntStateOf(0) }
    val result = remember(items, expired, reduceMotion) {
        if (items !== departures.shown) {
            val keys = items.mapTo(HashSet(), key)
            val left = departures.shown.withIndex().filter { key(it.value) !in keys }
            val leftKeys = left.mapTo(HashSet()) { key(it.value) }
            departures.leaving = departures.leaving.filter { key(it.value).let { k -> k !in keys && k !in leftKeys } } + left
            departures.shown = items
        }
        if (reduceMotion) departures.leaving = emptyList()
        val all = items.mapTo(ArrayList(items.size + departures.leaving.size)) { Leaving(it, leaving = false) }
        departures.leaving.sortedBy { it.index }.forEach { all.add(it.index.coerceAtMost(all.size), Leaving(it.value, leaving = true)) }
        all
    }
    val pending = departures.leaving
    LaunchedEffect(pending) {
        if (pending.isNotEmpty()) {
            delay(StashMotion.ROW_COLLAPSE.toLong())
            departures.leaving = emptyList()
            expired++
        }
    }
    return result
}

/**
 * While [leaving], shrinks the element out: its width, scale and opacity go to 0 ([StashMotion.ROW_COLLAPSE], ease-in)
 * and it leaves the accessibility tree. Does nothing otherwise.
 */
@Composable
fun Modifier.shrinkOut(leaving: Boolean): Modifier {
    if (!leaving) return this
    val shrink = remember { Animatable(1f) }
    LaunchedEffect(shrink) { shrink.animateTo(0f, StashMotion.exit(StashMotion.ROW_COLLAPSE)) }
    return clearAndSetSemantics {}
        .layout { measurable, constraints ->
            val placeable = measurable.measure(constraints)
            val width = (placeable.width * shrink.value).roundToInt()
            layout(width, placeable.height) { placeable.placeRelative((width - placeable.width) / 2, 0) }
        }
        .graphicsLayer {
            scaleX = shrink.value
            scaleY = shrink.value
            alpha = shrink.value
        }
}
