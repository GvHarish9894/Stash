package com.techgv.stash.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * The platform web view (Android `WebView`, iOS `WKWebView`) showing [url], an https URL from [httpsUrl].
 * JavaScript on; no file or content access; no mixed content; downloads and new windows are not opened; every
 * main-frame navigation goes through [httpsUrl] (http is re-loaded as https, other schemes are dropped). It reports
 * load progress and main-page failures through [onEvent], and is released when it leaves the composition.
 */
@Composable
internal expect fun PlatformWebView(url: String, onEvent: (WebEvent) -> Unit, modifier: Modifier = Modifier)
