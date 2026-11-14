package com.techgv.stash.core.designsystem

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.toPixelMap
import androidx.compose.ui.test.ComposeUiTest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.runComposeUiTest
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.ColorImage
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.annotation.DelicateCoilApi
import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult
import coil3.request.SuccessResult
import com.techgv.stash.core.designsystem.component.ItemActionSheet
import com.techgv.stash.core.designsystem.component.SavedItemCard
import com.techgv.stash.core.designsystem.component.SearchResultCard
import com.techgv.stash.core.designsystem.component.THUMBNAIL_IMAGE_TAG
import com.techgv.stash.core.designsystem.component.itemMenuActions
import com.techgv.stash.core.designsystem.presentation.CardLifecycle
import com.techgv.stash.core.designsystem.presentation.MetadataState
import com.techgv.stash.core.designsystem.presentation.NOW
import com.techgv.stash.core.designsystem.presentation.SavedItemCardUi
import com.techgv.stash.core.designsystem.presentation.savedItem
import com.techgv.stash.core.designsystem.presentation.toCardUi
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashTheme
import com.techgv.stash.core.domain.SwipeOutcome
import com.techgv.stash.core.model.Accent
import com.techgv.stash.core.model.CardLayout
import com.techgv.stash.core.model.TextSize
import com.techgv.stash.core.model.ThemeMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.datetime.TimeZone
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

// Runs on the iOS simulator and as an Android device test. Images come from a fake ImageLoader, never the network.

private const val OK_IMAGE = "https://img.test/ok.jpg"
private const val BROKEN_IMAGE = "https://img.test/broken.jpg"
private const val CARD_WIDTH = 360
private const val LOAD_TIMEOUT_MILLIS = 5_000L
private val ImageBlue = Color(0xFF0000FF)

/** "M" for the fixture's medium.com. */
private const val MONOGRAM = "M"

private fun card(imageUrl: String?): SavedItemCardUi = savedItem(imageUrl = imageUrl).toCardUi(NOW, TimeZone.UTC)

@OptIn(ExperimentalTestApi::class, DelicateCoilApi::class)
class ThumbnailUiTest {

    /** URLs the fake loader was asked for. */
    private val requested = MutableStateFlow(emptyList<Any>())

