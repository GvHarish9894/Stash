package com.techgv.stash

import androidx.compose.runtime.Composable

// shortcut: iOS keeps the system status-bar style; wire UIViewController.preferredStatusBarStyle if a forced theme looks wrong.
@Composable
actual fun SystemBars(darkIcons: Boolean) = Unit
