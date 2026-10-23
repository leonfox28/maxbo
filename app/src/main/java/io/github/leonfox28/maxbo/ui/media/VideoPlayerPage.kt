package io.github.leonfox28.maxbo.ui.media

import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.compose.ContentFrame
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.VideoHttpHeaders
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.media.resolveVideoUrl
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.core.util.formatDuration
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Plays the video of status [id] full screen; in a post mixing pictures and videos, the one at
 * [index] (see `Status.videoAt`).
 *
 * The stream URLs of [initial] (the status as the previous page had it) expire about an hour after
 * it loaded; past that, or if the video CDN rejects one, the status is fetched again for fresh
 * URLs.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerPage(id: String, index: Int?, initial: Status?) {
    val api = LocalAppGraph.current.api
    val context = LocalContext.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    val known = initial?.videoAt(index)
    val player = remember {
        val http = DefaultHttpDataSource.Factory().setDefaultRequestProperties(VideoHttpHeaders)
        ExoPlayer.Builder(context).setMediaSourceFactory(DefaultMediaSourceFactory(http)).build()
    }
    var ready by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var interactions by remember { mutableIntStateOf(0) }
    val session = remember { PlaybackSession() }

    fun open(refresh: Boolean = false, resumeAt: Long? = null) {
        val generation = ++session.generation
        ready = false
        error = null
        player.stop()
        player.clearMediaItems()
        scope.launch {
            val source =
                try {
                    resolveVideoUrl(api, id, index = index, known = known, refresh = refresh)
                } catch (e: WeiboException) {
                    if (generation == session.generation) error = e.message
                    return@launch
                }
            if (generation != session.generation) return@launch
            // A URL that came with the status gets one refresh when it
            // fails; a fresh one failing is reported.
            session.fetched = source.fetched
            player.setMediaItem(MediaItem.fromUri(source.url), resumeAt ?: 0L)
            player.prepare()
            player.play()
        }
    }

    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) {
                    if (state == Player.STATE_READY) ready = true
                    // Offer the replay button once the video ends.
                    if (state == Player.STATE_ENDED) controlsVisible = true
                }

                override fun onPlayerError(e: PlaybackException) {
                    devLog("video failed: ${e.errorCodeName}")
                    if (session.fetched) {
                        ready = false
                        error = "视频加载失败"
                    } else {
                        open(refresh = true, resumeAt = player.currentPosition)
                    }
                }
            }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(Unit) { open() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }
    // Hides the controls a while after the last interaction, during playback.
    LaunchedEffect(controlsVisible, interactions, ready) {
        if (!controlsVisible) return@LaunchedEffect
        delay(3_000)
        if (player.isPlaying) controlsVisible = false
    }
    LightSystemBarIcons()

    val shown = controlsVisible || !ready
    Box(
        Modifier.fillMaxSize().background(Color.Black).clickable(
            remember { MutableInteractionSource() },
            indication = null,
        ) {
            controlsVisible = !controlsVisible
            interactions++
        }
    ) {
        if (ready) {
            ContentFrame(
                player,
                Modifier.fillMaxSize(),
                surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
                contentScale = ContentScale.Fit,
            )
        } else {
            known?.coverUrl?.let {
                WeiboImage(
                    it,
                    Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit,
                    placeholder = false,
                )
            }
        }
        if (!ready) {
            Box(Modifier.align(Alignment.Center)) {
                val message = error
                if (message == null) {
                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
                } else {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(message, color = Color.White)
                        Spacer(Modifier.height(12.dp))
                        OutlinedButton(onClick = { open(refresh = true) }) {
                            Text("重试", color = Color.White)
                        }
                    }
                }
            }
        } else {
            Controls(player, controlsVisible, onInteraction = { interactions++ })
        }
        AnimatedVisibility(
            shown,
            Modifier.windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Start)
            ),
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200)),
        ) {
            IconButton(onClick = navigator::pop) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    tint = Color.White,
                )
            }
        }
    }
}

/** Which load of the video is current, and whether its URL was just fetched. */
private class PlaybackSession {
    var generation = 0
    var fetched = false
}

/** Play/pause in the middle, position and a seek bar at the bottom. */
@Composable
private fun Controls(player: Player, visible: Boolean, onInteraction: () -> Unit) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var ended by remember { mutableStateOf(player.playbackState == Player.STATE_ENDED) }
    var position by remember { mutableLongStateOf(player.currentPosition) }
    var duration by remember { mutableLongStateOf(player.duration.coerceAtLeast(0)) }
    var seeking by remember { mutableStateOf<Float?>(null) }
    DisposableEffect(player) {
        val listener =
            object : Player.Listener {
                override fun onEvents(player: Player, events: Player.Events) {
                    playing = player.isPlaying
                    ended = player.playbackState == Player.STATE_ENDED
                    duration = player.duration.coerceAtLeast(0)
                    position = player.currentPosition
                }
            }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(playing) {
        while (playing) {
            position = player.currentPosition
            delay(250)
        }
    }
    AnimatedVisibility(
        visible,
        Modifier.fillMaxSize(),
        enter = fadeIn(tween(200)),
        exit = fadeOut(tween(200)),
    ) {
        Box(Modifier.fillMaxSize()) {
            FilledIconButton(
                onClick = {
                    if (playing) {
                        player.pause()
                    } else {
                        if (ended) player.seekTo(0)
                        player.play()
                    }
                    onInteraction()
                },
                modifier = Modifier.align(Alignment.Center).size(72.dp),
                colors =
                    IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.Black.copy(alpha = 0.45f),
                        contentColor = Color.White,
                    ),
            ) {
                Icon(
                    when {
                        playing -> Icons.Filled.Pause
                        ended -> Icons.Filled.Replay
                        else -> Icons.Filled.PlayArrow
                    },
                    contentDescription = if (playing) "暂停" else "播放",
                    modifier = Modifier.size(48.dp),
                )
            }
            Row(
                Modifier.align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.54f))
                        )
                    )
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal
                        )
                    )
                    .padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val shownPosition = seeking?.let { (it * duration).toLong() } ?: position
                Text(
                    formatDuration((shownPosition / 1000).toInt()),
                    color = Color.White,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.width(8.dp))
                Slider(
                    value = if (duration > 0) shownPosition.toFloat() / duration else 0f,
                    onValueChange = {
                        seeking = it
                        onInteraction()
                    },
                    onValueChangeFinished = {
                        seeking?.let { player.seekTo((it * duration).toLong()) }
                        seeking = null
                    },
                    modifier = Modifier.weight(1f),
                    colors =
                        SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f),
                        ),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    formatDuration((duration / 1000).toInt()),
                    color = Color.White,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
