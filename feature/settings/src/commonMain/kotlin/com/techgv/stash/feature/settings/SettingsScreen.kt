package com.techgv.stash.feature.settings

import androidx.compose.ui.unit.Dp
import com.techgv.stash.core.resources.text_size_large_a11y
import com.techgv.stash.core.resources.text_size_medium_a11y
import com.techgv.stash.core.resources.text_size_small_a11y
import com.techgv.stash.core.designsystem.component.rememberRadioGroupFocus
import com.techgv.stash.core.designsystem.component.radioGroupOption
import com.techgv.stash.core.designsystem.component.RowOrStack
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.AccentSwatch
import com.techgv.stash.core.designsystem.component.AppIconTile
import com.techgv.stash.core.designsystem.component.Badge
import com.techgv.stash.core.designsystem.component.Candle
import com.techgv.stash.core.designsystem.component.CardLayoutPreview
import com.techgv.stash.core.designsystem.component.FilterChip
import com.techgv.stash.core.designsystem.component.InlineConfirm
import com.techgv.stash.core.designsystem.component.OptionCard
import com.techgv.stash.core.designsystem.component.ReminderPreviewBanner
import com.techgv.stash.core.designsystem.component.ScreenHeader
import com.techgv.stash.core.designsystem.component.SegmentedControl
import com.techgv.stash.core.designsystem.component.SettingsBlock
import com.techgv.stash.core.designsystem.component.SettingsDivider
import com.techgv.stash.core.designsystem.component.SettingsGroup
import com.techgv.stash.core.designsystem.component.SettingsLabels
import com.techgv.stash.core.designsystem.component.SettingsNavRow
import com.techgv.stash.core.designsystem.component.SettingsRow
import com.techgv.stash.core.designsystem.component.SettingsSwitchRow
import com.techgv.stash.core.designsystem.component.StashIcons
import com.techgv.stash.core.designsystem.component.ThemePreview
import com.techgv.stash.core.designsystem.component.ToastHost
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.contentWidth
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.exit
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.component.streakLook
import com.techgv.stash.core.designsystem.component.streakSheetStatus
import com.techgv.stash.core.designsystem.component.streakStatusText
import com.techgv.stash.core.designsystem.component.swipeActionIcon
import com.techgv.stash.core.designsystem.presentation.label
import com.techgv.stash.core.designsystem.presentation.resolve
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.AppIcon
import com.techgv.stash.core.model.AppLanguage
import com.techgv.stash.core.model.AutoArchivePeriod
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.OpenLinksIn
import com.techgv.stash.core.model.ReminderLevel
import com.techgv.stash.core.model.ReminderTime
import com.techgv.stash.core.model.ShelfGrouping
import com.techgv.stash.core.model.ShelfSort
import com.techgv.stash.core.model.SwipeAction
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_back_to_shelf
import com.techgv.stash.core.resources.common_cancel
import com.techgv.stash.core.resources.common_clear
import com.techgv.stash.core.resources.common_item_count
import com.techgv.stash.core.resources.nav_settings
import com.techgv.stash.core.resources.reminder_time_evening
import com.techgv.stash.core.resources.settings_accent
import com.techgv.stash.core.resources.settings_accent_body
import com.techgv.stash.core.resources.settings_app_icon
import com.techgv.stash.core.resources.settings_app_icon_body
import com.techgv.stash.core.resources.settings_auto_archive
import com.techgv.stash.core.resources.settings_auto_archive_body
import com.techgv.stash.core.resources.settings_auto_archive_confirm
import com.techgv.stash.core.resources.settings_badge_phase_2
import com.techgv.stash.core.resources.settings_badge_soon
import com.techgv.stash.core.resources.settings_card_layout
import com.techgv.stash.core.resources.settings_card_layout_body
import com.techgv.stash.core.resources.settings_clear_read
import com.techgv.stash.core.resources.settings_clear_read_body
import com.techgv.stash.core.resources.settings_clear_read_confirm
import com.techgv.stash.core.resources.settings_clear_read_detail
import com.techgv.stash.core.resources.settings_clear_read_none
import com.techgv.stash.core.resources.settings_daily_reminder
import com.techgv.stash.core.resources.settings_daily_reminder_body
import com.techgv.stash.core.resources.settings_drive_backup
import com.techgv.stash.core.resources.settings_drive_backup_body
import com.techgv.stash.core.resources.settings_export
import com.techgv.stash.core.resources.settings_export_body
import com.techgv.stash.core.resources.settings_group_by
import com.techgv.stash.core.resources.settings_group_by_body
import com.techgv.stash.core.resources.settings_group_by_source_body
import com.techgv.stash.core.resources.settings_language
import com.techgv.stash.core.resources.settings_language_body
import com.techgv.stash.core.resources.settings_language_system_body
import com.techgv.stash.core.resources.settings_mark_read_on_open
import com.techgv.stash.core.resources.settings_mark_read_on_open_body
import com.techgv.stash.core.resources.settings_move_now
import com.techgv.stash.core.resources.settings_not_yet
import com.techgv.stash.core.resources.settings_notifications_denied_body
import com.techgv.stash.core.resources.settings_open_archive
import com.techgv.stash.core.resources.settings_open_links_in
import com.techgv.stash.core.resources.settings_open_notification_settings
import com.techgv.stash.core.resources.settings_preview_11pm
import com.techgv.stash.core.resources.settings_preview_daily
import com.techgv.stash.core.resources.settings_preview_note
import com.techgv.stash.core.resources.settings_preview_reminder
import com.techgv.stash.core.resources.settings_preview_reminder_body
import com.techgv.stash.core.resources.settings_privacy
import com.techgv.stash.core.resources.settings_privacy_body
import com.techgv.stash.core.resources.settings_reader_default
import com.techgv.stash.core.resources.settings_reader_default_body
import com.techgv.stash.core.resources.settings_reduce_motion
import com.techgv.stash.core.resources.settings_reduce_motion_body
import com.techgv.stash.core.resources.settings_remind_me
import com.techgv.stash.core.resources.settings_section_about
import com.techgv.stash.core.resources.settings_section_account
import com.techgv.stash.core.resources.settings_section_appearance
import com.techgv.stash.core.resources.settings_section_data
import com.techgv.stash.core.resources.settings_section_home
import com.techgv.stash.core.resources.settings_section_reading
import com.techgv.stash.core.resources.settings_section_shelf
import com.techgv.stash.core.resources.settings_section_streak
import com.techgv.stash.core.resources.settings_section_swipe
import com.techgv.stash.core.resources.settings_sections
import com.techgv.stash.core.resources.settings_show_tip
import com.techgv.stash.core.resources.settings_show_tip_body
import com.techgv.stash.core.resources.settings_sign_in
import com.techgv.stash.core.resources.settings_sign_in_body
import com.techgv.stash.core.resources.settings_sort
import com.techgv.stash.core.resources.settings_sort_body
import com.techgv.stash.core.resources.settings_source_code
import com.techgv.stash.core.resources.settings_source_code_url
import com.techgv.stash.core.resources.settings_streak_saver
import com.techgv.stash.core.resources.settings_streak_saver_body
import com.techgv.stash.core.resources.settings_swipe_archive_note
import com.techgv.stash.core.resources.settings_swipe_left
import com.techgv.stash.core.resources.settings_swipe_left_body
import com.techgv.stash.core.resources.settings_swipe_right
import com.techgv.stash.core.resources.settings_swipe_right_body
import com.techgv.stash.core.resources.settings_text_size
import com.techgv.stash.core.resources.settings_text_size_sample
import com.techgv.stash.core.resources.settings_theme
import com.techgv.stash.core.resources.settings_version
import com.techgv.stash.core.resources.settings_widget
import com.techgv.stash.core.resources.settings_widget_body
import com.techgv.stash.core.resources.streak_title
import com.techgv.stash.core.resources.time_9_30_pm
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource
import kotlin.time.Duration

