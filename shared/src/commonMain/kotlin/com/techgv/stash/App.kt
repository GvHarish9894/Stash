package com.techgv.stash

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.techgv.stash.model.Screen
import com.techgv.stash.ui.ArchiveScreen
import com.techgv.stash.ui.Celebration
import com.techgv.stash.ui.HomeScreen
import com.techgv.stash.ui.MenuSheet
import com.techgv.stash.ui.NotificationBanner
import com.techgv.stash.ui.ReaderScreen
import com.techgv.stash.ui.SaveScreen
import com.techgv.stash.ui.SearchScreen
import com.techgv.stash.ui.SettingsScreen
import com.techgv.stash.ui.StreakSheet
import com.techgv.stash.ui.UndoToast
import com.techgv.stash.ui.WidgetScreen
import com.techgv.stash.ui.components.ToastHost
import com.techgv.stash.ui.theme.LocalStrings
import com.techgv.stash.ui.theme.Stash
import com.techgv.stash.ui.theme.StashTheme
import com.techgv.stash.ui.theme.stringsFor

/** Lets the platform match status/navigation bar icons to the app's own light or dark theme. */
@Composable
expect fun SystemBars(darkIcons: Boolean)

private fun depth(s: Screen) = when (s) {
    Screen.Home -> 0
    Screen.Search, Screen.Settings, Screen.Archive -> 1
    Screen.Widget, Screen.Save -> 2
    Screen.Reader -> 3
}

private val Out = CubicBezierEasing(.2f, .8f, .2f, 1f)

/** Page transitions from the design: side panels slide, search drops in, the reader rises. */
private fun transition(from: Screen, to: Screen, calm: Boolean): ContentTransform {
    if (calm) return EnterTransition.None togetherWith ExitTransition.None
    val forward = depth(to) >= depth(from)
    val panel = setOf(Screen.Settings, Screen.Archive, Screen.Widget)
    val t = when {
        to == Screen.Reader -> (slideInVertically(tween(360, easing = Out)) { it * 9 / 100 } + fadeIn(tween(360))) togetherWith
            scaleOut(tween(360), targetScale = .93f) + fadeOut(tween(360), targetAlpha = .6f)
        from == Screen.Reader -> (scaleIn(tween(360), initialScale = .93f) + fadeIn(tween(240))) togetherWith
            (slideOutVertically(tween(240)) { it * 7 / 100 } + fadeOut(tween(240)))
        to == Screen.Save || from == Screen.Save -> fadeIn(tween(250)) togetherWith fadeOut(tween(240))
        to == Screen.Search -> (slideInVertically(tween(240, easing = Out)) { -it / 60 } + fadeIn(tween(240))) togetherWith
            (scaleOut(tween(340), targetScale = .97f) + fadeOut(tween(340), targetAlpha = .8f))
        from == Screen.Search -> fadeIn(tween(220)) togetherWith (slideOutVertically(tween(220)) { -it / 100 } + fadeOut(tween(220)))
        forward && to in panel -> slideInHorizontally(tween(340, easing = Out)) { it } togetherWith
            slideOutHorizontally(tween(340, easing = Out)) { -it * 22 / 100 }
        from in panel -> slideInHorizontally(tween(240)) { -it * 22 / 100 } togetherWith slideOutHorizontally(tween(240)) { it }
        else -> fadeIn(tween(240)) togetherWith fadeOut(tween(240))
    }
    t.targetContentZIndex = if (forward) 1f else -1f
    return t
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun App() {
    val vm: StashViewModel = viewModel { StashViewModel() }
    val s = vm.state
    val p = s.prefs
    StashTheme(p.theme, p.accent, p.textSize, p.calm) {
        CompositionLocalProvider(LocalStrings provides stringsFor(p.lang)) {
            val c = Stash.colors
            val calm = Stash.calm
            SystemBars(darkIcons = !c.dark && s.screen != Screen.Reader)
            val homeList = rememberLazyListState()
            val overlay = s.celebrate || s.clearConfirm || s.archPending != null || s.menuId != null ||
                s.addOpen || s.streakOpen || s.notif != null
            BackHandler(enabled = overlay || s.screen != Screen.Home) { vm.back() }

            Box(Modifier.fillMaxSize().background(c.bg)) {
                AnimatedContent(s.screen, transitionSpec = { transition(initialState, targetState, calm) }, label = "screen") { screen ->
                    when (screen) {
                        Screen.Home -> HomeScreen(s, vm, homeList)
                        Screen.Search -> SearchScreen(s, vm)
                        Screen.Archive -> ArchiveScreen(s, vm)
                        Screen.Settings -> SettingsScreen(s, vm)
                        Screen.Widget -> WidgetScreen(s, vm)
                        Screen.Reader -> ReaderScreen(s, vm)
                        Screen.Save -> SaveScreen(s, vm)
                    }
                }
                ToastHost(s.homeToast, if (s.screen == Screen.Home) 96.dp else 28.dp)
                UndoToast(s, vm)
                MenuSheet(s, vm)
                StreakSheet(s, vm)
                NotificationBanner(s, vm)
                Celebration(s, vm)
            }
        }
    }
}
