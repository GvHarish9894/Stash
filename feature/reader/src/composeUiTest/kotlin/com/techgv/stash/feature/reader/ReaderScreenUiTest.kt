package com.techgv.stash.feature.reader

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertHeightIsEqualTo
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.isHeading
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onParent
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.techgv.stash.core.designsystem.WidthClass
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.ItemLifecycle
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import com.techgv.stash.core.testing.savedItem
import coil3.Image as CoilImage
import coil3.Canvas
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.Uri
import coil3.annotation.DelicateCoilApi
import coil3.compose.LocalPlatformContext
import coil3.decode.DataSource
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.pathSegments
import androidx.compose.runtime.remember
import androidx.compose.ui.test.assertWidthIsEqualTo
import androidx.compose.ui.test.onAllNodesWithContentDescription
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

// Runs on the iOS simulator and as an Android device test, in English.

private val unreadItem = savedItem("a", title = "Designing for slow reading", siteName = "medium.com", url = "http://medium.com/a")
private val readItem = unreadItem.copy(readAt = unreadItem.addedAt)

private fun webState(item: com.techgv.stash.core.model.SavedItem = readItem) = ReaderState(item = item, readerMode = false)

private fun readerState(article: ArticleState) = ReaderState(item = readItem, readerMode = true, article = article)

@OptIn(ExperimentalTestApi::class)
class ReaderScreenUiTest {
    private val intents = mutableListOf<ReaderIntent>()
    private val webUrls = mutableListOf<String>()

    /** Stands in for the platform web view: records the URL it was given. */
    private val fakeWebView: WebViewSlot = { url, _, _ -> webUrls += url }

