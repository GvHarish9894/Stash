package com.techgv.stash.di

import org.koin.core.module.Module

/**
 * The platform's capability bindings, one `actual` per platform. Each must bind all thirteen `core:platform`
 * capabilities: `ShareInbox`, `NotificationScheduler`, `WidgetPublisher`, `BackgroundJobs`, `Connectivity`,
 * `BrowserLauncher`, `ShareSheet`, `Clipboard`, `WidgetPinner`, `AppIconSwitcher`, `AppLocale`, `A11yPrefs` and
 * `NotificationPermission`.
 *
 * How features reach capabilities: feature modules cannot see `core:platform` (dependency rule 2), so they never call
 * a capability.
 * - Actions go out as effects. A feature ViewModel emits one-off effects (open in browser, share, copy, pin the
 *   widget, ask for notification permission, navigate) on a `Flow`; the navigation host in `shared` collects them and
 *   calls the capability. When the action has a result (`BrowserLauncher.open`, `AppIconSwitcher.set`,
 *   `NotificationPermission.request`), `shared` handles it: it dispatches the `ChangeSetting` or shows the message.
 * - Haptics and announcements never leave the UI: composables call `LocalHapticFeedback` and use semantics
 *   (`liveRegion`, `error()`, `paneTitle`); a confirm / reject haptic arrives as a feature effect the screen handles.
 * - Values come in as parameters. Whatever the UI needs from a capability (the clipboard URL when the user opens the
 *   Add-link sheet, OS reduce motion, the recommended undo timeout, whether the icon can change, how language
 *   switching works) is read by `shared` and passed into the feature's screen or ViewModel as plain parameters or
 *   state, never as the capability itself.
 */
expect val platformModule: Module
