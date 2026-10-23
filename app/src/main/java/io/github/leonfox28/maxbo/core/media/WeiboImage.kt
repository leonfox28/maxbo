package io.github.leonfox28.maxbo.core.media

import android.content.Context
import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BrokenImage
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.ImageLoader
import coil3.compose.AsyncImagePainter
import coil3.compose.rememberAsyncImagePainter
import coil3.disk.DiskCache
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.OkHttpClient

// Signature parameters on avatar URLs that change every few hours.
private val signatureParams = listOf("Expires", "ssig", "KID")

/**
 * The cache key for [url]: signature parameters are dropped, so a re-signed URL for the same
 * picture hits the cache, and so is plain http, which is fetched over https anyway.
 */
fun imageCacheKey(url: String): String {
    val parsed = url.toHttpUrlOrNull()
    if (parsed == null || (parsed.querySize == 0 && parsed.isHttps)) return url
    return parsed
        .newBuilder()
        .scheme("https")
        .apply { signatureParams.forEach(::removeAllQueryParameters) }
        .build()
        .toString()
}

/**
 * The image loader for Weibo's pictures, on [base]'s connections: every request has weibo.com as
 * its Referer (Weibo's image hosts answer 403 without it, and a browser tab on weibo.com sends it
 * too), goes over https, and pictures are cached on [diskCache].
 */
fun weiboImageLoader(context: Context, base: OkHttpClient, diskCache: DiskCache): ImageLoader {
    val client =
        base
            .newBuilder()
            .addInterceptor(
                Interceptor { chain ->
                    val request = chain.request()
                    // Some come as plain http (a comment's pictures, say), which
                    // Android refuses; Weibo's image hosts serve them over https.
                    val url = request.url.newBuilder().scheme("https").build()
                    chain.proceed(
                        request
                            .newBuilder()
                            .url(url)
                            .header("Referer", "https://weibo.com/")
                            .build()
                    )
                }
            )
            .build()
    return ImageLoader.Builder(context)
        .components {
            add(OkHttpNetworkFetcherFactory(callFactory = { client }))
            add(
                if (Build.VERSION.SDK_INT >= 28) AnimatedImageDecoder.Factory()
                else GifDecoder.Factory()
            )
        }
        .diskCache(diskCache)
        .build()
}

val LocalImageLoader = staticCompositionLocalOf<ImageLoader> { error("No ImageLoader provided") }

/**
 * A Weibo-hosted image with a placeholder while loading and tap-to-retry when it fails.
 *
 * Callers pick a rendition close to the displayed size (see `PicInfo.variantFor`), so images are
 * decoded as downloaded.
 *
 * @param placeholder whether to fill the area while loading; off for inline emoticons.
 */
@Composable
fun WeiboImage(
    url: String,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    alignment: Alignment = Alignment.Center,
    placeholder: Boolean = true,
    contentDescription: String? = null,
) {
    val context = LocalContext.current
    var attempt by remember(url) { mutableIntStateOf(0) }
    val request =
        remember(url, attempt) {
            val key = imageCacheKey(url)
            ImageRequest.Builder(context)
                .data(url)
                .memoryCacheKey(key)
                .diskCacheKey(key)
                // A retry asks the network again rather than the failure.
                .apply { if (attempt > 0) memoryCachePolicy(CachePolicy.WRITE_ONLY) }
                .build()
        }
    val painter = rememberAsyncImagePainter(request, LocalImageLoader.current)
    val state by painter.state.collectAsStateWithLifecycle()
    val colors = MaterialTheme.colorScheme
    val loaded = state is AsyncImagePainter.State.Success
    val failed = state is AsyncImagePainter.State.Error
    Box(
        modifier
            .background(
                if (!loaded && placeholder) colors.surfaceContainerHighest else Color.Transparent
            )
            .then(if (failed) Modifier.clickable { attempt++ } else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            painter,
            contentDescription = contentDescription,
            modifier = Modifier.fillMaxSize(),
            contentScale = contentScale,
            alignment = alignment,
        )
        if (failed && placeholder) {
            Icon(
                Icons.Outlined.BrokenImage,
                contentDescription = null,
                tint = colors.onSurfaceVariant,
            )
        }
    }
}
