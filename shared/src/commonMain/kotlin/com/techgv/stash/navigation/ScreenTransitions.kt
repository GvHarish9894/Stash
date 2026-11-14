package com.techgv.stash.navigation

import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hasRoute
import com.techgv.stash.core.designsystem.theme.StashMotion

/** How a destination moves (handoff motion table): search drops in, the reader rises, side screens slide from the end. */
internal enum class Layer { Home, Search, Panel, Reader }

internal fun NavBackStackEntry.layer(): Layer = when {
    destination.hasRoute<Shelf>() -> Layer.Home
    destination.hasRoute<Search>() -> Layer.Search
    destination.hasRoute<Reader>() -> Layer.Reader
    else -> Layer.Panel
}

internal fun openMillis(layer: Layer) = when (layer) {
    Layer.Search -> StashMotion.SEARCH_OPEN
    Layer.Reader -> StashMotion.READER_OPEN
    Layer.Home, Layer.Panel -> StashMotion.PANEL_IN
}

internal fun closeMillis(layer: Layer) = when (layer) {
    Layer.Search -> StashMotion.SEARCH_CLOSE
    Layer.Reader -> StashMotion.READER_CLOSE
    Layer.Home, Layer.Panel -> StashMotion.PANEL_OUT
}

/**
 * How the screen behind looks while [cover] is on top of it: scale, alpha, horizontal shift as a width fraction, how far
 * it moves up ([lift], dp) and its corner radius ([corner], dp).
 */
internal data class Recede(val scale: Float, val alpha: Float, val shift: Float, val lift: Float = 0f, val corner: Float = 0f)

internal fun recede(cover: Layer?): Recede = when (cover) {
    Layer.Search -> Recede(0.97f, 0.97f, 0f)
    Layer.Reader -> Recede(0.93f, 0.8f, 0f, lift = 6f, corner = 22f)
    Layer.Panel -> Recede(1f, 0.9f, -0.22f)
    Layer.Home, null -> Recede(1f, 1f, 0f)
}

/** The search screen fades in from 6 dp above its place (handoff). */
private const val SEARCH_DROP_DP = 6

private const val PANEL_SHIFT_PERCENT = 22

/** The new top screen arriving. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenEnter(reduceMotion: Boolean, density: Density): EnterTransition {
    if (reduceMotion) return EnterTransition.None
    val layer = targetState.layer()
    val millis = openMillis(layer)
    val drop = with(density) { SEARCH_DROP_DP.dp.roundToPx() }
    return when (layer) {
        Layer.Search -> fadeIn(StashMotion.enter(millis)) + slideInVertically(StashMotion.enter(millis)) { -drop }
        Layer.Reader -> fadeIn(StashMotion.enter(millis)) + slideInVertically(StashMotion.enter(millis)) { it * 9 / 100 }
        Layer.Panel -> slideIntoContainer(SlideDirection.Start, StashMotion.enter(millis))
        Layer.Home -> EnterTransition.None
    }
}

/** The screen that stays underneath while a new one arrives over it. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenRecede(reduceMotion: Boolean, density: Density): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    val cover = targetState.layer()
    val millis = openMillis(cover)
    val look = recede(cover)
    val lift = with(density) { look.lift.dp.roundToPx() }
    return when (cover) {
        Layer.Panel -> slideOutOfContainer(SlideDirection.Start, StashMotion.enter(millis)) { it * PANEL_SHIFT_PERCENT / 100 } +
            fadeOut(StashMotion.enter(millis), look.alpha)
        // shortcut: a screen in the navigation host does not round its corners behind the reader (Home does, see
        // StashNavHost's recede); an exit transition has no clip shape. Upgrade if screens other than Home open the reader often.
        else -> scaleOut(StashMotion.enter(millis), look.scale) + fadeOut(StashMotion.enter(millis), look.alpha) +
            slideOutVertically(StashMotion.enter(millis)) { -lift }
    }
}

/** The screen underneath coming back when the top one closes. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenReturn(reduceMotion: Boolean, density: Density): EnterTransition {
    if (reduceMotion) return EnterTransition.None
    val cover = initialState.layer()
    val millis = closeMillis(cover)
    val look = recede(cover)
    val lift = with(density) { look.lift.dp.roundToPx() }
    return when (cover) {
        Layer.Panel -> slideIntoContainer(SlideDirection.End, StashMotion.exit(millis)) { it * PANEL_SHIFT_PERCENT / 100 } +
            fadeIn(StashMotion.exit(millis), look.alpha)
        else -> scaleIn(StashMotion.exit(millis), look.scale) + fadeIn(StashMotion.exit(millis), look.alpha) +
            slideInVertically(StashMotion.exit(millis)) { -lift }
    }
}

/** The top screen closing; exits run about 30 % faster than entrances. */
internal fun AnimatedContentTransitionScope<NavBackStackEntry>.screenClose(reduceMotion: Boolean, density: Density): ExitTransition {
    if (reduceMotion) return ExitTransition.None
    val layer = initialState.layer()
    val millis = closeMillis(layer)
    val drop = with(density) { SEARCH_DROP_DP.dp.roundToPx() }
    return when (layer) {
        Layer.Search -> fadeOut(StashMotion.exit(millis)) + slideOutVertically(StashMotion.exit(millis)) { -drop }
        Layer.Reader -> fadeOut(StashMotion.exit(millis)) + slideOutVertically(StashMotion.exit(millis)) { it * 7 / 100 }
        Layer.Panel -> slideOutOfContainer(SlideDirection.End, StashMotion.exit(millis))
        Layer.Home -> ExitTransition.None
    }
}