/**
 * Every settings group in the handoff's order. [widthClass] Expanded shows two panes inside this screen: the sections
 * list on the start side and the selected group beside it. [initialSection] scrolls to (or selects) a group, e.g. the
 * streak sheet's "Reminders". Values the OS owns arrive as parameters: [canChangeIcon] hides the app-icon block,
 * [languageInApp] picks between the language picker and a row that opens the system settings, [currentLanguageTag]
 * marks the selected language, [previewTimeout] gives how long a reminder preview stays (asked for each preview).
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun SettingsScreen(
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    widthClass: WidthClass,
    canChangeIcon: Boolean,
    languageInApp: Boolean,
    currentLanguageTag: String?,
    appVersion: String,
    previewTimeout: () -> Duration,
    modifier: Modifier = Modifier,
    initialSection: SettingsSection? = null,
) {
    val c = Stash.colors
    BackHandler(enabled = state.hasTopLayer) { onIntent(SettingsIntent.DismissTopLayer) }
    val group: @Composable (SettingsSection) -> Unit = { section ->
        SettingsGroupFor(section, state, onIntent, canChangeIcon, languageInApp, currentLanguageTag, appVersion, previewTimeout)
    }
    // Opening the screen moves focus to its back button (the first focusable child of the header).
    val headerFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { headerFocus.requestFocus() }
    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().statusBarsPad()) {
            val expanded = widthClass == WidthClass.Expanded
            ScreenHeader(
                stringResource(Res.string.nav_settings), stringResource(Res.string.common_back_to_shelf),
                { onIntent(SettingsIntent.NavigateBack) }, (if (expanded) Modifier else Modifier.contentWidth()).focusRequester(headerFocus),
            )
            if (state.loaded) {
                if (expanded) TwoPane(initialSection, Modifier.weight(1f), group) else SingleColumn(initialSection, Modifier.weight(1f), group)
            }
        }
        val preview = rememberLast(state.preview)
        AnimatedVisibility(
            state.preview != null, Modifier.align(Alignment.TopCenter).statusBarsPad().padding(10.dp),
            enter = enter(slideInVertically(StashMotion.enter(StashMotion.NOTIFICATION_IN)) { -it * 2 }),
            exit = exit(slideOutVertically(StashMotion.exit(StashMotion.NOTIFICATION_OUT)) { -it * 2 }),
        ) {
            if (preview != null) {
                ReminderPreviewBanner(
                    preview.time.resolve(), preview.title, preview.body, state.prefs.appIcon,
                    onLater = { onIntent(SettingsIntent.DismissPreview) },
                    onReadNow = { onIntent(SettingsIntent.ReadPreviewItem) },
                    modifier = Modifier.contentWidth(),
                )
            }
        }
        ToastHost(state.toast?.resolve().orEmpty(), 32.dp)
    }
}

@Composable
private fun SingleColumn(initialSection: SettingsSection?, modifier: Modifier, group: @Composable (SettingsSection) -> Unit) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = initialSection?.ordinal ?: 0)
    LazyColumn(
        modifier.contentWidth().navBarsPad(),
        state = list,
        contentPadding = PaddingValues(start = Stash.spacing.xl, end = Stash.spacing.xl, top = Stash.spacing.xs, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(Stash.spacing.lg),
    ) {
        items(SettingsSection.entries, key = { it.name }) { group(it) }
    }
}

/** Expanded width: the sections list (single selection) on the start side, the selected group beside it. */
@Composable
private fun TwoPane(initialSection: SettingsSection?, modifier: Modifier, group: @Composable (SettingsSection) -> Unit) {
    val c = Stash.colors
    var selected by rememberSaveable { mutableStateOf(initialSection ?: SettingsSection.Appearance) }
    Row(
        modifier.fillMaxWidth().navBarsPad().padding(horizontal = Stash.spacing.xl),
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.lg),
    ) {
        Column(Modifier.width(SectionsPaneWidth).verticalScroll(rememberScrollState()).padding(top = Stash.spacing.xs, bottom = 40.dp)) {
            SettingsGroup(stringResource(Res.string.settings_sections), Modifier.selectableGroup()) {
                SettingsSection.entries.forEachIndexed { i, section ->
                    if (i > 0) SettingsDivider()
                    val on = section == selected
                    SettingsNavRow(
                        stringResource(section.title), null, { selected = section },
                        Modifier.semantics { this.selected = on },
                        titleColor = if (on) c.primary else c.ink,
                    )
                }
            }
        }
        key(selected) {
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(top = Stash.spacing.xs, bottom = 40.dp)) {
                Box(Modifier.contentWidth()) { group(selected) }
            }
        }
    }
}

