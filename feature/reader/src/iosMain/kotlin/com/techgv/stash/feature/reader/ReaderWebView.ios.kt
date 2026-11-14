package com.techgv.stash.feature.reader

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.UIKitInteropProperties
import androidx.compose.ui.viewinterop.UIKitView
import com.techgv.stash.core.designsystem.theme.Stash
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCSignatureOverride
import platform.CoreGraphics.CGRectMake
import platform.Foundation.NSError
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.WebKit.WKNavigation
import platform.WebKit.WKNavigationAction
import platform.WebKit.WKNavigationActionPolicy
import platform.WebKit.WKNavigationDelegateProtocol
import platform.WebKit.WKWebView
import platform.UIKit.UIColor
import platform.WebKit.WKWebViewConfiguration
import platform.darwin.NSObject

/**
 * Applies [httpsUrl] to main-frame navigations and reports progress and failures. No UI delegate, so pages cannot
 * open new windows; a link that targets one loads in this view instead.
 */
private class NavigationDelegate(private val onEvent: () -> (WebEvent) -> Unit) : NSObject(), WKNavigationDelegateProtocol {
    @ObjCSignatureOverride
    override fun webView(
        webView: WKWebView,
        decidePolicyForNavigationAction: WKNavigationAction,
        decisionHandler: (WKNavigationActionPolicy) -> Unit,
    ) {
        val frame = decidePolicyForNavigationAction.targetFrame
        if (frame != null && !frame.mainFrame) return decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        val requested = decidePolicyForNavigationAction.request.URL?.absoluteString
        val target = requested?.let(::httpsUrl)
        if (target != null && target == requested && frame != null) {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyAllow)
        } else {
            decisionHandler(WKNavigationActionPolicy.WKNavigationActionPolicyCancel)
            target?.let { NSURL.URLWithString(it) }?.let { webView.loadRequest(NSURLRequest.requestWithURL(it)) }
        }
    }

    // shortcut: three fixed steps instead of KVO on estimatedProgress; observe it if the bar should move smoothly.
    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didStartProvisionalNavigation: WKNavigation?) {
        onEvent()(WebEvent.Progress(0.1f))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didCommitNavigation: WKNavigation?) {
        onEvent()(WebEvent.Progress(0.6f))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFinishNavigation: WKNavigation?) {
        onEvent()(WebEvent.Progress(1f))
    }

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailProvisionalNavigation: WKNavigation?, withError: NSError) = failed(withError)

    @ObjCSignatureOverride
    override fun webView(webView: WKWebView, didFailNavigation: WKNavigation?, withError: NSError) = failed(withError)

    private fun failed(error: NSError) {
        if (!isCancelledNavigation(error.domain.orEmpty(), error.code)) onEvent()(WebEvent.Failed)
    }
}

private fun Color.toUIColor() = UIColor(red = red.toDouble(), green = green.toDouble(), blue = blue.toDouble(), alpha = alpha.toDouble())

@OptIn(ExperimentalForeignApi::class)
@Composable
internal actual fun PlatformWebView(url: String, onEvent: (WebEvent) -> Unit, modifier: Modifier) {
    val events by rememberUpdatedState(onEvent)
    // WKWebView holds its navigation delegate weakly; this keeps it alive as long as the view.
    val delegate = remember { NavigationDelegate { events } }
    // Until the page paints, the view shows the theme's background instead of white.
    val background = Stash.colors.bg.toUIColor()
    UIKitView(
        factory = {
            WKWebView(frame = CGRectMake(0.0, 0.0, 0.0, 0.0), configuration = WKWebViewConfiguration()).apply {
                setOpaque(false)
                backgroundColor = background
                scrollView.backgroundColor = background
                navigationDelegate = delegate
                NSURL.URLWithString(url)?.let { loadRequest(NSURLRequest.requestWithURL(it)) }
            }
        },
        modifier = modifier,
        update = { view ->
            view.backgroundColor = background
            view.scrollView.backgroundColor = background
        },
        // Off by default; without it VoiceOver gets nothing of the page.
        properties = UIKitInteropProperties(isNativeAccessibilityEnabled = true),
        onRelease = { view ->
            view.stopLoading()
            view.navigationDelegate = null
        },
    )
}
