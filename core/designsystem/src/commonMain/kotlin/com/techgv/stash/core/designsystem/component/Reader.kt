package com.techgv.stash.core.designsystem.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.runtime.remember
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.LinkInteractionListener
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.techgv.stash.core.designsystem.theme.READER_TEXT_STEPS
import com.techgv.stash.core.designsystem.theme.Stash
import com.techgv.stash.core.designsystem.theme.StashMotion
import com.techgv.stash.core.designsystem.theme.motionSpec
import com.techgv.stash.core.model.ReaderArticle
import com.techgv.stash.core.model.ReaderBlock
import com.techgv.stash.core.model.RichSpan
import com.techgv.stash.core.model.RichText
import com.techgv.stash.core.resources.Res
import com.techgv.stash.core.resources.card_meta_two
import com.techgv.stash.core.resources.card_minutes_read
import com.techgv.stash.core.resources.reader_close
import com.techgv.stash.core.resources.reader_larger
import com.techgv.stash.core.resources.reader_larger_label
import com.techgv.stash.core.resources.reader_mark_unread
import com.techgv.stash.core.resources.reader_mode
import com.techgv.stash.core.resources.reader_mode_glyph
import com.techgv.stash.core.resources.reader_ornament
import com.techgv.stash.core.resources.reader_smaller
import com.techgv.stash.core.resources.reader_smaller_label
import org.jetbrains.compose.resources.stringResource

/** Reader text column max width on medium and expanded windows (handoff). */
val MaxReaderWidth = 680.dp

/**
 * Reader top bar on the primary colour: close (44 dp), title and site on one line each, the "Aa" reader-mode toggle
 * (a switch, inverted when on) and the "Mark unread" pill (hidden with [showMarkUnread] false), then a 3 dp brass load
 * bar at [loadProgress] (0..1, read while drawing). Every control has a 48 dp touch target; the pills grow with the
 * font scale. [closeFocusRequester] is attached to Close, so the screen can move focus there when the reader opens.
 */
