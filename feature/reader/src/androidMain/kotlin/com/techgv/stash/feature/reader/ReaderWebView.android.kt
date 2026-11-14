package com.techgv.stash.feature.reader

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.net.http.SslError
import android.os.Bundle
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.backhandler.BackHandler
import androidx.compose.ui.viewinterop.AndroidView
import com.techgv.stash.core.designsystem.theme.Stash

/** Survives activity re-creation (rotation): the page history saved by [WebView.saveState]. */
private class SavedWebView(val restored: Bundle?) {
    var view: WebView? = null
}

private val SavedWebViewSaver = Saver<SavedWebView, Bundle>(
    save = { saved -> saved.view?.let { view -> Bundle().also { view.saveState(it) } } },
    restore = { SavedWebView(it) },
)

@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalComposeUiApi::class)
@Composable
internal actual fun PlatformWebView(url: String, onEvent: (WebEvent) -> Unit, modifier: Modifier) {
    val events by rememberUpdatedState(onEvent)
    // Until the page paints, the view shows its background: the theme's, never the engine's white.
    val background = Stash.colors.bg.toArgb()
    val saved = rememberSaveable(url, saver = SavedWebViewSaver) { SavedWebView(null) }
    var canGoBack by remember { mutableStateOf(false) }
    // System back walks the page's own history first, then closes the reader.
    BackHandler(enabled = canGoBack) { saved.view?.goBack() }
    AndroidView(
        factory = { context ->
            WebView(context).apply {
                setBackgroundColor(background)
                settings.javaScriptEnabled = true
                settings.allowFileAccess = false
                settings.allowContentAccess = false
                settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                settings.setSupportMultipleWindows(false)
                settings.javaScriptCanOpenWindowsAutomatically = false
                // No DownloadListener: downloads are ignored.
                webViewClient = object : WebViewClient() {
                    // The page the main frame is loading, to tell its certificate errors from a sub-resource's.
                    private var mainUrl: String? = url

                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        val requested = request.url.toString()
                        val target = httpsUrl(requested) ?: return true
                        mainUrl = target
                        if (target == requested) return false
                        view.loadUrl(target)
                        return true
                    }

                    override fun onPageStarted(view: WebView, startedUrl: String, favicon: Bitmap?) {
                        mainUrl = startedUrl
                    }

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) events(WebEvent.Failed)
                    }

                    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                        handler.cancel()
                        if (error.url == mainUrl) events(WebEvent.Failed)
                    }

                    override fun doUpdateVisitedHistory(view: WebView, historyUrl: String?, isReload: Boolean) {
                        canGoBack = view.canGoBack()
                    }
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onProgressChanged(view: WebView, newProgress: Int) {
                        events(WebEvent.Progress(newProgress / 100f))
                    }

                    // The page's console would reach logcat in Stash's process, with page URLs; nothing is logged.
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean = true
                }
                saved.view = this
                if (saved.restored?.let { restoreState(it) } == null) loadUrl(url)
            }
        },
        modifier = modifier,
        update = { it.setBackgroundColor(background) },
        onRelease = { view ->
            if (saved.view === view) saved.view = null
            view.stopLoading()
            view.destroy()
        },
    )
}
