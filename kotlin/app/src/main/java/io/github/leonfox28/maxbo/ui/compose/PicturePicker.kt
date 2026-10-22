package io.github.leonfox28.maxbo.ui.compose

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts.PickMultipleVisualMedia
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.core.net.toUri
import io.github.leonfox28.maxbo.core.net.WeiboApiException
import io.github.leonfox28.maxbo.core.util.devLog
import java.io.IOException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Picks pictures from the gallery; tests provide their own. */
fun interface PicturePicker {
    /**
     * Up to [limit] pictures the user picks, by URI; none if they pick none.
     *
     * @throws PickerUnavailableException if no gallery could be opened.
     */
    suspend fun pick(limit: Int): List<String>
}

class PickerUnavailableException : Exception()

val LocalPicturePicker =
    staticCompositionLocalOf<PicturePicker> { error("No picture picker provided") }

/**
 * The system photo picker (Android 13+, and 11–12 with Google Play services), which keeps to the
 * limit; older Android has whatever opens documents, which may let the user pick more.
 */
@Composable
fun rememberGalleryPicker(): PicturePicker {
    val waiting = remember { arrayOfNulls<CompletableDeferred<List<Uri>>>(1) }
    val launcher =
        rememberLauncherForActivityResult(PickPictures()) { uris ->
            waiting[0]?.complete(uris)
            waiting[0] = null
        }
    return remember(launcher) {
        PicturePicker { limit ->
            val picked = CompletableDeferred<List<Uri>>()
            waiting[0]?.cancel()
            waiting[0] = picked
            try {
                launcher.launch(limit)
            } catch (e: ActivityNotFoundException) {
                devLog("no picture picker: ${e.javaClass.simpleName}")
                waiting[0] = null
                throw PickerUnavailableException()
            }
            picked.await().map(Uri::toString)
        }
    }
}

/** Picks up to as many pictures as it is launched with. */
private class PickPictures : ActivityResultContract<Int, List<Uri>>() {
    override fun createIntent(context: Context, input: Int): Intent {
        val request = PickVisualMediaRequest(PickVisualMedia.ImageOnly)
        // The one for many wants room for two at least.
        return if (input > 1) PickMultipleVisualMedia(input).createIntent(context, request)
        else PickVisualMedia().createIntent(context, request)
    }

    // Reads one picture as well as many.
    override fun parseResult(resultCode: Int, intent: Intent?): List<Uri> =
        PickMultipleVisualMedia().parseResult(resultCode, intent)
}

/** Reads a picked picture, to upload it. */
fun interface PictureReader {
    /** @throws WeiboApiException if it can't be read, to show as sending it failed. */
    suspend fun read(uri: String): ByteArray
}

/** Reads pictures through the content resolver, which the picker's URIs need. */
class ContentPictureReader(private val context: Context) : PictureReader {
    override suspend fun read(uri: String): ByteArray =
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.openInputStream(uri.toUri())?.use { it.readBytes() }
                    ?: throw IOException("no stream")
            } catch (e: IOException) {
                devLog("reading a picture failed: ${e.javaClass.simpleName}")
                throw WeiboApiException("图片读取失败")
            } catch (e: SecurityException) {
                devLog("reading a picture failed: ${e.javaClass.simpleName}")
                throw WeiboApiException("图片读取失败")
            }
        }
}
