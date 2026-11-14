package com.techgv.stash.feature.reader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import coil3.size.Precision
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.component.EmptyIllustration
import com.techgv.stash.core.designsystem.component.EmptyState
import com.techgv.stash.core.designsystem.component.OutlineButton
import com.techgv.stash.core.designsystem.component.PrimaryButton
import com.techgv.stash.core.designsystem.component.ReaderModeView
import com.techgv.stash.core.designsystem.component.ReaderTopBar
import com.techgv.stash.core.designsystem.component.StashIcons
import com.techgv.stash.core.designsystem.component.Toast
import com.techgv.stash.core.designsystem.component.Txt
import com.techgv.stash.core.designsystem.component.enter
import com.techgv.stash.core.designsystem.component.exit
import com.techgv.stash.core.designsystem.component.navBarsPad
import com.techgv.stash.core.designsystem.component.rememberLast
import com.techgv.stash.core.designsystem.component.statusBarsPad
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.designsystem.theme.motionSpec
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.SavedItem
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.common_close
import com.techgv.stash.core.resources.common_retry
import com.techgv.stash.core.resources.reader_action_failed
import com.techgv.stash.core.resources.reader_empty_body
import com.techgv.stash.core.resources.reader_empty_title
import com.techgv.stash.core.resources.reader_missing_body
import com.techgv.stash.core.resources.reader_missing_title
import com.techgv.stash.core.resources.reader_offline_body
import com.techgv.stash.core.resources.reader_offline_title
import com.techgv.stash.core.resources.reader_open_in_browser
import com.techgv.stash.core.resources.reader_unavailable
import com.techgv.stash.core.resources.toast_back_on_unread
import com.techgv.stash.core.resources.toast_marked_read
import org.jetbrains.compose.resources.stringResource

/** Draws the web view; the UI tests pass a stand-in. */
typealias WebViewSlot = @Composable (url: String, onEvent: (WebEvent) -> Unit, modifier: Modifier) -> Unit

/**
 * The reader: [ReaderTopBar], then the web page or reader mode, the offline and missing-item states and the toasts.
 * Fills the space it is given, so it works as a full screen or as the detail pane.
 */
@Composable
fun ReaderScreen(
    state: ReaderState,
    widthClass: WidthClass,
    onIntent: (ReaderIntent) -> Unit,
    modifier: Modifier = Modifier,
    webView: WebViewSlot = { url, onEvent, m -> PlatformWebView(url, onEvent, m) },
) {
    val c = Stash.colors
    Box(modifier.fillMaxSize().background(c.bg)) {
        if (state.missing) {
            MissingItem(onClose = { onIntent(ReaderIntent.Close) })
        } else {
            Column(Modifier.fillMaxSize()) {
                // Opening the reader moves focus to Close (handoff focus order 2).
                val closeFocus = remember { FocusRequester() }
                LaunchedEffect(Unit) { closeFocus.requestFocus() }
                val bar by animateFloatAsState(
                    loadBarFraction(state.loadProgress),
                    motionSpec(tween(StashMotion.READER_LOAD, easing = StashMotion.LoadBar)),
                )
                ReaderTopBar(
                    title = state.item?.title.orEmpty(),
                    site = state.item?.siteName.orEmpty(),
                    readerMode = state.readerMode == true,
                    loadProgress = { bar },
                    onClose = { onIntent(ReaderIntent.Close) },
                    onToggleReaderMode = { onIntent(ReaderIntent.ToggleReaderMode) },
                    onMarkUnread = { onIntent(ReaderIntent.MarkUnread) },
                    showMarkUnread = state.canMarkUnread,
                    closeFocusRequester = closeFocus,
                )
                // Clipped: a platform web view must never paint over the top bar.
                Box(Modifier.weight(1f).fillMaxWidth().clipToBounds()) {
                    val item = state.item
                    when {
                        item == null || state.readerMode == null -> Unit
                        state.readerMode -> ReaderModeContent(item, state, onIntent)
                        else -> {
                            val url = httpsUrl(item.url)
                            if (state.webFailed || url == null) {
                                OfflineMessage({ onIntent(ReaderIntent.RetryWeb) }, { onIntent(ReaderIntent.OpenInBrowser) })
                            } else {
                                // Created after the reader's first frame, so starting the web engine (on Android it
                                // blocks the main thread the first time) never holds back the top bar and load bar.
                                var started by remember { mutableStateOf(false) }
                                LaunchedEffect(Unit) {
                                    withFrameNanos {}
                                    started = true
                                }
                                if (started) webView(url, { onIntent(ReaderIntent.Web(it)) }, Modifier.fillMaxSize())
                            }
                        }
                    }
                }
            }
        }
        ToastHost(state.toast, widthClass)
    }
}

@Composable
private fun ReaderModeContent(item: SavedItem, state: ReaderState, onIntent: (ReaderIntent) -> Unit) {
    val onTextStep = { step: Int -> onIntent(ReaderIntent.ChangeTextStep(step)) }
    when (val article = state.article) {
        is ArticleState.Ready -> ReaderModeView(
            article.article, state.textStep, onTextStep,
            onLinkClick = { onIntent(ReaderIntent.OpenLink(it)) },
            image = { ArticleImage(it) },
        )
        ArticleState.Offline -> OfflineMessage({ onIntent(ReaderIntent.RetryArticle) }, onOpenInBrowser = null)
        // No blocks yet: ReaderModeView draws the reader skeleton under the item's own headline.
        else -> ReaderModeView(ReaderArticle(item.title, item.siteName, item.description, item.readingMinutes, emptyList()), state.textStep, onTextStep)
    }
}

