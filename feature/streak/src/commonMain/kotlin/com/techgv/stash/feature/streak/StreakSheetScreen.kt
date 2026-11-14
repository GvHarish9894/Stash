package com.techgv.stash.feature.streak

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import com.techgv.stash.core.designsystem.component.StreakSheet
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.streakSheetStatus
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.shelf_toast_browser_failed
import com.techgv.stash.core.resources.toast_opened_in_browser
import org.jetbrains.compose.resources.stringResource

/**
 * The streak sheet over whatever is underneath; it fills its parent. Back, the scrim and the handle call [onDismiss].
 * Without [notificationsPermitted] no reminder can arrive, so the rule says reminders are off.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun StreakSheetScreen(
    visible: Boolean,
    state: StreakSheetState,
    onIntent: (StreakSheetIntent) -> Unit,
    onDismiss: () -> Unit,
    notificationsPermitted: Boolean = true,
) {
    // Deprecated for NavigationEventHandler, whose artifact is not in the catalog; shared's App uses the same.
    @Suppress("DEPRECATION")
    BackHandler(enabled = visible, onBack = onDismiss)
    val streak = state.streak
    Box(Modifier.fillMaxSize()) {
        StreakSheet(
            visible = visible,
            streak = streak?.count ?: 0,
            status = streak?.status ?: StreakStatus.Unlit,
            sheetStatus = streakSheetStatus(streak?.readToday == true, state.hasUnread),
            week = streak?.week.orEmpty(),
            reminderTime = state.reminderTime.takeIf { notificationsPermitted },
            streakSaver = state.streakSaver,
            onReminders = { onIntent(StreakSheetIntent.OpenReminders) },
            onReadShort = { onIntent(StreakSheetIntent.ReadShort) },
            onDismiss = onDismiss,
            readShortEnabled = state.hasUnread,
        )
        // Above Home's add button, where the shelf shows the same toasts.
        ToastHost(state.toast?.let { stringResource(it.text) }.orEmpty(), bottom = 96.dp)
    }
}

private val StreakToast.text
    get() = when (this) {
        StreakToast.OpenedInBrowser -> Res.string.toast_opened_in_browser
        StreakToast.BrowserFailed -> Res.string.shelf_toast_browser_failed
    }
