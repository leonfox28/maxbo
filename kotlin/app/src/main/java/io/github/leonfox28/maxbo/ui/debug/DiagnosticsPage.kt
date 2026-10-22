package io.github.leonfox28.maxbo.ui.debug

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.core.util.readMaps
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.ui.widget.BackButton
import kotlinx.coroutines.launch

/**
 * Checks the app against the real session, in debug builds only. Lines also go to logcat, prefixed
 * `diag:`. More probes come over from the Flutter app's page as their features do (K6).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsPage() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    fun log(line: String) {
        devLog("diag: $line")
        lines += line
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者诊断") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(
                        onClick = {
                            lines.clear()
                            running = true
                            scope.launch {
                                try {
                                    probeRenewal(graph, ::log)
                                } finally {
                                    running = false
                                }
                            }
                        },
                        enabled = !running,
                    ) {
                        Icon(Icons.Filled.Autorenew, contentDescription = "模拟登录过期（会触发续期）")
                    }
                },
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding =
                PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding(),
                ),
        ) {
            items(lines) {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

/**
 * Swaps the session's `SUB` for an invalid one, then loads the timeline through the app's own API,
 * to see whether renewal works and how long it takes. Logs which `SUB` ends up where, never values.
 * If the session ends, signs back in with the cookies from before, as long as they still work.
 */
private suspend fun probeRenewal(graph: AppGraph, log: (String) -> Unit) {
    val session = graph.session
    val signedIn = session.state.value as? SessionState.SignedIn
    if (signedIn == null) {
        log("renewal probe needs a signed-in session")
        return
    }
    val lapsed = "lapsed-by-diagnostics"
    val jar = session.jar
    val saved = jar.toMap()
    val savedSub = saved[WeiboCookieJar.WEB]?.get("SUB")
    suspend fun webSub() = graph.webCookies.readWeiboCookies()[WeiboCookieJar.WEB]?.get("SUB")
    fun compare(sub: String?) =
        when (sub) {
            null -> "missing"
            lapsed -> "the lapsed one"
            savedSub -> "as before"
            else -> "new"
        }

    val webBefore = webSub()
    log("webview SUB: ${compare(webBefore)}")
    jar.replaceAll(
        saved + (WeiboCookieJar.WEB to (saved[WeiboCookieJar.WEB].orEmpty() + ("SUB" to lapsed)))
    )
    val started = System.nanoTime()
    fun elapsed() = (System.nanoTime() - started) / 1_000_000
    try {
        // The timeline's own request, until the timeline itself comes over (K2).
        val json =
            graph.api.getJson(
                "${WeiboApi.WEB}/ajax/feed/friendstimeline",
                mapOf(
                    "list_id" to "11000${signedIn.account.uid}",
                    "refresh" to 4,
                    "count" to 20,
                    "since_id" to 0,
                ),
            )
        log("timeline: ${readMaps(json["statuses"]).size} statuses after ${elapsed()} ms")
    } catch (e: WeiboException) {
        log("timeline: ${e.javaClass.simpleName} ${e.message} after ${elapsed()} ms")
    }
    val sessionSub = jar.value(WeiboCookieJar.WEB, "SUB")
    val webAfter = webSub()
    log("session SUB: ${compare(sessionSub)}")
    log(
        "webview SUB: ${compare(webAfter)}" +
            (if (webAfter == webBefore) " (unchanged)" else " (changed)") +
            (if (webAfter == sessionSub) ", same as the session" else "")
    )

    if (session.state.value !is SessionState.SignedIn) {
        log("signed out: signing back in with the cookies from before")
        try {
            session.completeLogin(saved)
            log("signed back in")
        } catch (e: WeiboException) {
            log("signing back in: ${e.javaClass.simpleName}")
        }
    } else if (sessionSub == lapsed) {
        jar.replaceAll(saved)
        log("not renewed: put the session back")
    }
}
