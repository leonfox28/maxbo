package io.github.leonfox28.maxbo.ui.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.util.formatDuration
import io.github.leonfox28.maxbo.data.model.MixedMedia
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.PicType
import io.github.leonfox28.maxbo.data.model.VideoInfo

private val Gap = 4.dp
private const val MAX_SHOWN = 9
private val TileShape = RoundedCornerShape(4.dp)

/**
 * The pictures of a status: one sized to its shape, otherwise a grid of square cells (2×2 for four,
 * three columns else, at most nine). Posts mixing pictures and videos show both in it.
 *
 * @param total how many items the status has; beyond nine shows as "+N".
 * @param onClick called with the index in [items].
 */
@Composable
fun PicGrid(
    items: List<MixedMedia>,
    modifier: Modifier = Modifier,
    total: Int = items.size,
    onClick: ((Int) -> Unit)? = null,
) {
    if (items.isEmpty()) return
    BoxWithConstraints(modifier) {
        val width = maxWidth
        if (items.size == 1) {
            Single(items.first(), width, onClick?.let { { it(0) } })
            return@BoxWithConstraints
        }
        val cell = (width - Gap * 2) / 3
        val columns = if (items.size == 4) 2 else 3
        val shown = items.take(MAX_SHOWN)
        val hidden = total - shown.size
        FlowRow(
            Modifier.width(cell * columns + Gap * (columns - 1)),
            horizontalArrangement = Arrangement.spacedBy(Gap),
            verticalArrangement = Arrangement.spacedBy(Gap),
            maxItemsInEachRow = columns,
        ) {
            shown.forEachIndexed { i, item ->
                Tile(
                    item,
                    width = cell,
                    height = cell,
                    overlay = if (i == shown.lastIndex && hidden > 0) "+$hidden" else null,
                    onClick = onClick?.let { { it(i) } },
                )
            }
        }
    }
}

/** A single item, sized to its shape: extreme shapes are cropped, long pictures show their top. */
@Composable
private fun Single(item: MixedMedia, width: Dp, onClick: (() -> Unit)?) {
    val aspectRatio =
        when (item) {
            is MixedMedia.Pic -> item.pic.aspectRatio
            is MixedMedia.Video -> item.video.aspectRatio
        }
    val ratio = (aspectRatio ?: 1f).coerceIn(0.6f, 1.8f)
    val maxSide = width * 0.7f
    Tile(
        item,
        width = if (ratio >= 1) maxSide else maxSide * ratio,
        height = if (ratio >= 1) maxSide / ratio else maxSide,
        onClick = onClick,
    )
}

@Composable
private fun Tile(
    item: MixedMedia,
    width: Dp,
    height: Dp,
    overlay: String? = null,
    onClick: (() -> Unit)?,
) {
    Box(
        Modifier.size(width, height)
            .testTag("media")
            .clip(TileShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        when (item) {
            is MixedMedia.Pic -> PicTile(item.pic, width, height)
            is MixedMedia.Video -> VideoTile(item.video)
        }
        if (overlay != null) MoreOverlay(overlay)
    }
}

@Composable
private fun PicTile(pic: PicInfo, width: Dp, height: Dp) {
    val settings by LocalAppGraph.current.settings.settings.collectAsStateWithLifecycle()
    val density = LocalDensity.current.density
    val variant =
        pic.variantFor(settings.imageQuality.listPixels(minOf(width, height).value, density))
    val badge =
        when {
            pic.type == PicType.Gif -> "GIF"
            pic.type == PicType.LivePhoto -> "实况"
            pic.isLong -> "长图"
            else -> null
        }
    Box(Modifier.fillMaxSize()) {
        WeiboImage(
            variant.url,
            Modifier.fillMaxSize(),
            alignment = if (pic.isLong) Alignment.TopCenter else Alignment.Center,
        )
        if (badge != null) MediaBadge(badge, Modifier.align(Alignment.BottomEnd).padding(4.dp))
    }
}

/** A video's cover in the grid, marked as a video and with its length. */
@Composable
private fun VideoTile(video: VideoInfo) {
    Box(Modifier.fillMaxSize()) {
        val cover = video.coverUrl
        if (cover != null) {
            WeiboImage(cover, Modifier.fillMaxSize())
        } else {
            Box(
                Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest)
            )
        }
        PlayIcon(36.dp, Modifier.align(Alignment.Center))
        val duration = video.duration
        if (duration != null && duration > 0) {
            MediaBadge(formatDuration(duration), Modifier.align(Alignment.BottomEnd).padding(4.dp))
        }
    }
}

/** The play mark over a video cover. */
@Composable
fun PlayIcon(size: Dp, modifier: Modifier = Modifier) {
    Icon(
        Icons.Filled.PlayCircle,
        contentDescription = "播放",
        modifier =
            modifier
                .size(size)
                .shadow(
                    8.dp,
                    RoundedCornerShape(50),
                    clip = false,
                    ambientColor = Color.Black,
                    spotColor = Color.Black,
                ),
        tint = Color.White.copy(alpha = 0.7f),
    )
}

/** The "+N" over the last cell when there are more items than shown. */
@Composable
private fun MoreOverlay(label: String) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Medium)
    }
}
