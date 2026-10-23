package io.github.leonfox28.maxbo.ui.widget

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.compose.material3.SnackbarHostState
import androidx.core.net.toUri
import kotlinx.coroutines.CoroutineScope

/** Opens [url] in another app, such as the browser, or says that none can in [snackbars]. */
fun Context.openExternally(url: String, snackbars: SnackbarHostState, scope: CoroutineScope) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, url.toUri()))
    } catch (_: ActivityNotFoundException) {
        snackbars.showMessage("无法打开链接", scope)
    }
}
