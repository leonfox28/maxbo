package io.github.leonfox28.maxbo.core.util

import android.util.Log
import io.github.leonfox28.maxbo.BuildConfig

/**
 * Logs [message] in debug builds only; release builds stay silent.
 *
 * Never pass cookie values or post content: the log is readable over adb.
 */
fun devLog(message: String) {
    if (BuildConfig.DEBUG) Log.d("maxbo", message)
}
