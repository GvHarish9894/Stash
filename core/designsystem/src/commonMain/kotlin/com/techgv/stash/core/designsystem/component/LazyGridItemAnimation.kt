package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.core.tween
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion

/** `stashAnimateItem` for lazy grid items (the medium-width two-column shelf): the same specs, none under Reduce motion. */
@Composable
fun Modifier.stashAnimateItem(scope: LazyGridItemScope): Modifier =
    if (Stash.reduceMotion) {
        this
    } else {
        with(scope) {
            animateItem(
                fadeInSpec = tween(StashMotion.LIST_ITEM, easing = StashMotion.EaseOutEmphasized),
                // Rows below a removed one close the gap with the swipe-out's row collapse (handoff: 260 ms, ease).
                placementSpec = tween(StashMotion.ROW_COLLAPSE, easing = StashMotion.Ease),
                fadeOutSpec = StashMotion.exit(StashMotion.ROW_COLLAPSE),
            )
        }
    }
