package io.github.leonfox28.maxbo.ui.widget.status

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.core.util.formatDuration
import io.github.leonfox28.maxbo.core.util.formatPostTime
import io.github.leonfox28.maxbo.data.model.MixedMedia
import io.github.leonfox28.maxbo.data.model.PageCard
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.StatusVisibility
import io.github.leonfox28.maxbo.data.model.VideoInfo
import io.github.leonfox28.maxbo.data.model.WeiboUser
import io.github.leonfox28.maxbo.ui.widget.MediaBadge
import io.github.leonfox28.maxbo.ui.widget.PicGrid
import io.github.leonfox28.maxbo.ui.widget.PlayIcon
import io.github.leonfox28.maxbo.ui.widget.TagLabel
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.VerifiedIcon
import io.github.leonfox28.maxbo.ui.widget.showsVerified
import io.github.leonfox28.maxbo.ui.widget.text.StatusLink
import io.github.leonfox28.maxbo.ui.widget.text.StatusText

/**
 * What tapping parts of a status (or comment) leads to. A null callback leaves its target inert.
 */
data class StatusCardActions(
    /** The card itself, or the retweeted post inside it. */
    val onOpen: ((Status) -> Unit)? = null,
    val onLink: ((StatusLink) -> Unit)? = null,
    val onUser: ((WeiboUser) -> Unit)? = null,
    /** "展开" on a truncated long post. */
    val onExpand: ((Status) -> Unit)? = null,
    val onPicture: ((pics: List<PicInfo>, index: Int) -> Unit)? = null,
    /**
     * A video; the index picks one in a post mixing pictures and videos, as `Status.videoAt` does.
     */
    val onVideo: ((status: Status, index: Int?) -> Unit)? = null,
    /** The repost count, which reposts the card's post. */
    val onRepost: ((Status) -> Unit)? = null,
    /** Deleting a post of the signed-in user's, offered on their posts only. */
    val onDelete: ((Status) -> Unit)? = null,
)

/**
 * A status as shown in a timeline, or atop its detail page when [detail], where the card itself
 * isn't tappable and the counts move to the page's tabs.
 */
@Composable
fun StatusCard(
    status: Status,
    modifier: Modifier = Modifier,
    actions: StatusCardActions = StatusCardActions(),
    detail: Boolean = false,
) {
    val onOpen = actions.onOpen
    val activity = if (detail) null else status.activity
    Column(
        modifier
            .then(
                if (!detail && onOpen != null) Modifier.clickable { onOpen(status) } else Modifier
            )
            .padding(start = 16.dp, top = 12.dp, end = 16.dp, bottom = if (detail) 12.dp else 0.dp)
    ) {
        if (activity != null) {
            Activity(activity)
            Spacer(Modifier.height(8.dp))
        }
        Header(status, actions)
        Spacer(Modifier.height(8.dp))
        Body(status, actions)
        status.retweeted?.let {
            Spacer(Modifier.height(8.dp))
            Retweeted(it, actions)
        }
        if (!detail) Counts(status, actions)
    }
}

/**
 * `Status.activity`, above the card, so a post by someone else on a user's profile doesn't pass for
 * theirs.
 */
@Composable
private fun Activity(label: String) {
    val color = MaterialTheme.colorScheme.onSurfaceVariant
    // The icons of the counts under a card.
    val icon =
        when {
            label.contains("赞") -> Icons.Outlined.ThumbUp
            label.contains("评论") -> Icons.Outlined.ChatBubbleOutline
            else -> null
        }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = color)
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodySmall,
            color = color,
        )
    }
}

@Composable
private fun Retweeted(status: Status, actions: StatusCardActions) {
    val onOpen = actions.onOpen
    // Deleted or hidden posts come without an author or time and can't be
    // opened; those in `statuses/mentions` may lack only the author.
    val canOpen = onOpen != null && (status.user != null || status.createdAt != null)
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Box(
            Modifier.testTag("retweet")
                .then(if (canOpen) Modifier.clickable { onOpen(status) } else Modifier)
                .padding(10.dp)
        ) {
            Body(status, actions, nested = true)
        }
    }
}

@Composable
private fun Header(status: Status, actions: StatusCardActions) {
    val user = status.user
    val meta =
        listOfNotNull(
                status.createdAt?.let { formatPostTime(it) },
                status.source?.let { "来自 $it" },
                status.region,
            )
            .joinToString("  ")
    val verified = user?.verifiedType
    val onUser = actions.onUser
    val onDelete = actions.onDelete
    val tags =
        listOfNotNull(
            "置顶".takeIf { status.pinned },
            status.visibility.label.takeIf { status.visibility != StatusVisibility.Public },
            "广告".takeIf { status.isAd },
        )
    Row(verticalAlignment = Alignment.CenterVertically) {
        UserAvatar(user, onClick = onUser)
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user?.screenName ?: "微博用户",
                    modifier =
                        Modifier.weight(1f, fill = false)
                            .then(
                                if (user != null && onUser != null)
                                    Modifier.clickable { onUser(user) }
                                else Modifier
                            ),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.titleSmall,
                )
                if (showsVerified(verified)) {
                    Spacer(Modifier.width(4.dp))
                    VerifiedIcon(verified!!)
                }
            }
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        tags.forEachIndexed { i, tag ->
            if (i > 0) Spacer(Modifier.width(4.dp))
            TagLabel(tag)
        }
        if (onDelete != null && user != null) OwnStatusMenu(user.id) { onDelete(status) }
    }
}

