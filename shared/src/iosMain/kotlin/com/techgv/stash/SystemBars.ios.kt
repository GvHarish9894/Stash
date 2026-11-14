package com.techgv.stash

import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import platform.UIKit.UIApplication
import platform.UIKit.UIStatusBarStyleDarkContent
import platform.UIKit.UIStatusBarStyleLightContent
import platform.UIKit.setStatusBarStyle

// The Compose view is opaque from its first frame, so the window behind it is never seen: [background] is unused.
// Needs UIViewControllerBasedStatusBarAppearance = NO in Info.plist; SwiftUI's host controller ignores Compose otherwise.
@Composable
actual fun SystemBars(darkIcons: Boolean, @Suppress("UNUSED_PARAMETER") background: Color) {
    SideEffect {
        @Suppress("DEPRECATION")
        UIApplication.sharedApplication.setStatusBarStyle(
            if (darkIcons) UIStatusBarStyleDarkContent else UIStatusBarStyleLightContent, animated = true,
        )
    }
}