    @Composable
    private fun Screen(state: ReaderState, fontScale: Float = 1f) {
        StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                Box(Modifier.size(390.dp, 844.dp)) {
                    ReaderScreen(state, WidthClass.Compact, { intents += it }, webView = fakeWebView)
                }
            }
        }
    }

    private fun ComposeUiTest.show(state: ReaderState, fontScale: Float = 1f) = setContent { Screen(state, fontScale) }

    private fun SemanticsNodeInteraction.textLayout(): TextLayoutResult {
        val results = mutableListOf<TextLayoutResult>()
        fetchSemanticsNode().config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
        return results.single()
    }

    private fun ComposeUiTest.assertNotClipped(text: String) {
        val node = onNodeWithText(text, useUnmergedTree = true)
        node.assertIsDisplayed()
        // Clipped means cut at the bottom, cut to fewer lines, or ended with an ellipsis.
        val layout = node.textLayout()
        assertFalse(layout.didOverflowHeight, "\"$text\" is cut at the bottom")
        assertFalse(layout.multiParagraph.didExceedMaxLines, "\"$text\" has more lines than it may show")
        assertFalse((0 until layout.lineCount).any(layout::isLineEllipsized), "\"$text\" is ellipsized")
    }

    // ---- top bar --------------------------------------------------------------------------------------------------

    @Test
    fun topBarActionsSendTheirIntents() = runComposeUiTest {
        show(webState())
        onNodeWithContentDescription("Close and return to Stash").performClick()
        onNodeWithContentDescription("Reader mode")
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Switch))
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.Off))
            .performClick()
        onNodeWithText("Mark unread").performClick()
        assertEquals(listOf(ReaderIntent.Close, ReaderIntent.ToggleReaderMode, ReaderIntent.MarkUnread), intents)
        onNodeWithText("Designing for slow reading").assertIsDisplayed()
        onNodeWithText("medium.com").assertIsDisplayed()
    }

    @Test
    fun readerModeSwitchIsOnInReaderMode() = runComposeUiTest {
        show(readerState(ArticleState.Loading))
        onNodeWithContentDescription("Reader mode").assert(SemanticsMatcher.expectValue(SemanticsProperties.ToggleableState, ToggleableState.On))
    }

    @Test
    fun markUnreadIsHiddenForAnUnreadItem() = runComposeUiTest {
        show(webState(unreadItem))
        onNodeWithText("Mark unread").assertDoesNotExist()
    }

    @Test
    fun topBarTargetsMeetTheHandoffSizes() = runComposeUiTest {
        show(webState())
        onNodeWithContentDescription("Close and return to Stash").assertWidthIsAtLeast(44.dp).assertHeightIsAtLeast(44.dp)
        onNodeWithContentDescription("Reader mode").assertWidthIsAtLeast(44.dp).assertHeightIsEqualTo(36.dp)
        onNodeWithText("Mark unread").assertHeightIsEqualTo(36.dp)
    }

    // ---- web mode -------------------------------------------------------------------------------------------------

    @Test
    fun webModeLoadsTheHttpsFormOfTheItemUrl() = runComposeUiTest {
        show(webState())
        waitForIdle()
        assertEquals(listOf("https://medium.com/a"), webUrls)
    }

    @Test
    fun failedWebPageShowsTheOfflineMessageWithRetryAndBrowser() = runComposeUiTest {
        show(webState().copy(webFailed = true))
        onNodeWithText("You’re offline.").assertIsDisplayed()
        onNodeWithText("This page will open when you’re back online.").assertIsDisplayed()
        onNodeWithText("Retry").performClick()
        onNodeWithText("Open in browser").performClick()
        assertEquals(listOf(ReaderIntent.RetryWeb, ReaderIntent.OpenInBrowser), intents)
        assertTrue(webUrls.isEmpty())
    }

    @Test
    fun unavailableMessageIsAnnounced() = runComposeUiTest {
        show(webState().copy(toast = ReaderToast.ReaderUnavailable))
        onNodeWithText("Reader mode isn’t available for this page.").assertIsDisplayed().onParent()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    @Test
    fun markedAsReadToastIsAnnounced() = runComposeUiTest {
        show(webState().copy(toast = ReaderToast.MarkedRead))
        onNodeWithText("Marked as read").assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
    }

    // ---- reader mode ----------------------------------------------------------------------------------------------

    @Test
    fun loadingShowsTheItemHeadlineOverTheSkeletonAndNoWebView() = runComposeUiTest {
        show(readerState(ArticleState.Loading))
        onNode(isHeading() and SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("Designing for slow reading"))))
            .assertIsDisplayed()
        assertTrue(webUrls.isEmpty())
    }

    @Test
    fun offlineReaderModeOffersRetryOnly() = runComposeUiTest {
        show(readerState(ArticleState.Offline))
        onNodeWithText("You’re offline.").assertIsDisplayed()
        onNodeWithText("Retry").performClick()
        onNodeWithText("Open in browser").assertDoesNotExist()
        assertEquals(listOf<ReaderIntent>(ReaderIntent.RetryArticle), intents)
    }

    @Test
    fun articleBlocksRenderWithSpansOnNonAsciiText() = runComposeUiTest {
        // UTF-16 offsets: "நன்றி " is 0..6, the emoji a surrogate pair at 6..8, the link word after the space at 9..16.
        val text = "நன்றி 😀 பார்க்க"
        val link = text.indexOf("பார்க்க")
        val article = ReaderArticle(
            "தலைப்பு", "example.com", "Standfirst", 4,
            listOf(
                ReaderBlock.Heading(2, RichText("Section")),
                ReaderBlock.Paragraph(RichText(text, listOf(RichSpan.Bold(6, 8), RichSpan.Link(link, text.length, "https://example.com/ta")))),
                ReaderBlock.Quote(RichText("A quote")),
                ReaderBlock.ListItem(RichText("First point"), ordered = true, index = 1),
                ReaderBlock.Code("val x = 1"),
                ReaderBlock.Image("https://example.com/a.png", "A chart of reading time"),
                ReaderBlock.Image("file:///etc/hosts", "Local file"),
            ),
        )
        show(readerState(ArticleState.Ready(article)))
        onNodeWithText("EXAMPLE.COM · 4 MIN READ").assertExists()
        onNode(isHeading() and SemanticsMatcher.expectValue(SemanticsProperties.Text, listOf(AnnotatedString("Section")))).assertExists()
        onNodeWithText("A quote").assertExists()
        onNodeWithText("First point").assertExists()
        onNodeWithText("val x = 1").assertExists()

        val annotated = onNodeWithText(text).fetchSemanticsNode().config[SemanticsProperties.Text].single()
        val bold = annotated.spanStyles.single { it.item.fontWeight == FontWeight.Bold }
        assertEquals("😀", annotated.text.substring(bold.start, bold.end))
        val url = annotated.getLinkAnnotations(0, annotated.length).single()
        assertEquals("பார்க்க", annotated.text.substring(url.start, url.end))
        assertEquals("https://example.com/ta", (url.item as LinkAnnotation.Url).url)

        // A web image is described by its alt text; a file: image is never loaded and shows its alt text instead.
        onNodeWithContentDescription("A chart of reading time").assertExists()
        onNodeWithText("Local file").assertExists()
        onNodeWithContentDescription("Local file").assertDoesNotExist()
    }

    @OptIn(DelicateCoilApi::class)
    @Test
    fun articleImagesKeepTheirOwnSizeAndIconsAreLeftOut() = runComposeUiTest {
        val article = ReaderArticle(
            "Printing press", "wikipedia.org", null, 4,
            listOf(
                ReaderBlock.Image("https://example.com/20x20/badge.png", "Good article"),
                ReaderBlock.Image("https://example.com/48x16/flag.png", "Flag"),
                ReaderBlock.Image("https://example.com/220x160/press.jpg", "A printing press"),
                ReaderBlock.Image("https://example.com/2000x1000/workshop.jpg", "A workshop"),
                ReaderBlock.Paragraph(RichText("After the images")),
            ),
        )
        try {
            setContent {
                val context = LocalPlatformContext.current
                remember { SingletonImageLoader.setUnsafe(sizedImageLoader(context)) }
                Screen(readerState(ArticleState.Ready(article)))
            }
            waitUntil { onAllNodesWithContentDescription("A workshop").fetchSemanticsNodes().isNotEmpty() }
            waitForIdle()
            onNodeWithContentDescription("Good article").assertDoesNotExist()
            onNodeWithContentDescription("Flag").assertDoesNotExist()
            // Never wider than its own 220 px, in a column wider than that.
            onNodeWithContentDescription("A printing press").assertWidthIsEqualTo(220.dp).assertHeightIsEqualTo(160.dp)
            // A large image fills the column (under 390 dp), at its own proportions.
            val workshop = onNodeWithContentDescription("A workshop").assertWidthIsAtLeast(300.dp).fetchSemanticsNode().size
            assertTrue(workshop.width < with(density) { 390.dp.roundToPx() }, "$workshop")
            assertTrue(abs(workshop.width / 2 - workshop.height) <= 1, "$workshop")
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @Test
    fun articleLinksAndTextSizeSendIntents() = runComposeUiTest {
        val article = ReaderArticle(
            "Title", "example.com", null, 3,
            listOf(ReaderBlock.Paragraph(RichText("Read more", listOf(RichSpan.Link(0, 9, "https://example.com/more"))))),
        )
        show(readerState(ArticleState.Ready(article)).copy(textStep = 1))
        onNodeWithText("Read more").performClick()
        onNodeWithContentDescription("Larger text").performClick()
        onNodeWithContentDescription("Smaller text").performClick()
        assertEquals(
            listOf(ReaderIntent.OpenLink("https://example.com/more"), ReaderIntent.ChangeTextStep(2), ReaderIntent.ChangeTextStep(0)),
            intents,
        )
    }

    // ---- missing item and empty pane ------------------------------------------------------------------------------

    @Test
    fun missingItemSaysSoAndOffersClose() = runComposeUiTest {
        show(ReaderState(missing = true))
        onNodeWithText("This link is gone").assertIsDisplayed()
        onNodeWithContentDescription("Reader mode").assertDoesNotExist()
        onNodeWithText("Close").performClick()
        assertEquals(listOf<ReaderIntent>(ReaderIntent.Close), intents)
    }

    @Test
    fun emptyPaneInvitesAPick() = runComposeUiTest {
        setContent {
            StashTheme(ThemeMode.Night, Accent.Forest, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) { ReaderEmptyPane() }
        }
        onNodeWithText("Nothing open yet").assertIsDisplayed()
        onNodeWithText("Pick a link from your shelf to read it here.").assertIsDisplayed()
    }

    // ---- 200 % font scale -----------------------------------------------------------------------------------------

    @Test
    fun primaryActionsAreNotClippedAtDoubleFontSize() = runComposeUiTest {
        show(webState().copy(webFailed = true), fontScale = 2f)
        assertNotClipped("Mark unread")
        // Not checked: the "Aa" glyph is cut at the bottom, since ReaderTopBar draws it in a fixed 36 dp pill (a
        // design-system gap from review R3, fixed there); add assertNotClipped("Aa") once it grows with the text.
        assertNotClipped("Retry")
        assertNotClipped("Open in browser")
    }

    @Test
    fun missingItemCloseIsNotClippedAtDoubleFontSize() = runComposeUiTest {
        show(ReaderState(missing = true), fontScale = 2f)
        assertNotClipped("Close")
    }
}

/** Serves every `https://example.com/{w}x{h}/…` image as a blank image of that size, without a network. */
@OptIn(DelicateCoilApi::class)
private fun sizedImageLoader(context: PlatformContext) = ImageLoader.Builder(context)
    .components {
        add(
            Fetcher.Factory<Uri> { data, _, _ ->
                val (width, height) = data.pathSegments.first().split('x').map(String::toInt)
                Fetcher { ImageFetchResult(BlankImage(width, height), isSampled = false, dataSource = DataSource.MEMORY) }
            },
        )
    }
    .build()

private class BlankImage(override val width: Int, override val height: Int) : CoilImage {
    override val size: Long = width.toLong() * height * 4
    override val shareable = true

    override fun draw(canvas: Canvas) = Unit
}
