package com.techgv.stash.feature.widgetpreview

import androidx.compose.runtime.Immutable
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.UiText
import com.techgv.stash.core.domain.StreakStatus
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.WidgetSize

@Immutable
data class WidgetPreviewState(
    val loaded: Boolean = false,
    val size: WidgetSize = WidgetSize.Medium,
    val appIcon: AppIcon = AppIcon.Leather,
    val streak: Int = 0,
    val streakStatus: StreakStatus = StreakStatus.Unlit,
    val unreadCount: Int = 0,
    /** The widget's rows: the next unread items from `WidgetSnapshotPolicy`, at most 3. Empty: "All caught up". */
    val items: List<SavedItemCardUi> = emptyList(),
    /** Pinning is not possible here: show how to add the widget from the home screen. */
    val showSteps: Boolean = false,
    val toast: UiText? = null,
)
