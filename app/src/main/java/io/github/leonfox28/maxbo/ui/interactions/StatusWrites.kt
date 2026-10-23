package io.github.leonfox28.maxbo.ui.interactions

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.AppGraph
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.StatusVisibility
import io.github.leonfox28.maxbo.ui.compose.VisibilityButton
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Navigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.LocalOverlays
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.OverlaySheet
import io.github.leonfox28.maxbo.ui.widget.Overlays
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.showMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

/**
 * What the user is writing to repost, by status id. It stays until sent, so closing the sheet loses
 * nothing, and starts over for another account.
 */
class RepostDrafts(session: SessionController, scope: CoroutineScope) {
    private val drafts = mutableMapOf<String, String>()

    init {
        scope.launch { session.uid.drop(1).collect { drafts.clear() } }
    }

    operator fun get(id: String): String? = drafts[id]

    operator fun set(id: String, text: String) {
        drafts[id] = text
    }

    fun remove(id: String) {
        drafts -= id
    }
}

/** Reposting and deleting statuses, from any page; see [rememberStatusWrites]. */
class StatusWrites(
    private val graph: AppGraph,
    private val overlays: Overlays,
    private val snackbars: SnackbarHostState,
    private val navigator: Navigator,
    private val scope: CoroutineScope,
) {
    /**
     * Opens a sheet to repost [status], which goes to Weibo when the user taps 转发. The repost then
     * goes atop the open lists it belongs in, and [onReposted] is called.
     *
     * Reposting a repost starts the text with the chain it carries on, as weibo.com does:
     * `//@author:their text`.
     */
    fun repost(status: Status, onReposted: () -> Unit = {}) {
        scope.launch {
            var quoted = status
            // Lists cut long posts short, and the chain quotes all of it.
            if (status.retweeted != null && status.plainText == null) {
                quoted =
                    try {
                        graph.api.fetchStatus(status.id)
                    } catch (e: WeiboException) {
                        launch { snackbars.showError(e, navigator) }
                        return@launch
                    }
            }
            val sent =
                overlays.show<Sent> { close -> RepostSheet(status, quoteOf(quoted), close) }
                    ?: return@launch
            sent.repost?.let { repost ->
                graph.openLists.prependOwn(repost)
                graph.openLists.reposts.forEach(status.id) { it.prepend(repost) }
            }
            snackbars.showMessage("转发成功", graph.appScope)
            onReposted()
        }
    }

    /**
     * Offers to delete [status], which the user posted, and asks again before it goes. Once deleted
     * it leaves the open lists of the user's posts, and [onDeleted] is called, e.g. to leave its
     * detail page.
     */
    fun delete(status: Status, onDeleted: () -> Unit = {}) {
        if (status.id in deleting) return
        scope.launch {
            if (!overlays.confirmDelete("确定删除这条微博吗？")) return@launch
            if (!deleting.add(status.id)) return@launch
            try {
                graph.api.deleteStatus(status.id)
            } catch (e: WeiboException) {
                launch { snackbars.showError(e, navigator) }
                return@launch
            } finally {
                deleting -= status.id
            }
            graph.openLists.removeOwn(status)
            snackbars.showMessage("已删除", graph.appScope)
            onDeleted()
        }
    }

    private companion object {
        // Statuses being deleted, so a second tap doesn't ask Weibo again.
        val deleting = mutableSetOf<String>()
    }
}

/** [StatusWrites] for this page, its work cancelled as the page goes. */
@Composable
fun rememberStatusWrites(): StatusWrites {
    val graph = LocalAppGraph.current
    val overlays = LocalOverlays.current
    val snackbars = LocalSnackbars.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    return remember(graph, overlays, snackbars, navigator, scope) {
        StatusWrites(graph, overlays, snackbars, navigator, scope)
    }
}

/** What the repost sheet closes with once sent: the repost, if Weibo answered with it. */
private class Sent(val repost: Status?)

/** The chain a repost of [status] carries on, empty if it isn't a repost. */
private fun quoteOf(status: Status): String {
    val name = status.user?.screenName
    val text = status.plainText
    if (status.retweeted == null || name == null || text == null) return ""
    return "//@$name:$text"
}

/** @param quote what the text starts as, when there is no draft; the cursor goes before it. */
@Composable
private fun RepostSheet(status: Status, quote: String, close: (Sent?) -> Unit) {
    val graph = LocalAppGraph.current
    val navigator = LocalNavigator.current
    val scope = rememberCoroutineScope()
    var text by remember {
        mutableStateOf(TextFieldValue(graph.repostDrafts[status.id] ?: quote, TextRange(0)))
    }
    var visibility by remember { mutableStateOf(StatusVisibility.Public) }
    var alsoComment by remember { mutableStateOf(false) }
    var sending by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<WeiboException?>(null) }

    OverlaySheet(close, canDismiss = !sending) { dismiss ->
        fun send() = scope.launch {
            sending = true
            error = null
            try {
                val repost =
                    graph.api.repostStatus(
                        id = status.id,
                        text = text.text.trim(),
                        alsoComment = alsoComment,
                        visibility = visibility,
                    )
                graph.repostDrafts.remove(status.id)
                sending = false
                dismiss(Sent(repost))
            } catch (e: WeiboException) {
                error = e
                sending = false
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) {
            val focus = remember { FocusRequester() }
            OutlinedTextField(
                text,
                {
                    text = it
                    graph.repostDrafts[status.id] = it.text
                },
                Modifier.fillMaxWidth().focusRequester(focus),
                enabled = !sending,
                // Weibo puts in 转发微博 when there is nothing.
                placeholder = { Text("说说分享心得…") },
                minLines = 3,
                maxLines = 6,
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
            error?.let { e ->
                SendError(
                    e,
                    onVerify = { url ->
                        dismiss(null)
                        navigator.push(Route.Web(url))
                    },
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                FlowRow(
                    Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    itemVerticalAlignment = Alignment.CenterVertically,
                ) {
                    VisibilityButton(
                        visibility,
                        onChange = if (sending) null else ({ visibility = it }),
                    )
                    Row(
                        Modifier.clip(RoundedCornerShape(4.dp))
                            .clickable(enabled = !sending, role = Role.Checkbox) {
                                alsoComment = !alsoComment
                            }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(checked = alsoComment, onCheckedChange = null, enabled = !sending)
                        Spacer(Modifier.width(4.dp))
                        Text("同时评论")
                    }
                }
                Spacer(Modifier.width(8.dp))
                SendButton("转发", sending = sending, enabled = true) { send() }
            }
        }
    }
}
