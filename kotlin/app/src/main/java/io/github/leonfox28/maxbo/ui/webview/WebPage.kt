package io.github.leonfox28.maxbo.ui.webview

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import io.github.leonfox28.maxbo.core.net.BROWSER_USER_AGENT
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import java.net.URLEncoder
import kotlinx.coroutines.launch

/**
 * Weibo's search results for [query], e.g. `#话题#`, on its mobile site: s.weibo.com is laid out for
 * desktops and sends phones there.
 */
fun weiboSearchUrl(query: String): String {
    val topic = query.length > 2 && query.startsWith("#") && query.endsWith("#")
    // What s.weibo.com redirects phones to: topic or general results.
    val container = if (topic) "231522" else "100103"
    return "https://m.weibo.cn/search?containerid=$container&q=${URLEncoder.encode(query, "UTF-8")}"
}

/**
 * A web page inside the app, for what maxbo has no page of its own for, such as Weibo's own search
 * results or its security checks.
 *
 * It shares the WebView cookie store with the login page and sends the same browser identity, so
 * Weibo sees the signed-in user in the same browser. Back steps back through the pages visited here
 * before leaving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebPage(url: String) {
    val navigator = LocalNavigator.current
    val context = LocalContext.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    var webView by remember { mutableStateOf<WebView?>(null) }
    var title by remember { mutableStateOf("") }
    var progress by remember { mutableIntStateOf(0) }
    var canGoBack by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }

    fun back() {
        val view = webView
        if (view != null && view.canGoBack()) view.goBack() else navigator.pop()
    }

    fun openExternally() {
        val current = webView?.url ?: url
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, current.toUri()))
        } catch (_: ActivityNotFoundException) {
            scope.launch { snackbars.showSnackbar("无法打开链接") }
        }
    }

    BackHandler(enabled = canGoBack) { webView?.goBack() }
    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = ::back) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        if (canGoBack) {
                            IconButton(onClick = navigator::pop) {
                                Icon(Icons.Filled.Close, contentDescription = "关闭")
                            }
                        }
                        Box {
                            IconButton(onClick = { menu = true }) {
                                Icon(Icons.Filled.MoreVert, contentDescription = "更多")
                            }
                            DropdownMenu(menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(
                                    text = { Text("刷新") },
                                    onClick = {
                                        menu = false
                                        webView?.reload()
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("在浏览器中打开") },
                                    onClick = {
                                        menu = false
                                        openExternally()
                                    },
                                )
                            }
                        }
                    },
                )
                if (progress < 100) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        },
        snackbarHost = { AppSnackbarHost() },
    ) { padding ->
        AndroidView(
            factory = { context ->
                browserWebView(
                        context,
                        onHistory = { view ->
                            title = view.title.orEmpty()
                            canGoBack = view.canGoBack()
                        },
                        onProgress = { progress = it },
                    )
                    .also {
                        webView = it
                        it.loadUrl(url)
                    }
            },
            modifier =
                Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding).imePadding(),
            onRelease = WebView::destroy,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun browserWebView(
    context: android.content.Context,
    onHistory: (WebView) -> Unit,
    onProgress: (Int) -> Unit,
) =
    WebView(context).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.userAgentString = BROWSER_USER_AGENT
        webViewClient =
            object : WebViewClient() {
                override fun shouldOverrideUrlLoading(
                    view: WebView,
                    request: WebResourceRequest,
                ): Boolean {
                    val url = request.url.toString()
                    if (url.startsWith("https://")) return false
                    // Weibo still links and redirects to some pages over plain
                    // http, which Android refuses; the same pages are served
                    // over https.
                    if (url.startsWith("http://")) {
                        if (!request.isForMainFrame) return false
                        view.loadUrl("https://${url.substring(7)}")
                    }
                    // Anything else is an app link such as sinaweibo://.
                    return true
                }

                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    onHistory(view)
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    onHistory(view)
                }

                override fun doUpdateVisitedHistory(
                    view: WebView,
                    url: String?,
                    isReload: Boolean,
                ) {
                    onHistory(view)
                }
            }
        webChromeClient =
            object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    onProgress(newProgress)
                }

                override fun onReceivedTitle(view: WebView, title: String?) {
                    onHistory(view)
                }
            }
    }
