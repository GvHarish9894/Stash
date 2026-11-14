package com.techgv.stash.feature.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.domain.Celebration
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.ItemId
import org.koin.compose.viewmodel.koinViewModel
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** The handoff's in-app reminder preview time, before [SettingsRoute]'s `recommendedTimeout` adjusts it. */
val ReminderPreviewTimeout: Duration = 12.seconds

/**
 * The settings destination. Values and actions the OS owns come from `shared`:
 * - [canChangeIcon]: `AppIconSwitcher.isSupported`; [setAppIcon]: `AppIconSwitcher.set`, the setting is stored only on true.
 * - [languageInApp]: `AppLocale.switching == InApp`; [currentLanguageTag]: `AppLocale.current`; [onSetLanguage]:
 *   `AppLocale.set(tag)`; [onOpenLanguageSettings]: `AppLocale.set(null)` on iOS (it opens the app's Settings page).
 * - [requestNotificationPermission]: `NotificationPermission.request`; [onOpenNotificationSettings]: `openSettings()`;
 *   [notificationsPermitted]: `NotificationPermission.isGranted`, read each time the screen comes to the front (null until
 *   read).
 * - [recommendedTimeout]: `A11yPrefs.recommendedTimeout`, applied to the 12 s reminder preview.
 * - [onOpenExternally]: `BrowserLauncher` for the GitHub link, and for "Read now" when "Open links in" is Browser.
 */
@Composable
fun SettingsRoute(
    widthClass: WidthClass,
    canChangeIcon: Boolean,
    languageInApp: Boolean,
    currentLanguageTag: String?,
    appVersion: String,
    recommendedTimeout: (Duration) -> Duration,
    onBack: () -> Unit,
    onOpenArchive: () -> Unit,
    onOpenWidgetPreview: () -> Unit,
    onOpenExternally: (url: String) -> Unit,
    requestNotificationPermission: suspend () -> Boolean,
    onOpenNotificationSettings: () -> Unit,
    notificationsPermitted: Boolean?,
    setAppIcon: suspend (AppIcon) -> Boolean,
    onSetLanguage: (tag: String?) -> Unit,
    onOpenLanguageSettings: () -> Unit,
    onOpenReader: (id: ItemId, markedRead: Boolean) -> Unit,
    onCelebrate: (Celebration) -> Unit,
    modifier: Modifier = Modifier,
    initialSection: SettingsSection? = null,
) {
    val viewModel = koinViewModel<SettingsViewModel>()
    val state by viewModel.state.collectAsStateWithLifecycle()
    val requestPermission by rememberUpdatedState(requestNotificationPermission)
    val applyIcon by rememberUpdatedState(setAppIcon)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                SettingsEffect.Back -> onBack()
                SettingsEffect.OpenArchive -> onOpenArchive()
                SettingsEffect.OpenWidgetPreview -> onOpenWidgetPreview()
                is SettingsEffect.OpenExternally -> onOpenExternally(effect.url)
                SettingsEffect.RequestNotificationPermission ->
                    viewModel.onIntent(SettingsIntent.NotificationPermissionResult(requestPermission()))
                SettingsEffect.OpenNotificationSettings -> onOpenNotificationSettings()
                is SettingsEffect.SetAppIcon -> viewModel.onIntent(SettingsIntent.AppIconResult(effect.icon, applyIcon(effect.icon)))
                is SettingsEffect.SetLanguage -> onSetLanguage(effect.tag)
                SettingsEffect.OpenLanguageSettings -> onOpenLanguageSettings()
                is SettingsEffect.OpenReader -> onOpenReader(effect.id, effect.markedRead)
                is SettingsEffect.Celebrate -> onCelebrate(effect.celebration)
            }
        }
    }
    LaunchedEffect(viewModel, notificationsPermitted) {
        notificationsPermitted?.let { viewModel.onIntent(SettingsIntent.NotificationsPermitted(it)) }
    }
    val previewTimeout = remember(recommendedTimeout) { { recommendedTimeout(ReminderPreviewTimeout) } }
    SettingsScreen(
        state, viewModel::onIntent, widthClass, canChangeIcon, languageInApp, currentLanguageTag, appVersion, previewTimeout,
        modifier, initialSection,
    )
}