private val SectionsPaneWidth = 320.dp

private val SettingsSection.title: StringResource
    get() = when (this) {
        SettingsSection.Appearance -> Res.string.settings_section_appearance
        SettingsSection.Reading -> Res.string.settings_section_reading
        SettingsSection.Streak -> Res.string.settings_section_streak
        SettingsSection.Swipe -> Res.string.settings_section_swipe
        SettingsSection.Shelf -> Res.string.settings_section_shelf
        SettingsSection.Home -> Res.string.settings_section_home
        SettingsSection.Account -> Res.string.settings_section_account
        SettingsSection.Data -> Res.string.settings_section_data
        SettingsSection.About -> Res.string.settings_section_about
    }

/** The language the app shows for [tag] ("en-US" is English); null when it is none of the app's languages. */
fun appLanguageOf(tag: String?): AppLanguage? = AppLanguage.entries.firstOrNull { it.tag == tag?.substringBefore('-') }

/** Test tag of a section's group, for UI tests and the integration task. */
fun settingsSectionTag(section: SettingsSection) = "settings-section-${section.name}"

@Composable
private fun str(res: StringResource) = stringResource(res)

@Composable
private fun SettingsGroupFor(
    section: SettingsSection,
    state: SettingsState,
    onIntent: (SettingsIntent) -> Unit,
    canChangeIcon: Boolean,
    languageInApp: Boolean,
    currentLanguageTag: String?,
    appVersion: String,
    previewTimeout: () -> Duration,
) {
    val c = Stash.colors
    val p = state.prefs
    SettingsGroup(str(section.title), Modifier.testTag(settingsSectionTag(section))) {
        when (section) {
            SettingsSection.Appearance -> {
                SettingsBlock(str(Res.string.settings_theme)) {
                    RadioCards(ThemeMode.entries, p.theme, { onIntent(SettingsIntent.SetTheme(it)) }, 10.dp) { m, mod ->
                        OptionCard(str(m.label), p.theme == m, { onIntent(SettingsIntent.SetTheme(m)) }, mod) { ThemePreview(m) }
                    }
                }
                SettingsDivider()
                SettingsRow(str(Res.string.settings_accent), str(Res.string.settings_accent_body)) {
                    Row(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val group = rememberRadioGroupFocus()
                        val accents = Accent.entries
                        accents.forEachIndexed { i, a ->
                            AccentSwatch(
                                a, str(a.label), p.accent == a, onClick = { onIntent(SettingsIntent.SetAccent(a)) },
                                Modifier.radioGroupOption(group, i, accents.indexOf(p.accent), accents.size) { onIntent(SettingsIntent.SetAccent(accents[it])) },
                            )
                        }
                    }
                }
                SettingsDivider()
                SettingsBlock(str(Res.string.settings_card_layout), str(Res.string.settings_card_layout_body)) {
                    RadioCards(CardLayout.entries, p.cardLayout, { onIntent(SettingsIntent.SetCardLayout(it)) }, 10.dp) { l, mod ->
                        OptionCard(str(l.label), p.cardLayout == l, { onIntent(SettingsIntent.SetCardLayout(l)) }, mod) { CardLayoutPreview(l) }
                    }
                }
                SettingsDivider()
                SettingsRow(str(Res.string.settings_text_size), extra = {
                    Txt(str(Res.string.settings_text_size_sample), Stash.type.cardTitle, c.inkFaded)
                }) {
                    SegmentedControl(
                        TextSize.entries, p.textSize, { str(it.label) }, { onIntent(SettingsIntent.SetTextSize(it)) },
                        description = {
                            str(
                                when (it) {
                                    TextSize.Small -> Res.string.text_size_small_a11y
                                    TextSize.Medium -> Res.string.text_size_medium_a11y
                                    TextSize.Large -> Res.string.text_size_large_a11y
                                },
                            )
                        },
                    )
                }
                SettingsDivider()
                SettingsSwitchRow(
                    str(Res.string.settings_reduce_motion), str(Res.string.settings_reduce_motion_body), p.reduceMotion,
                    { onIntent(SettingsIntent.SetReduceMotion(it)) },
                )
            }
            SettingsSection.Reading -> {
                SettingsSwitchRow(
                    str(Res.string.settings_mark_read_on_open), str(Res.string.settings_mark_read_on_open_body), p.markReadOnOpen,
                    { onIntent(SettingsIntent.SetMarkReadOnOpen(it)) },
                )
                SettingsDivider()
                SettingsSwitchRow(
                    str(Res.string.settings_reader_default), str(Res.string.settings_reader_default_body), p.readerModeDefault,
                    { onIntent(SettingsIntent.SetReaderModeDefault(it)) },
                )
                SettingsDivider()
                SettingsRow(str(Res.string.settings_open_links_in)) {
                    SegmentedControl(OpenLinksIn.entries, p.openLinksIn, { str(it.label) }, { onIntent(SettingsIntent.SetOpenLinksIn(it)) })
                }
            }
            SettingsSection.Streak -> StreakGroupContent(state, onIntent, previewTimeout)
            SettingsSection.Swipe -> {
                // Stored as start / end (decision D6); the labels name the physical direction for the layout direction.
                val ltr = LocalLayoutDirection.current == LayoutDirection.Ltr
                SettingsBlock(
                    str(if (ltr) Res.string.settings_swipe_right else Res.string.settings_swipe_left),
                    str(if (ltr) Res.string.settings_swipe_right_body else Res.string.settings_swipe_left_body),
                ) {
                    SwipeOptions(EndSwipeOptions, p.swipeEndAction) { onIntent(SettingsIntent.SetSwipeEnd(it)) }
                }
                SettingsDivider()
                SettingsBlock(
                    str(if (ltr) Res.string.settings_swipe_left else Res.string.settings_swipe_right),
                    str(if (ltr) Res.string.settings_swipe_left_body else Res.string.settings_swipe_right_body),
                ) {
                    SwipeOptions(StartSwipeOptions, p.swipeStartAction) { onIntent(SettingsIntent.SetSwipeStart(it)) }
                }
                Txt(
                    str(Res.string.settings_swipe_archive_note), Stash.type.meta, c.inkFaded,
                    Modifier.padding(start = Stash.spacing.md, end = Stash.spacing.md, bottom = 14.dp),
                )
            }
            SettingsSection.Shelf -> {
                SettingsBlock(str(Res.string.settings_sort), str(Res.string.settings_sort_body)) {
                    SegmentedControl(ShelfSort.entries, p.sort, { str(it.label) }, { onIntent(SettingsIntent.SetSort(it)) }, Modifier.fillMaxWidth(), fill = true)
                }
                SettingsDivider()
                val bySource = p.sort == ShelfSort.BySource
                SettingsBlock(
                    str(Res.string.settings_group_by),
                    str(if (bySource) Res.string.settings_group_by_source_body else Res.string.settings_group_by_body),
                ) {
                    SegmentedControl(
                        ShelfGrouping.entries, p.group.takeIf { !bySource }, { str(it.label) }, { onIntent(SettingsIntent.SetGroup(it)) },
                        Modifier.fillMaxWidth(), fill = true,
                    )
                }
                SettingsDivider()
                SettingsBlock(str(Res.string.settings_auto_archive), str(Res.string.settings_auto_archive_body)) {
                    SegmentedControl(
                        AutoArchivePeriod.entries, p.autoArchive, { it.label.resolve() }, { onIntent(SettingsIntent.SetAutoArchive(it)) },
                        Modifier.fillMaxWidth(), fill = true,
                    )
                    val confirm = rememberLast(state.autoArchiveConfirm)
                    AnimatedVisibility(state.autoArchiveConfirm != null, enter = enter(fadeIn(tween(StashMotion.TOAST)))) {
                        val n = confirm?.count ?: 0
                        InlineConfirm(
                            pluralStringResource(Res.plurals.settings_auto_archive_confirm, n, n), null,
                            str(Res.string.settings_not_yet), { onIntent(SettingsIntent.MoveDueLater) },
                            str(Res.string.settings_move_now), { onIntent(SettingsIntent.MoveDueNow) }, c.primary, c.bg,
                            focusRequester = remember { FocusRequester() },
                        )
                    }
                }
                SettingsDivider()
                SettingsNavRow(
                    str(Res.string.settings_open_archive), pluralStringResource(Res.plurals.common_item_count, state.archivedCount, state.archivedCount),
                    { onIntent(SettingsIntent.OpenArchive) },
                )
                SettingsDivider()
                SettingsNavRow(str(Res.string.settings_show_tip), str(Res.string.settings_show_tip_body), { onIntent(SettingsIntent.ShowTipAgain) })
            }
            SettingsSection.Home -> {
                SettingsNavRow(str(Res.string.settings_widget), str(Res.string.settings_widget_body), { onIntent(SettingsIntent.OpenWidgetPreview) })
                if (canChangeIcon) {
                    SettingsDivider()
                    SettingsBlock(str(Res.string.settings_app_icon), str(Res.string.settings_app_icon_body), gap = Stash.spacing.sm) {
                        RadioCards(AppIcon.entries, p.appIcon, { onIntent(SettingsIntent.ChooseAppIcon(it)) }, Stash.spacing.xs) { icon, mod ->
                            OptionCard(str(icon.label), p.appIcon == icon, { onIntent(SettingsIntent.ChooseAppIcon(icon)) }, mod) {
                                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { AppIconTile(icon, 48.dp, 14.dp) }
                            }
                        }
                    }
                }
                SettingsDivider()
                if (languageInApp) {
                    SettingsBlock(str(Res.string.settings_language), str(Res.string.settings_language_body)) {
                        SegmentedControl(
                            AppLanguage.entries, appLanguageOf(currentLanguageTag), { str(it.label) }, { onIntent(SettingsIntent.ChooseLanguage(it)) },
                            Modifier.fillMaxWidth(), fill = true,
                        )
                    }
                } else {
                    val current = str((appLanguageOf(currentLanguageTag) ?: AppLanguage.System).label)
                    SettingsNavRow(
                        str(Res.string.settings_language), stringResource(Res.string.settings_language_system_body, current),
                        { onIntent(SettingsIntent.OpenLanguageSettings) }, trailingIcon = StashIcons.external(),
                    )
                }
            }
            // Decision D1: this group stays exactly as it was (same rows, badges and look) and does nothing.
            SettingsSection.Account -> {
                SettingsRow(str(Res.string.settings_sign_in), str(Res.string.settings_sign_in_body)) { Badge(str(Res.string.settings_badge_phase_2), c.brass) }
                SettingsDivider()
                SettingsRow(str(Res.string.settings_drive_backup), str(Res.string.settings_drive_backup_body)) { Badge(str(Res.string.settings_badge_phase_2), c.brass) }
                SettingsDivider()
                SettingsRow(str(Res.string.settings_export), str(Res.string.settings_export_body)) { Badge(str(Res.string.settings_badge_soon), null, c.variant, c.inkFaded) }
            }
            SettingsSection.Data -> {
                val confirm = state.clearReadConfirm
                if (confirm == null) {
                    val n = state.readCount
                    SettingsNavRow(
                        str(Res.string.settings_clear_read),
                        if (n > 0) pluralStringResource(Res.plurals.settings_clear_read_body, n, n) else str(Res.string.settings_clear_read_none),
                        { onIntent(SettingsIntent.ClearRead) }, chevron = false, titleColor = c.danger,
                    )
                } else {
                    Box(Modifier.padding(horizontal = Stash.spacing.xxs, vertical = 2.dp)) {
                        InlineConfirm(
                            pluralStringResource(Res.plurals.settings_clear_read_confirm, confirm, confirm), str(Res.string.settings_clear_read_detail),
                            str(Res.string.common_cancel), { onIntent(SettingsIntent.CancelClearRead) },
                            str(Res.string.common_clear), { onIntent(SettingsIntent.ConfirmClearRead) }, c.danger, Color.Transparent, bordered = false,
                            focusRequester = remember { FocusRequester() },
                        )
                    }
                }
            }
            SettingsSection.About -> {
                SettingsNavRow(
                    str(Res.string.settings_source_code), str(Res.string.settings_source_code_url), { onIntent(SettingsIntent.OpenSourceCode) },
                    leading = {
                        Box(Modifier.size(36.dp).clip(CircleShape).background(c.ink), contentAlignment = Alignment.Center) {
                            Icon(StashIcons.github(), null, Modifier.size(20.dp), tint = c.bg)
                        }
                    },
                    trailingIcon = StashIcons.external(),
                )
                SettingsDivider()
                SettingsRow(str(Res.string.settings_privacy), str(Res.string.settings_privacy_body))
                SettingsDivider()
                SettingsRow(str(Res.string.settings_version)) { Txt(appVersion, Stash.type.callout, c.inkFaded) }
            }
        }
    }
}

