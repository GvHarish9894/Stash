package com.techgv.stash.feature.streak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel

/**
 * The streak sheet, placed by `shared` above any screen (it fills its parent). `shared` owns [visible]: it opens the
 * sheet for the shelf's streak chip and closes it in [onDismiss]. "Reminders" and "Read something short" close the
 * sheet, then call [onOpenReminders] (Settings at the reminders group) or [onOpenReader] ([onOpenInBrowser] when "Open
 * links in" is Browser; it returns whether a browser showed the page); a first read of the day also reaches [onCelebrate].
 * [notificationsPermitted] is `NotificationPermission.isGranted`.
 */
@Composable
fun StreakSheetRoute(
    visible: Boolean,
    onDismiss: () -> Unit,
    onOpenReader: (id: ItemId, markedRead: Boolean) -> Unit,
    onOpenInBrowser: (url: String) -> Boolean,
    onOpenReminders: () -> Unit,
    onCelebrate: (Celebration) -> Unit,
    notificationsPermitted: Boolean,
) {
    val viewModel = koinViewModel<StreakSheetViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is StreakSheetEffect.OpenReader -> {
                    onDismiss()
                    onOpenReader(effect.id, effect.markedRead)
                }
                is StreakSheetEffect.OpenInBrowser -> {
                    onDismiss()
                    viewModel.onIntent(StreakSheetIntent.BrowserOpened(onOpenInBrowser(effect.url)))
                }
                StreakSheetEffect.OpenReminders -> {
                    onDismiss()
                    onOpenReminders()
                }
                is StreakSheetEffect.Celebrate -> onCelebrate(effect.celebration)
            }
        }
    }
    StreakSheetScreen(visible, state, viewModel::onIntent, onDismiss, notificationsPermitted)
}