@Composable
fun ReaderTopBar(
    title: String,
    site: String,
    readerMode: Boolean,
    loadProgress: () -> Float,
    onClose: () -> Unit,
    onToggleReaderMode: () -> Unit,
    onMarkUnread: () -> Unit,
    modifier: Modifier = Modifier,
    showMarkUnread: Boolean = true,
    closeFocusRequester: FocusRequester? = null,
) {
    val c = Stash.colors
    val pillBorder = c.onPrimary.copy(alpha = 0.5f)
    Column(modifier.fillMaxWidth()) {
        Box(Modifier.fillMaxWidth().background(c.primary).statusBarsPad()) {
            Row(
                Modifier.contentWidth().padding(start = 6.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                BackButton(
                    stringResource(Res.string.reader_close), onClose,
                    Modifier.minimumInteractiveComponentSize().then(closeFocusRequester?.let { Modifier.focusRequester(it) } ?: Modifier),
                    tint = c.onPrimary, icon = StashIcons.close(1.8f),
                )
                Column(Modifier.weight(1f)) {
                    Txt(title, Stash.type.calloutStrong, c.onPrimary, maxLines = 1)
                    Txt(site, Stash.type.meta, c.onPrimaryMuted, maxLines = 1)
                }
                val bg by animateColorAsState(if (readerMode) c.onPrimary else Color.Transparent, motionSpec(tween(StashMotion.SELECT)))
                val modeLabel = stringResource(Res.string.reader_mode)
                Box(
                    Modifier.minimumInteractiveComponentSize().heightIn(min = 36.dp).widthIn(min = 44.dp).focusRing(CircleShape).clip(CircleShape).background(bg)
                        .border(1.dp, pillBorder, CircleShape)
                        .toggleable(readerMode, role = Role.Switch) { onToggleReaderMode() }
                        .semantics { contentDescription = modeLabel }.padding(horizontal = 10.dp, vertical = Stash.spacing.xxs),
                    contentAlignment = Alignment.Center,
                ) { Txt(stringResource(Res.string.reader_mode_glyph), Stash.type.glyph, if (readerMode) c.primary else c.onPrimary) }
                if (showMarkUnread) {
                    Box(
                        Modifier.padding(end = 2.dp).minimumInteractiveComponentSize().heightIn(min = 36.dp).focusRing(CircleShape).clip(CircleShape)
                            .border(1.dp, pillBorder, CircleShape).clickable(role = Role.Button, onClick = onMarkUnread)
                            .padding(horizontal = Stash.spacing.sm, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) { Txt(stringResource(Res.string.reader_mark_unread), Stash.type.labelSmall, c.onPrimary) }
                }
            }
        }
        Box(
            Modifier.fillMaxWidth().height(3.dp).background(c.variant).drawBehind {
                drawRect(c.brass, size = size.copy(width = size.width * loadProgress().coerceIn(0f, 1f)))
            },
        )
    }
}

/**
 * Reader mode on parchment: kicker (site · minutes, uppercase), headline, italic standfirst, ❦ rule, then the article
 * blocks at [textStep] (0..3), with the floating A− / A+ group at the bottom end. With no blocks yet it draws skeleton
 * lines (the reader skeleton). [length] replaces the kicker's "N min read" (e.g. "18 min video"); [footnote] is an
 * optional line under the body. Links in the text call [onLinkClick] with their URL. [image] draws image blocks (the
 * reader feature loads them); without it an image shows its alt text. The text can be selected and copied.
 */
@Composable
fun ReaderModeView(
    article: ReaderArticle,
    textStep: Int,
    onTextStepChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
    length: String? = null,
    footnote: String? = null,
    onLinkClick: (String) -> Unit = {},
    image: (@Composable (ReaderBlock.Image) -> Unit)? = null,
) {
    val c = Stash.colors
    val type = Stash.type
    val step = textStep.coerceIn(0, READER_TEXT_STEPS - 1)
    val head by animateIntAsState(type.readerHead[step].fontSize.value.toInt(), motionSpec(tween(StashMotion.SELECT)))
    val body by animateIntAsState(type.readerBody[step].fontSize.value.toInt(), motionSpec(tween(StashMotion.SELECT)))
    val headStyle = type.readerHead[step].let { it.copy(fontSize = head.sp, lineHeight = (it.lineHeight.value * head / it.fontSize.value).sp) }
    val bodyStyle = type.readerBody[step].let { it.copy(fontSize = body.sp, lineHeight = (it.lineHeight.value * body / it.fontSize.value).sp) }
    val gap = (16 + 2 * step).dp
    Box(modifier.fillMaxSize().background(c.bg)) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            SelectionContainer {
                Column(
                    Modifier.fillMaxWidth().wrapContentWidth().widthIn(max = MaxReaderWidth).fillMaxWidth()
                        .padding(start = 28.dp, end = 28.dp, top = 26.dp, bottom = 90.dp).navBarsPad(),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    val minutes = length ?: article.readingMinutes?.let { stringResource(Res.string.card_minutes_read, it) }
                    val kicker = minutes?.let { stringResource(Res.string.card_meta_two, article.siteName, it) } ?: article.siteName
                    Txt(kicker.uppercase(), Stash.type.kicker, c.primary)
                    Txt(article.title, headStyle, c.ink, Modifier.semantics { heading() })
                    article.standfirst?.let { Txt(it, bodyStyle.copy(fontStyle = FontStyle.Italic), c.inkFaded) }
                    Row(Modifier.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(Modifier.weight(1f).height(1.dp).background(c.outline))
                        Txt(stringResource(Res.string.reader_ornament), Stash.type.body, c.brass, Modifier.clearAndSetSemantics {})
                        Box(Modifier.weight(1f).height(1.dp).background(c.outline))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(gap)) {
                        if (article.blocks.isEmpty()) {
                            SkeletonLines(c.variant, listOf(1f, .96f, .92f, .58f))
                            SkeletonLines(c.variant, listOf(1f, .9f, .97f, .4f))
                        }
                        val linkHandler = remember(onLinkClick) { LinkInteractionListener { (it as? LinkAnnotation.Url)?.let { url -> onLinkClick(url.url) } } }
                        article.blocks.forEach { ReaderBlockView(it, bodyStyle, linkHandler, image) }
                    }
                    footnote?.let { Txt(it, Stash.type.meta, c.inkFaded) }
                }
            }
        }
        Row(
            Modifier.align(Alignment.BottomEnd).navBarsPad().padding(end = Stash.spacing.md, bottom = 28.dp)
                .graphicsLayer { shadowElevation = 16f; shape = CircleShape; clip = true }
                .background(c.surface).border(1.dp, c.outline, CircleShape).padding(Stash.spacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SizeButton(stringResource(Res.string.reader_smaller), 14.sp, stringResource(Res.string.reader_smaller_label)) {
                onTextStepChange((step - 1).coerceAtLeast(0))
            }
            SizeButton(stringResource(Res.string.reader_larger), 19.sp, stringResource(Res.string.reader_larger_label)) {
                onTextStepChange((step + 1).coerceAtMost(READER_TEXT_STEPS - 1))
            }
        }
    }
}

@Composable
private fun ReaderBlockView(
    block: ReaderBlock,
    bodyStyle: TextStyle,
    links: LinkInteractionListener,
    image: (@Composable (ReaderBlock.Image) -> Unit)?,
) {
    val c = Stash.colors
    when (block) {
        is ReaderBlock.Heading -> Text(
            block.text.annotated(c.primary, links), Modifier.semantics { heading() }, color = c.ink,
            style = bodyStyle.copy(fontWeight = FontWeight.Bold, fontSize = bodyStyle.fontSize * (if (block.level <= 2) 1.25f else 1.1f)),
        )
        is ReaderBlock.Paragraph -> Text(block.text.annotated(c.primary, links), color = c.ink, style = bodyStyle)
        is ReaderBlock.Quote -> Text(
            block.text.annotated(c.primary, links),
            Modifier.drawBehind { drawLine(c.brass, Offset(0f, 0f), Offset(0f, size.height), 3.dp.toPx()) }.padding(start = 14.dp),
            color = c.inkFaded, style = bodyStyle.copy(fontStyle = FontStyle.Italic),
        )
        is ReaderBlock.ListItem -> Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Txt(if (block.ordered) "${block.index}." else "•", bodyStyle, c.inkFaded)
            Text(block.text.annotated(c.primary, links), color = c.ink, style = bodyStyle)
        }
        is ReaderBlock.Code -> Txt(
            block.text, bodyStyle.copy(fontFamily = FontFamily.Monospace, fontSize = bodyStyle.fontSize * 0.85f), c.ink,
            Modifier.fillMaxWidth().clip(RoundedCornerShape(Stash.radius.thumbnailCompact)).background(c.variant).padding(Stash.spacing.sm),
        )
        is ReaderBlock.Image -> if (image != null) image(block) else block.alt?.let { Txt(it, Stash.type.meta, c.inkFaded) }
    }
}

