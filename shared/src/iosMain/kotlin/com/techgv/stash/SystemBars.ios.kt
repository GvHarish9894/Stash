package com.techgv.stash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

// Needs UIViewControllerBasedStatusBarAppearance = NO in Info.plist; SwiftUI's host controller ignores Compose otherwise.
@Composable
actual fun SystemBars(darkIcons: Boolean) {
    SideEffect {
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.setStatusBarStyle(
            if (darkIcons) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, animated = true,
        )
    }
}
