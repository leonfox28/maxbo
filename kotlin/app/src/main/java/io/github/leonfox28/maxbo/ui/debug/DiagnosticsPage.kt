package io.github.leonfox28.maxbo.ui.debug

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Autorenew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import io.github.leonfox28.maxbo.data.model.CommentOrder
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.AppAlertDialog
import io.github.leonfox28.maxbo.ui.widget.BackButton
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Checks the app against the real session, in debug builds only. Lines also go to logcat, prefixed
 * `diag:`. They hold counts and names, never cookie values, ids or what anyone wrote.
 *
 * Of the Flutter app's probes, those still of use came over: the app's own reads against the real
 * API, opening a page by its path (as of a test post's author), and renewal. Those that answered
 * the open questions of PLAN §8 stayed behind with their answers, and so did the one that liked and
 * commented for real.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiagnosticsPage() {
    val graph = LocalAppGraph.current
    val scope = rememberCoroutineScope()
    val lines = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var openingRoute by remember { mutableStateOf(false) }
    val navigator = LocalNavigator.current
    fun log(line: String) {
        devLog("diag: $line")
        lines += line
    }

    fun run(probe: suspend (AppGraph, (String) -> Unit) -> Unit) {
        lines.clear()
        running = true
        scope.launch {
            try {
                probe(graph, ::log)
            } finally {
                running = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("开发者诊断") },
                navigationIcon = { BackButton() },
                actions = {
                    IconButton(onClick = { run(::probeReads) }, enabled = !running) {
                        Icon(Icons.Filled.PlayArrow, contentDescription = "核对接口（只读）")
                    }
                    IconButton(onClick = { openingRoute = true }) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "打开页面")
                    }
                    IconButton(onClick = { run(::probeRenewal) }, enabled = !running) {
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
    if (openingRoute) {
        RouteDialog(
            close = { openingRoute = false },
            open = { path ->
                val route = routeFor(path)
                if (route == null) log("no page at $path") else navigator.push(route)
            },
        )
    }
}

/** Asks for a page by its path, as the Flutter app's router had them. */
@Composable
private fun RouteDialog(close: () -> Unit, open: (String) -> Unit) {
    var path by remember { mutableStateOf("") }
    AppAlertDialog(
        onDismissRequest = close,
        title = { Text("打开页面") },
        text = {
            OutlinedTextField(
                path,
                { path = it },
                placeholder = { Text("/n/昵称、/u/uid、/status/id、/results?q=…") },
                singleLine = true,
            )
        },
        confirmButton = {
            TextButton(
                onClick = {
                    close()
                    open(path.trim())
                }
            ) {
                Text("打开")
            }
        },
        dismissButton = { TextButton(onClick = close) { Text("取消") } },
    )
}

/** The page at [path]: `/n/<name>`, `/u/<uid>`, `/status/<id>` or `/results?q=<query>`. */
internal fun routeFor(path: String): Route? {
    val url = "https://maxbo.invalid$path".toHttpUrlOrNull() ?: return null
    val segments = url.pathSegments
    val value = segments.getOrNull(1)?.takeIf { it.isNotEmpty() && segments.size == 2 }
    return when (segments.firstOrNull()) {
        "n" -> value?.let { Route.ProfileByName(it) }
        "u" -> value?.let { Route.Profile(it) }
        "status" -> value?.let { Route.StatusDetail(it) }
        "results" ->
            url.queryParameter("q")?.takeIf { it.isNotBlank() }?.let { Route.SearchResults(it) }
        else -> null
    }
}

/**
 * Reads through the app's own API what its pages read, to check its parsing against the real API:
 * the account, the timeline, a status and its comments, the profile, hot and searched posts, and
 * which notices are unread (the notice lists themselves would mark them read). Logs counts only.
 */
private suspend fun probeReads(graph: AppGraph, log: (String) -> Unit) {
    val uid = (graph.session.state.value as? SessionState.SignedIn)?.account?.uid
    if (uid == null) {
        log("reads need a signed-in session")
        return
    }
    val api = graph.api.withoutRenewal()
    suspend fun step(label: String, read: suspend () -> String) {
        try {
            log("$label: ${read()}")
        } catch (e: WeiboException) {
            log("$label: ${e.javaClass.simpleName} ${e.message}")
        }
    }

    for (site in WeiboCookieJar.SITES) {
        log("jar $site: ${graph.session.jar.names(site).joinToString(", ")}")
    }
    step("account") { if (api.fetchCurrentAccount().uid == uid) "the signed-in one" else "another" }
    var first: Status? = null
    step("timeline") {
        val page = api.fetchHomeTimeline(uid)
        first = page.statuses.firstOrNull()
        val statuses = page.statuses
        "${statuses.size} statuses (${statuses.count { it.pics.isNotEmpty() }} with pictures, " +
            "${statuses.count { it.video != null }} videos, " +
            "${statuses.count { it.retweeted != null }} reposts), more=${page.nextCursor != null}"
    }
    first?.let { status ->
        step("status") {
            val full = api.fetchStatus(status.id)
            "full text=${full.plainText != null}, ${full.commentsCount} comments counted"
        }
        status.user?.id?.let { author ->
            step("comments") {
                val page = api.fetchComments(status.id, author, CommentOrder.Hot)
                "${page.items.size}, more=${page.nextCursor != null}"
            }
        }
    }
    step("profile") { if (api.fetchProfile(uid = uid).user.id == uid) "found" else "another user" }
    step("own statuses") { "${api.fetchUserStatuses(uid).items.size}" }
    step("hot search") {
        val hot = api.fetchHotSearch()
        "${hot.pinned.size} pinned, ${hot.items.size} ranked"
    }
    step("search") {
        val results = api.searchStatuses("微博")
        "${results.statuses.items.size}, topic=${results.topic != null}"
    }
    step("unread") { api.fetchUnreadNotices().kinds.joinToString(", ").ifEmpty { "none" } }
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
        val page = graph.api.fetchHomeTimeline(uid = signedIn.account.uid)
        log("timeline: ${page.statuses.size} statuses after ${elapsed()} ms")
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