/** A menu for the signed-in user's own posts; nothing on others'. */
@Composable
private fun OwnStatusMenu(authorId: String, onDelete: () -> Unit) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    if ((session as? SessionState.SignedIn)?.account?.uid != authorId) return
    IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Filled.MoreHoriz, contentDescription = "更多")
    }
}

/** The pictures, video or linked page of a status, if it has any. */
private fun hasMedia(status: Status): Boolean {
    val isRetweet = status.retweeted != null
    return status.pics.isNotEmpty() ||
        status.mixedMedia.isNotEmpty() ||
        (!isRetweet && (status.video != null || status.pageCard != null))
}

@Composable
private fun Media(status: Status, actions: StatusCardActions) {
    // A retweet carries a copy of its source's page_info; show it only once,
    // inside the retweeted post.
    val isRetweet = status.retweeted != null
    val video = if (isRetweet) null else status.video
    val card = if (isRetweet) null else status.pageCard
    val onPicture = actions.onPicture
    val onVideo = actions.onVideo
    when {
        status.pics.isNotEmpty() ->
            PicGrid(
                status.pics.map { MixedMedia.Pic(it) },
                total = status.picCount,
                onClick = onPicture?.let { { i: Int -> it(status.pics, i) } },
            )
        status.mixedMedia.isNotEmpty() -> MixedGrid(status, actions)
        video != null -> VideoCover(video, onVideo?.let { { it(status, null) } })
        card != null -> PageCardView(card)
    }
}

// Pictures open in the viewer among the post's other pictures; videos play
// by their place among its videos.
@Composable
private fun MixedGrid(status: Status, actions: StatusCardActions) {
    val items = status.mixedMedia
    val pics = items.filterIsInstance<MixedMedia.Pic>().map { it.pic }
    PicGrid(items, total = status.picCount) { i ->
        val before = items.take(i)
        when (items[i]) {
            is MixedMedia.Pic ->
                actions.onPicture?.invoke(pics, before.count { it is MixedMedia.Pic })
            is MixedMedia.Video ->
                actions.onVideo?.invoke(status, before.count { it is MixedMedia.Video })
        }
    }
}

/** Text and media of a status, or of the post it retweets when [nested]. */
@Composable
private fun Body(status: Status, actions: StatusCardActions, nested: Boolean = false) {
    Column {
        if (status.text.isNotEmpty() || (nested && status.user != null)) {
            StatusText(
                status.text,
                urls = status.urls,
                author = if (nested) status.user?.screenName else null,
                style =
                    MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 15.sp,
                        lineHeight = (15 * 1.45).sp,
                    ),
                onLink = actions.onLink,
                onExpand = actions.onExpand?.let { { it(status) } },
            )
        }
        if (hasMedia(status)) {
            Spacer(Modifier.height(8.dp))
            Media(status, actions)
        }
    }
}

@Composable
private fun VideoCover(video: VideoInfo, onClick: (() -> Unit)?) {
    // Portrait videos are cropped square to keep the timeline compact.
    val ratio = (video.aspectRatio ?: (16f / 9)).coerceIn(1f, 16f / 9)
    Box(
        Modifier.fillMaxWidth()
            .aspectRatio(ratio)
            .clip(RoundedCornerShape(6.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        val cover = video.coverUrl
        if (cover != null) WeiboImage(cover, Modifier.fillMaxSize())
        else
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        PlayIcon(52.dp, Modifier.align(Alignment.Center))
        val duration = video.duration
        if (duration != null && duration > 0) {
            MediaBadge(formatDuration(duration), Modifier.align(Alignment.BottomEnd).padding(6.dp))
        }
    }
}

@Composable
private fun PageCardView(card: PageCard) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(colors.surfaceContainer),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        card.imageUrl?.let { WeiboImage(it, Modifier.size(64.dp)) }
        Column(Modifier.weight(1f).padding(horizontal = 10.dp, vertical = 8.dp)) {
            Text(
                card.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleSmall,
            )
            card.description?.let {
                Text(
                    it,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * Repost, comment and like counts; the repost count is a repost button and the like count a like
 * button (K5).
 */
@Composable
private fun Counts(status: Status, actions: StatusCardActions) {
    val colors = MaterialTheme.colorScheme
    val onRepost = actions.onRepost
    val onOpen = actions.onOpen
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        CountItem(
            Icons.Filled.Repeat,
            status.repostsCount,
            "转发",
            onClick = onRepost?.let { { it(status) } },
        )
        // Opens the status, as the card does, without lighting it all up.
        CountItem(
            Icons.Outlined.ChatBubbleOutline,
            status.commentsCount,
            "评论",
            onClick = onOpen?.let { { it(status) } },
        )
        // Taking no taps until liking comes over (K5), but swallowing them,
        // so they don't open the status.
        CountItem(
            if (status.liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
            status.likesCount,
            "赞",
            color = if (status.liked) colors.primary else null,
            onClick = {},
        )
    }
}

/** No ink on the counts, nor on the card behind them. */
@Composable
private fun RowScope.CountItem(
    icon: ImageVector,
    count: Int,
    label: String,
    color: Color? = null,
    onClick: (() -> Unit)?,
) {
    val tint = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.weight(1f)
            .then(
                if (onClick == null) Modifier
                else
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        onClick = onClick,
                    )
            )
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = tint)
        Spacer(Modifier.width(4.dp))
        Text(if (count > 0) formatCount(count) else label, fontSize = 13.sp, color = tint)
    }
}