@Composable
private fun StreakGroupContent(state: SettingsState, onIntent: (SettingsIntent) -> Unit, previewTimeout: () -> Duration) {
    val c = Stash.colors
    val p = state.prefs
    // Without notification permission no reminder arrives: the switches say so, and turning one on asks again.
    val remindersDeliver = !state.notificationsDenied
    val look = streakLook(state.streakStatus)
    Row(
        Modifier.padding(horizontal = Stash.spacing.md, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).background(look.bg), contentAlignment = Alignment.Center) {
            Candle(26.dp, c.inkFaded, look.flame, flicker = look.flicker)
        }
        SettingsLabels(
            stringResource(Res.string.streak_title, state.streak),
            streakStatusText(streakSheetStatus(state.readToday, state.unreadCount > 0), state.streak),
        )
    }
    SettingsDivider()
    SettingsSwitchRow(
        str(Res.string.settings_daily_reminder), str(Res.string.settings_daily_reminder_body), p.dailyReminder && remindersDeliver,
        { onIntent(SettingsIntent.SetDailyReminder(it)) },
    )
    SettingsDivider()
    SettingsRow(str(Res.string.settings_remind_me)) {
        SegmentedControl(ReminderTime.entries, p.reminderTime, { str(it.label) }, { onIntent(SettingsIntent.SetReminderTime(it)) })
    }
    SettingsDivider()
    SettingsSwitchRow(
        str(Res.string.settings_streak_saver), str(Res.string.settings_streak_saver_body), p.streakSaver && remindersDeliver,
        { onIntent(SettingsIntent.SetStreakSaver(it)) },
    )
    if (state.notificationsDenied) {
        SettingsDivider()
        SettingsNavRow(
            str(Res.string.settings_open_notification_settings), str(Res.string.settings_notifications_denied_body),
            { onIntent(SettingsIntent.OpenNotificationSettings) },
            Modifier.semantics { liveRegion = LiveRegionMode.Polite }, trailingIcon = StashIcons.external(),
        )
    }
    SettingsDivider()
    SettingsBlock(str(Res.string.settings_preview_reminder), str(Res.string.settings_preview_reminder_body)) {
        SegmentedControl(
            ReminderLevel.entries, state.previewSlot, { str(it.previewLabel) }, { onIntent(SettingsIntent.PreviewReminder(it, previewTimeout())) },
            Modifier.fillMaxWidth(), fill = true,
        )
        Txt(str(Res.string.settings_preview_note), Stash.type.meta, c.inkFaded)
    }
}

