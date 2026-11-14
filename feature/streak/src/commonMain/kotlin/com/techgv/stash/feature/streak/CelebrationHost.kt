package com.techgv.stash.feature.streak

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.backhandler.BackHandler
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techgv.stash.core.designsystem.component.CelebrationDialog
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.presentation.celebrationMessage
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.domain.WeekDay
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * The first-read-of-the-day celebration, placed by `shared` above everything (it fills its parent). It shows while
 * [celebration] is non-null; "Keep reading" and back call [onClose], after which `shared` passes null. `shared` decides
 * when to pass one: the handoff shows it after the reader closes when the read happened there.
 */
@Composable
fun CelebrationHost(celebration: Celebration?, onClose: () -> Unit) {
    val viewModel = koinViewModel<StreakSheetViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    CelebrationScreen(celebration, state.streak?.week.orEmpty(), onClose)
}

/** Stateless [CelebrationHost]: the hero animation (static under Reduce motion) with the regular or milestone message. */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun CelebrationScreen(celebration: Celebration?, week: List<WeekDay>, onClose: () -> Unit) {
    // Deprecated for NavigationEventHandler, whose artifact is not in the catalog; shared's App uses the same.
    @Suppress("DEPRECATION")
    BackHandler(enabled = celebration != null, onBack = onClose)
    // Keeps drawing the last celebration while it fades out.
    val shown = rememberLast(celebration) ?: return
    CelebrationDialog(celebration != null, shown.from, shown.to, stringResource(celebrationMessage(shown)), week, onClose)
}
