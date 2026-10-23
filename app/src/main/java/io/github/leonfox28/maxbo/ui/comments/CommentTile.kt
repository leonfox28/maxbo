package io.github.leonfox28.maxbo.ui.comments

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.core.util.formatPostTime
import io.github.leonfox28.maxbo.data.model.Comment
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.ui.interactions.LikeKind
import io.github.leonfox28.maxbo.ui.interactions.LikeState
import io.github.leonfox28.maxbo.ui.interactions.LikeTarget
import io.github.leonfox28.maxbo.ui.interactions.rememberLike
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.status.StatusCardActions
import io.github.leonfox28.maxbo.ui.widget.text.StatusText

/**
 * A comment with its author, pictures, time and like button; with [onThread], also a preview of its
 * replies that opens the whole thread.
 *
 * @param authorId the status author, whose comments are labelled 博主.
 * @param onClick tapping the comment, e.g. to reply to it.
 * @param below shown last, e.g. the post a notification's comment is on.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CommentTile(
    comment: Comment,
    modifier: Modifier = Modifier,
    authorId: String? = null,
    actions: StatusCardActions = StatusCardActions(),
    onThread: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    below: (@Composable () -> Unit)? = null,
) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val user = comment.user
    val meta =
        listOfNotNull(comment.createdAt?.let { formatPostTime(it) }, comment.source)
            .joinToString("  ")
    val onPicture = actions.onPicture
    Row(
        modifier
            .fillMaxWidth()
            .then(
                if (onClick != null || onLongClick != null) {
                    Modifier.combinedClickable(onClick = onClick ?: {}, onLongClick = onLongClick)
                } else {
                    Modifier
                }
            )
            .padding(16.dp, 12.dp)
    ) {
        UserAvatar(user, size = 32.dp, onClick = actions.onUser)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user?.screenName ?: "微博用户",
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyMedium,
                    color = muted,
                    fontWeight = FontWeight.Medium,
                )
                if (user != null && user.id == authorId) {
                    Spacer(Modifier.width(4.dp))
                    AuthorLabel()
                }
            }
            if (comment.text.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                StatusText(
                    comment.text,
                    urls = comment.urls,
                    // A reply links its picture too; it shows below.
                    hiddenPictures = comment.pics.map { it.id },
                    style =
                        MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = (15 * 1.45).sp,
                        ),
                    onLink = actions.onLink,
                )
            }
            if (comment.pics.isNotEmpty()) {
                Spacer(Modifier.height(6.dp))
                Pictures(comment.pics, onPicture?.let { { i: Int -> it(comment.pics, i) } })
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    meta,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = muted,
                )
                LikeButton(comment)
            }
            if (onThread != null && comment.replyCount > 0) {
                Spacer(Modifier.height(8.dp))
                ThreadPreview(comment, actions, onThread)
            }
            if (below != null) {
                Spacer(Modifier.height(8.dp))
                below()
            }
        }
    }
}

/**
 * The comment's like button. No ink, as on a card's counts; while a like is on its way it takes
 * taps all the same, so they don't reply to the comment.
 */
@Composable
private fun LikeButton(comment: Comment) {
    val shown =
        rememberLike(
            LikeTarget(LikeKind.Comment, comment.id),
            LikeState(comment.liked, comment.likesCount),
        )
    val like = shown.like
    val color =
        if (like.liked) MaterialTheme.colorScheme.primary
        else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.clickable(
                remember { MutableInteractionSource() },
                indication = null,
                onClick = shown.onToggle ?: {},
            )
            .padding(horizontal = 6.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (like.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            contentDescription = null,
            modifier = Modifier.size(14.dp),
            tint = color,
        )
        Spacer(Modifier.width(3.dp))
        Text(
            if (like.count > 0) formatCount(like.count) else "赞",
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun AuthorLabel() {
    val colors = MaterialTheme.colorScheme
    Text(
        "博主",
        modifier =
            Modifier.background(colors.primaryContainer, RoundedCornerShape(3.dp))
                .padding(horizontal = 4.dp, vertical = 1.dp),
        fontSize = 10.sp,
        lineHeight = 14.sp,
        color = colors.onPrimaryContainer,
    )
}

private val PictureSize = 96.dp

@Composable
private fun Pictures(pics: List<PicInfo>, onClick: ((Int) -> Unit)?) {
    val settings by LocalAppGraph.current.settings.settings.collectAsStateWithLifecycle()
    val pixels = settings.imageQuality.listPixels(PictureSize.value, LocalDensity.current.density)
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        pics.forEachIndexed { i, pic ->
            WeiboImage(
                pic.variantFor(pixels).url,
                Modifier.size(PictureSize)
                    .testTag("media")
                    .clip(RoundedCornerShape(4.dp))
                    .then(if (onClick != null) Modifier.clickable { onClick(i) } else Modifier),
            )
        }
    }
}

/** The first replies of a thread and its size; tapping opens the thread. */
@Composable
private fun ThreadPreview(comment: Comment, actions: StatusCardActions, onClick: () -> Unit) {
    val style = MaterialTheme.typography.bodyMedium.let { it.copy(lineHeight = it.fontSize * 1.4f) }
    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.padding(8.dp)) {
            for (reply in comment.replies) {
                StatusText(
                    reply.text,
                    Modifier.padding(bottom = 4.dp),
                    urls = reply.urls,
                    author = reply.user?.screenName,
                    style = style,
                    onLink = actions.onLink,
                )
            }
            Text(
                "共${comment.replyCount}条回复 >",
                style = style,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
