package io.github.leonfox28.maxbo.ui.widget.text

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.data.model.UrlEntity

/**
 * The body of a status: text with tappable links, topics, mentions and inline emoticons.
 *
 * @param text the status `text` HTML.
 * @param hiddenPictures ids of pictures whose links to leave out, e.g. as they show below.
 * @param author shown as `@author：` before the text, for retweeted posts.
 * @param onExpand called when "展开" at the end of a truncated long post is tapped.
 */
@Composable
fun StatusText(
    text: String,
    modifier: Modifier = Modifier,
    urls: List<UrlEntity> = emptyList(),
    hiddenPictures: List<String> = emptyList(),
    author: String? = null,
    style: TextStyle = LocalTextStyle.current,
    onLink: ((StatusLink) -> Unit)? = null,
    onExpand: (() -> Unit)? = null,
) {
    // Taps read the latest callbacks, so they never go stale.
    val link by rememberUpdatedState(onLink)
    val expand by rememberUpdatedState(onExpand)
    val segments =
        remember(text, urls, hiddenPictures, author) {
            buildList {
                if (author != null) {
                    add(TextSegment.Link("@$author", StatusLink.Mention(author)))
                    add(TextSegment.Plain("："))
                }
                addAll(parseStatusText(text, urls, hiddenPictures))
            }
        }
    val linkStyles = TextLinkStyles(SpanStyle(color = MaterialTheme.colorScheme.primary))
    val annotated = buildAnnotatedString {
        segments.forEachIndexed { i, segment ->
            when (segment) {
                is TextSegment.Plain -> append(segment.text)
                is TextSegment.Emoticon -> appendInlineContent("$i", segment.alt)
                is TextSegment.Link ->
                    withLink(
                        LinkAnnotation.Clickable("$i", linkStyles) { link?.invoke(segment.link) }
                    ) {
                        if (segment.iconUrl != null) appendInlineContent("$i", " ")
                        append(segment.text)
                    }
                TextSegment.Expand ->
                    withLink(LinkAnnotation.Clickable("expand", linkStyles) { expand?.invoke() }) {
                        append("展开")
                    }
            }
        }
    }
    val fontSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    val iconSize = (fontSize.value * 1.2f).sp
    val inline =
        segments
            .withIndex()
            .mapNotNull { (i, segment) ->
                when (segment) {
                    is TextSegment.Emoticon ->
                        "$i" to inlineImage(segment.url, iconSize, segment.alt)
                    is TextSegment.Link ->
                        segment.iconUrl?.let {
                            "$i" to inlineImage(it, iconSize, null) { link?.invoke(segment.link) }
                        }
                    else -> null
                }
            }
            .toMap()
    // Links take taps on their glyphs only, as in Flutter: stretched to the
    // usual 48dp, a mention would take taps on the line below it, or on
    // "共N条回复" under a reply.
    val viewConfiguration = LocalViewConfiguration.current
    val exactTargets =
        remember(viewConfiguration) {
            object : ViewConfiguration by viewConfiguration {
                override val minimumTouchTargetSize = DpSize.Zero
            }
        }
    CompositionLocalProvider(LocalViewConfiguration provides exactTargets) {
        Text(annotated, modifier, style = style, inlineContent = inline)
    }
}

private fun inlineImage(
    url: String,
    size: TextUnit,
    label: String?,
    onClick: (() -> Unit)? = null,
) =
    InlineTextContent(Placeholder(size, size, PlaceholderVerticalAlign.Center)) {
        WeiboImage(
            url,
            Modifier.fillMaxSize()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
            contentScale = ContentScale.Fit,
            placeholder = false,
            contentDescription = label,
        )
    }
