package io.github.leonfox28.maxbo.ui.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MotionPhotosOff
import androidx.compose.material.icons.filled.MotionPhotosOn
import androidx.compose.material.icons.filled.MotionPhotosPause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import io.github.leonfox28.maxbo.core.media.VideoHttpHeaders
import io.github.leonfox28.maxbo.core.media.resolveLivePhotoUrl
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.PicInfo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Plays the moving part of the viewer's live photos ("实况"), one at a time and once through, over
 * the still picture; its video is fetched only when asked for. Its URL expires like a video's (PLAN
 * §8), so one that fails is fetched afresh from status [statusId], once.
 */
@OptIn(UnstableApi::class)
@Stable
class LivePhotoPlayer(
    private val context: Context,
    private val api: WeiboApi,
    private val statusId: String?,
    private val scope: CoroutineScope,
) {
    /** The picture whose motion is loading or playing. */
    var current by mutableStateOf<PicInfo?>(null)
        private set

    /** Whether [current] is moving, rather than still loading. */
    var moving by mutableStateOf(false)
        private set

    /** The id of the picture whose motion failed to load. */
    var failed by mutableStateOf<String?>(null)
        private set

    /** Made once there is a motion to play. */
    var player by mutableStateOf<ExoPlayer?>(null)
        private set

    /** The picture [player] holds the motion of, kept buffered for playing again. */
    private var loaded: String? = null
    /** Whether [loaded]'s URL came from fetching its status again. */
    private var fetched = false
    private var resolving: Job? = null

    private val listener =
        object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                moving = isPlaying && current != null
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) current = null
            }

            override fun onPlayerError(error: PlaybackException) {
                devLog("live photo failed: ${error.errorCodeName}")
                // Nothing to play again: the next play loads it anew.
                loaded = null
                val pic = current ?: return
                if (fetched || statusId == null) {
                    current = null
                    failed = pic.id
                } else {
                    play(pic, refresh = true)
                }
            }
        }

    /** Plays [pic]'s motion from the start; with [refresh], from a URL fetched afresh. */
    fun play(pic: PicInfo, refresh: Boolean = false) {
        stop()
        current = pic
        failed = null
        val buffered = player?.takeIf { pic.id == loaded && !refresh }
        if (buffered != null) {
            buffered.seekTo(0)
            buffered.play()
            return
        }
        loaded = null
        resolving = scope.launch {
            val source =
                try {
                    resolveLivePhotoUrl(api, pic, statusId, refresh = refresh)
                } catch (e: WeiboException) {
                    devLog("live photo: ${e.javaClass.simpleName}")
                    current = null
                    failed = pic.id
                    return@launch
                }
            loaded = pic.id
            fetched = source.fetched
            val player = player ?: create().also { player = it }
            player.setMediaItem(MediaItem.fromUri(source.url))
            player.prepare()
            player.play()
        }
    }

    fun stop() {
        resolving?.cancel()
        player?.pause()
        current = null
        moving = false
    }

    fun release() {
        stop()
        player?.release()
        player = null
    }

    private fun create(): ExoPlayer {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(VideoHttpHeaders)
        return ExoPlayer.Builder(context)
            .setMediaSourceFactory(DefaultMediaSourceFactory(http))
            // Keeps what has played (a few seconds), so playing it again
            // needs no new download.
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBackBuffer(BACK_BUFFER_MS, /* retainBackBufferFromKeyframe= */ true)
                    .build()
            )
            .build()
            .apply { addListener(listener) }
    }

    private companion object {
        const val BACK_BUFFER_MS = 30_000
    }
}

/**
 * [pic]'s motion over its still picture while [live] plays it. Until the first frame shows, the
 * still shows through. It takes taps off the picture beneath, so [onClick] does what tapping that
 * would.
 */
@OptIn(UnstableApi::class)
@Composable
fun LivePhotoMotion(live: LivePhotoPlayer, pic: PicInfo, onClick: () -> Unit) {
    val player = live.player ?: return
    AnimatedVisibility(
        live.current?.id == pic.id,
        Modifier.fillMaxSize(),
        enter = fadeIn(tween(150)),
        exit = fadeOut(tween(250)),
    ) {
        ContentFrame(
            player,
            Modifier.fillMaxSize().pointerInput(onClick) { detectTapGestures { onClick() } },
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            contentScale = ContentScale.Fit,
            shutter = {},
        )
    }
}

/** The "实况" mark of [pic] in the viewer, which plays or stops its motion. */
@Composable
fun LivePhotoButton(live: LivePhotoPlayer, pic: PicInfo, modifier: Modifier = Modifier) {
    val playing = live.current?.id == pic.id
    val failed = live.failed == pic.id
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(Shape)
            .background(Color.Black.copy(alpha = 0.54f))
            .clickable(
                onClickLabel = if (playing) "停止播放实况" else "播放实况",
                role = Role.Button,
            ) {
                when {
                    playing -> live.stop()
                    // As a video's "重试" does.
                    failed -> live.play(pic, refresh = true)
                    else -> live.play(pic)
                }
            }
            .padding(start = 8.dp, top = 4.dp, end = 10.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (playing && !live.moving) {
            CircularProgressIndicator(
                Modifier.padding(1.dp).size(14.dp),
                color = Color.White,
                strokeWidth = 1.5.dp,
            )
        } else {
            Icon(
                when {
                    playing -> Icons.Filled.MotionPhotosPause
                    failed -> Icons.Filled.MotionPhotosOff
                    else -> Icons.Filled.MotionPhotosOn
                },
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color.White,
            )
        }
        Spacer(Modifier.width(4.dp))
        Text(
            if (failed) "实况加载失败，点击重试" else "实况",
            color = Color.White,
            fontSize = 13.sp,
        )
    }
}

private val Shape = RoundedCornerShape(50)