    /** Answers [OK_IMAGE] with a solid blue image and fails everything else. */
    private fun fakeImageLoader(context: PlatformContext) = ImageLoader.Builder(context).components {
        add(
            object : Interceptor {
                override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
                    requested.update { it + chain.request.data }
                    return if (chain.request.data == OK_IMAGE) {
                        SuccessResult(ColorImage(color = ImageBlue.toArgb(), width = 100, height = 100), chain.request)
                    } else {
                        ErrorResult(null, chain.request, IllegalStateException("no network in tests"))
                    }
                }
            },
        )
    }.build()

    private fun thumbnailTest(content: @Composable () -> Unit, block: ComposeUiTest.(surface: Color) -> Unit) = runComposeUiTest {
        SingletonImageLoader.setUnsafe { fakeImageLoader(it) }
        try {
            var surface = Color.Unspecified
            setContent {
                StashTheme(ThemeMode.Parchment, Accent.Leather, TextSize.Medium, reduceMotion = true, systemReduceMotion = false) {
                    surface = Stash.colors.surface
                    Box(Modifier.width(CARD_WIDTH.dp)) { content() }
                }
            }
            block(surface)
        } finally {
            SingletonImageLoader.reset()
        }
    }

    @Composable
    private fun Card(item: SavedItemCardUi, layout: CardLayout) =
        SavedItemCard(item, layout, SwipeOutcome.Remove, SwipeOutcome.MarkRead, onOpen = {}, onAction = {}, onMore = {})

    private fun ComposeUiTest.awaitImageLoaded() =
        waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { onAllNodesWithText(MONOGRAM, useUnmergedTree = true).fetchSemanticsNodes().isEmpty() }

    private fun ComposeUiTest.awaitRequested(url: String) {
        waitUntil(timeoutMillis = LOAD_TIMEOUT_MILLIS) { url in requested.value }
        waitForIdle()
    }

    private fun ComposeUiTest.assertImageSize(width: Dp, height: Dp) {
        val bounds = onNodeWithTag(THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).getUnclippedBoundsInRoot()
        assertEquals(width.value, (bounds.right - bounds.left).value, 0.5f, "width")
        assertEquals(height.value, (bounds.bottom - bounds.top).value, 0.5f, "height")
    }

    /** The colour drawn at the centre of the thumbnail. */
    private fun ComposeUiTest.thumbnailCentre(): Color {
        val pixels = onNodeWithTag(THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).captureToImage().toPixelMap()
        return pixels[pixels.width / 2, pixels.height / 2]
    }

    private fun assertColor(expected: Color, actual: Color) = assertTrue(
        abs(expected.red - actual.red) < 0.03f && abs(expected.green - actual.green) < 0.03f && abs(expected.blue - actual.blue) < 0.03f,
        "expected $expected, got $actual",
    )

    @Test
    fun loadedImageReplacesTheMonogramAtEachLayoutsSize() {
        listOf(
            CardLayout.Comfortable to (60.dp to 60.dp),
            CardLayout.Compact to (40.dp to 40.dp),
            CardLayout.LargeImages to (CARD_WIDTH.dp to 156.dp),
        ).forEach { (layout, size) ->
            thumbnailTest({ Card(card(OK_IMAGE), layout) }) {
                awaitImageLoaded()
                assertImageSize(size.first, size.second)
                assertColor(ImageBlue, thumbnailCentre())
            }
        }
    }

    @Test
    fun monogramStaysWhenTheImageFails() = thumbnailTest({ Card(card(BROKEN_IMAGE), CardLayout.Comfortable) }) {
        awaitRequested(BROKEN_IMAGE)
        onNodeWithText(MONOGRAM, useUnmergedTree = true).assertIsDisplayed()
        assertImageSize(60.dp, 60.dp)
        assertTrue(thumbnailCentre() != ImageBlue)
    }

    @Test
    fun monogramWithoutAnImage() = thumbnailTest({ Card(card(null), CardLayout.Comfortable) }) {
        onNodeWithText(MONOGRAM, useUnmergedTree = true).assertIsDisplayed()
        onNodeWithTag(THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
    }

    @Test
    fun pendingAndFailedDetailsKeepTheMonogram() {
        listOf(MetadataState.Pending, MetadataState.Failed).forEach { metadata ->
            thumbnailTest({ Card(card(OK_IMAGE).copy(metadata = metadata), CardLayout.Comfortable) }) {
                onNodeWithText(MONOGRAM, useUnmergedTree = true).assertIsDisplayed()
                onNodeWithTag(THUMBNAIL_IMAGE_TAG, useUnmergedTree = true).assertDoesNotExist()
            }
        }
    }

    @Test
    fun readCardDrawsTheImageAt55Percent() = thumbnailTest({
        Card(card(OK_IMAGE).copy(lifecycle = CardLifecycle.Read, unread = false), CardLayout.Comfortable)
    }) { surface ->
        awaitImageLoaded()
        // Blended in sRGB, as the layer is composited (Color's lerp works in Oklab).
        fun mix(under: Float, over: Float) = under * 0.45f + over * 0.55f
        assertColor(Color(mix(surface.red, ImageBlue.red), mix(surface.green, ImageBlue.green), mix(surface.blue, ImageBlue.blue)), thumbnailCentre())
    }

    @Test
    fun itemSheetHeaderShowsTheImage() = thumbnailTest({
        val item = card(OK_IMAGE)
        ItemActionSheet(item, itemMenuActions(item), onSelect = {}, onDismiss = {})
    }) {
        awaitImageLoaded()
        assertImageSize(44.dp, 44.dp)
        assertColor(ImageBlue, thumbnailCentre())
    }

    @Test
    fun searchResultShowsTheImage() = thumbnailTest({
        SearchResultCard(card(OK_IMAGE), onOpen = {}, onAction = {}, onMore = {})
    }) {
        awaitImageLoaded()
        assertImageSize(52.dp, 52.dp)
        assertColor(ImageBlue, thumbnailCentre())
    }
}
