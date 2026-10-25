package io.github.leonfox28.maxbo.ui.media

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogWindowProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.request.ImageRequest
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.LocalImageLoader
import io.github.leonfox28.maxbo.core.media.imageCacheKey
import io.github.leonfox28.maxbo.data.model.PicInfo
import io.github.leonfox28.maxbo.data.model.PicType
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.widget.MediaBadge
import kotlin.math.abs
import me.saket.telephoto.ExperimentalTelephotoApi
import me.saket.telephoto.flick.FlickToDismiss
import me.saket.telephoto.flick.FlickToDismissState
import me.saket.telephoto.flick.rememberFlickToDismissState
import me.saket.telephoto.zoomable.coil3.ZoomableAsyncImage
import me.saket.telephoto.zoomable.rememberZoomableImageState

/**
 * Full-screen pictures: swipe between them, pinch or double-tap to zoom, tap or flick vertically to
 * close. Shown as a dialog over the page, which shows through as a picture is flicked away.
 * Pictures taller than the screen open filling its width, from the top. A live photo moves when its
 * "实况" mark is tapped or the picture long-pressed.
 *
 * @param statusId the status the pictures belong to, if any, where a live photo's video is fetched
 *   afresh.
 */
@OptIn(ExperimentalTelephotoApi::class)
@Composable
fun ImageViewerPage(pics: List<PicInfo>, initialIndex: Int, statusId: String? = null) {
    val navigator = LocalNavigator.current
    val pager = rememberPagerState(initialIndex) { pics.size }
    // How far the picture on show has been flicked, 0 to 1.
    var flicked by remember { mutableFloatStateOf(0f) }
    val view = LocalView.current
    LaunchedEffect(view) { (view.parent as? DialogWindowProvider)?.window?.setDimAmount(0f) }
    LightSystemBarIcons()
    val api = LocalAppGraph.current.api
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val live = remember { LivePhotoPlayer(context, api, statusId, scope) }
    DisposableEffect(live) { onDispose { live.release() } }
    LaunchedEffect(pager.currentPage) { live.stop() }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { live.stop() }

    Box(
        Modifier.fillMaxSize()
            .background(Color.Black.copy(alpha = (1 - flicked * 2).coerceIn(0f, 1f)))
    ) {
        HorizontalPager(
            pager,
            Modifier.fillMaxSize(),
            pageSpacing = 16.dp,
            key = { pics[it].id },
        ) { page ->
            val flick = rememberFlickToDismissState(rotateOnDrag = false)
            LaunchedEffect(flick, page) {
                snapshotFlow { flick.gestureState to abs(flick.offsetFraction) }
                    .collect { (gesture, fraction) ->
                        if (page == pager.currentPage) flicked = fraction
                        if (gesture is FlickToDismissState.GestureState.Dismissed) navigator.pop()
                    }
            }
            FlickToDismiss(flick, Modifier.fillMaxSize()) {
                ViewerImage(pics[page], live, onClick = navigator::pop)
            }
        }
        val pic = pics[pager.currentPage]
        val badge =
            when (pic.type) {
                PicType.Gif -> "GIF"
                // Without its video, it stays still.
                PicType.LivePhoto -> if (pic.liveVideo == null) "实况" else null
                PicType.Pic -> null
            }
        Row(
            Modifier.align(Alignment.BottomCenter)
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pics.size > 1) {
                Text(
                    "${pager.currentPage + 1} / ${pics.size}",
                    style =
                        TextStyle(
                            color = Color.White,
                            fontSize = 15.sp,
                            shadow = Shadow(blurRadius = 4f),
                        ),
                )
            }
            if (badge != null) {
                Spacer(Modifier.width(8.dp))
                MediaBadge(badge)
            }
            if (pic.liveVideo != null) {
                Spacer(Modifier.width(4.dp))
                LivePhotoButton(live, pic)
            }
        }
    }
}

@Composable
private fun ViewerImage(pic: PicInfo, live: LivePhotoPlayer, onClick: () -> Unit) {
    val settings by LocalAppGraph.current.settings.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var attempt by remember { mutableIntStateOf(0) }
    var failed by remember { mutableStateOf(false) }
    val url = settings.imageQuality.viewerVariant(pic).url
    val request =
        remember(url, attempt) {
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(imageCacheKey(url))
                .diskCacheKey(imageCacheKey(url))
                .listener(onStart = { failed = false }, onError = { _, _ -> failed = true })
                .build()
        }
    val state = rememberZoomableImageState()
    val moving = live.current?.id == pic.id
    // The motion shows at the picture's unzoomed size.
    LaunchedEffect(moving) { if (moving) state.zoomableState.resetZoom() }
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // Taller than the viewer's shape: fill its width, from the top,
        // rather than show a thin strip.
        val viewAspect = maxWidth / maxHeight
        val tall = (pic.aspectRatio ?: viewAspect) < viewAspect
        ZoomableAsyncImage(
            model = request,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            state = state,
            imageLoader = LocalImageLoader.current,
            alignment = if (tall) Alignment.TopCenter else Alignment.Center,
            contentScale = if (tall) ContentScale.FillWidth else ContentScale.Fit,
            onClick = { onClick() },
            onLongClick = if (pic.liveVideo != null) ({ live.play(pic) }) else null,
        )
        LivePhotoMotion(live, pic, onClick)
        when {
            failed ->
                TextButton(onClick = { attempt++ }) {
                    Icon(
                        Icons.Outlined.BrokenImage,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.7f),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("加载失败，点击重试", color = Color.White.copy(alpha = 0.7f))
                }
            !state.isImageDisplayed ->
                CircularProgressIndicator(color = Color.White.copy(alpha = 0.7f))
        }
    }
}