/** Bold, italic and clickable underlined link spans. */
private fun RichText.annotated(link: Color, links: LinkInteractionListener): AnnotatedString = buildAnnotatedString {
    append(text)
    spans.forEach { s ->
        val start = s.start.coerceIn(0, text.length)
        val end = s.end.coerceIn(start, text.length)
        when (s) {
            is RichSpan.Bold -> addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, end)
            is RichSpan.Italic -> addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, end)
            is RichSpan.Link -> addLink(
                LinkAnnotation.Url(s.url, TextLinkStyles(SpanStyle(color = link, textDecoration = TextDecoration.Underline)), links),
                start, end,
            )
        }
    }
}

/** Placeholder text lines while the text is not there yet. */
@Composable
fun SkeletonLines(color: Color, widths: List<Float>, height: Int = 9, gap: Int = 9) {
    Column(verticalArrangement = Arrangement.spacedBy(gap.dp)) {
        widths.forEach { Box(Modifier.fillMaxWidth(it).height(height.dp).clip(RoundedCornerShape((height / 2).dp)).background(color)) }
    }
}

@Composable
private fun SizeButton(text: String, size: TextUnit, label: String, onClick: () -> Unit) {
    Box(
        Modifier.sizeIn(minWidth = 44.dp, minHeight = 40.dp).focusRing(CircleShape).clip(CircleShape).clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) { Txt(text, Stash.type.readerSizeButton.copy(fontSize = size), Stash.colors.ink) }
}