private val ReminderLevel.previewLabel: StringResource
    get() = when (this) {
        ReminderLevel.Daily -> Res.string.settings_preview_daily
        ReminderLevel.Evening -> Res.string.reminder_time_evening
        ReminderLevel.Late -> Res.string.time_9_30_pm
        ReminderLevel.LastCall -> Res.string.settings_preview_11pm
    }

private val EndSwipeOptions = listOf(SwipeAction.MarkRead, SwipeAction.Archive, SwipeAction.Share, SwipeAction.Remove, SwipeAction.Nothing)
private val StartSwipeOptions = listOf(SwipeAction.Remove, SwipeAction.Archive, SwipeAction.MarkRead, SwipeAction.Share, SwipeAction.Nothing)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SwipeOptions(options: List<SwipeAction>, selected: SwipeAction, onPick: (SwipeAction) -> Unit) {
    val c = Stash.colors
    val group = rememberRadioGroupFocus()
    FlowRow(Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        options.forEach { a ->
            val on = a == selected
            val icon = swipeActionIcon(a)
            FilterChip(
                stringResource(a.label), on, { onPick(a) },
                Modifier.radioGroupOption(group, options.indexOf(a), options.indexOf(selected), options.size) { onPick(options[it]) },
                leading = icon?.let { { Icon(it, null, Modifier.size(18.dp), tint = if (on) c.onPrimary else c.ink) } },
                role = Role.RadioButton,
            )
        }
    }
}

/**
 * Option cards side by side with equal widths, or stacked when a label's longest word would not fit (very large text);
 * one radio group: arrow keys, Home and End move the selection, and only the selected card is a Tab stop.
 */
@Composable
private fun <T> RadioCards(options: List<T>, selected: T, onSelect: (T) -> Unit, gap: Dp, card: @Composable (T, Modifier) -> Unit) {
    val group = rememberRadioGroupFocus()
    RowOrStack(List(options.size) { 1f }, Modifier.selectableGroup(), gap = gap) {
        options.forEachIndexed { i, option ->
            card(option, Modifier.radioGroupOption(group, i, options.indexOf(selected), options.size) { onSelect(options[it]) })
        }
    }
}