/**
 * Images no larger than this on both sides (CSS px) are icons and badges, such as Wikipedia's 19–20 px "good article"
 * star and padlock, not article images: reader mode leaves them out.
 */
internal const val SMALL_IMAGE_PX = 48

/**
 * A web image at most as wide as the column and never wider than its own size (its pixels taken as CSS px, as the
 * page shows it); icons and badges ([SMALL_IMAGE_PX]) are left out. It stays invisible until its size is known.
 */
@Composable
private fun ArticleImage(block: ReaderBlock.Image) {
    // Only web images: a file: or content: URL in a page must not read the device's files.
    val url = httpsUrl(block.url)
    if (url == null) {
        block.alt?.let { Txt(it, Stash.type.meta, Stash.colors.inkFaded) }
        return
    }
    var size by remember(url) { mutableStateOf<IntSize?>(null) }
    val known = size
    if (known != null && known.width <= SMALL_IMAGE_PX && known.height <= SMALL_IMAGE_PX) return
    val context = LocalPlatformContext.current
    // Inexact: the decoder never scales a small image up to the requested column width, so its size is its own.
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).precision(Precision.INEXACT).build() }
    AsyncImage(
        request, block.alt,
        Modifier.then(if (known != null) Modifier.widthIn(max = known.width.dp) else Modifier)
            .fillMaxWidth()
            .alpha(if (known != null) 1f else 0f)
            .clip(RoundedCornerShape(Stash.radius.thumbnail)),
        contentScale = ContentScale.FillWidth,
        onSuccess = { size = IntSize(it.result.image.width, it.result.image.height) },
    )
}

/** Handoff offline copy with Retry; the web page also offers the browser, which can open what the in-app view cannot. */
@Composable
private fun OfflineMessage(onRetry: () -> Unit, onOpenInBrowser: (() -> Unit)?) {
    Column(
        Modifier.fillMaxSize().background(Stash.colors.bg).verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(stringResource(Res.string.reader_offline_title), stringResource(Res.string.reader_offline_body), EmptyIllustration.None)
        Column(
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = Stash.spacing.xl),
            verticalArrangement = Arrangement.spacedBy(Stash.spacing.sm),
        ) {
            PrimaryButton(stringResource(Res.string.common_retry), onRetry, Modifier.fillMaxWidth())
            onOpenInBrowser?.let { OutlineButton(stringResource(Res.string.reader_open_in_browser), it, Modifier.fillMaxWidth()) }
        }
    }
}

/** The item no longer exists (a widget row or notification can outlive it): say so and offer Close. */
@Composable
private fun MissingItem(onClose: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPad().verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        EmptyState(stringResource(Res.string.reader_missing_title), stringResource(Res.string.reader_missing_body), EmptyIllustration.None)
        PrimaryButton(
            stringResource(Res.string.common_close), onClose,
            Modifier.widthIn(max = 360.dp).fillMaxWidth().padding(horizontal = Stash.spacing.xl),
        )
    }
}

/** The detail pane on expanded windows before an item is picked. */
@Composable
fun ReaderEmptyPane(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize().background(Stash.colors.bg).verticalScroll(rememberScrollState()), contentAlignment = Alignment.Center) {
        EmptyState(stringResource(Res.string.reader_empty_title), stringResource(Res.string.reader_empty_body), EmptyIllustration.Book)
    }
}

/**
 * Prototype toast: confirmations ("Marked as read", "Back on your unread list") are a green pill with a check;
 * other messages use the design system's toast. Full width on compact windows, 400 dp otherwise.
 */
@Composable
private fun BoxScope.ToastHost(toast: ReaderToast?, widthClass: WidthClass) {
    val shown = rememberLast(toast)
    val width = if (widthClass == WidthClass.Compact) Modifier.fillMaxWidth() else Modifier.width(400.dp)
    AnimatedVisibility(
        toast != null,
        Modifier.align(Alignment.BottomCenter).navBarsPad().padding(start = 16.dp, end = 16.dp, bottom = 28.dp),
        enter = enter(slideInVertically(StashMotion.enter(StashMotion.TOAST)) { it } + fadeIn(tween(StashMotion.TOAST))),
        exit = exit(fadeOut(StashMotion.exit(StashMotion.TOAST))),
    ) {
        when (shown) {
            ReaderToast.MarkedRead, ReaderToast.BackOnUnread -> ConfirmationToast(
                stringResource(if (shown == ReaderToast.MarkedRead) Res.string.toast_marked_read else Res.string.toast_back_on_unread),
                width,
            )
            ReaderToast.ReaderUnavailable -> Toast(stringResource(Res.string.reader_unavailable), width)
            ReaderToast.ActionFailed -> Toast(stringResource(Res.string.reader_action_failed), width)
            null -> Unit
        }
    }
}

@Composable
private fun ConfirmationToast(text: String, modifier: Modifier) {
    val c = Stash.colors
    Row(
        modifier.heightIn(min = 48.dp).clip(CircleShape).background(c.good).padding(horizontal = Stash.spacing.md)
            .semantics(mergeDescendants = true) { liveRegion = LiveRegionMode.Polite },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Stash.spacing.xs, Alignment.CenterHorizontally),
    ) {
        Icon(StashIcons.check(2.2f), null, Modifier.size(16.dp), tint = c.onPrimary)
        Txt(text, Stash.type.bodySmallStrong, c.onPrimary)
    }
}
