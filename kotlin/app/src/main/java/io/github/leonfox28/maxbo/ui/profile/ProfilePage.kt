package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.media.WeiboImage
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.ListLoad
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.data.model.UserProfile
import io.github.leonfox28.maxbo.data.model.WeiboUser
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.status.rememberStatusCardActions
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BackButton
import io.github.leonfox28.maxbo.ui.widget.ErrorRetry
import io.github.leonfox28.maxbo.ui.widget.HidingAppBarBody
import io.github.leonfox28.maxbo.ui.widget.LoadMoreTrigger
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.TagLabel
import io.github.leonfox28.maxbo.ui.widget.UserAvatar
import io.github.leonfox28.maxbo.ui.widget.VerifiedIcon
import io.github.leonfox28.maxbo.ui.widget.loadErrorMessage
import io.github.leonfox28.maxbo.ui.widget.pagedItems
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import io.github.leonfox28.maxbo.ui.widget.showsVerified
import io.github.leonfox28.maxbo.ui.widget.status.StatusCard
import kotlinx.coroutines.launch

/**
 * A user's profile and statuses: the user with [uid], or else the one named [screenName].
 *
 * [initial] (the user as the previous page showed them) shows until the profile arrives.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfilePage(uid: String? = null, screenName: String? = null, initial: WeiboUser? = null) {
    val api = LocalAppGraph.current.api
    val model = viewModel { ProfileViewModel(uid, screenName, api) }
    val load by model.profile.collectAsStateWithLifecycle()
    val user = load.profile?.user ?: initial
    val statuses = model.statuses
    val title: @Composable () -> Unit = {
        Text(
            user?.screenName ?: screenName.orEmpty(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
    if (statuses == null) {
        Scaffold(topBar = { TopAppBar(title = title, navigationIcon = { BackButton() }) }) { padding
            ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                val error = load.error
                if (error != null) ErrorRetry(error, model::reload) else CircularProgressIndicator()
            }
        }
        return
    }
    Scaffold(snackbarHost = { AppSnackbarHost() }) { padding ->
        HidingAppBarBody(title = title) {
            ProfileView(model, statuses, load, initial, padding.calculateBottomPadding())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfileView(
    model: ProfileViewModel,
    statuses: UserStatusesController,
    load: ProfileLoad,
    initial: WeiboUser?,
    bottom: Dp,
) {
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val statusesLoad = key(statuses) { statuses.state.collectAsStateWithLifecycle().value }
    val base = rememberStatusCardActions()
    // This user's own avatar would only open this page again.
    val actions =
        remember(base, statuses.uid) {
            base.copy(onUser = { if (it.id != statuses.uid) base.onUser?.invoke(it) })
        }
    var refreshing by remember { mutableStateOf(false) }

    LoadMoreTrigger(
        listState,
        onNearEnd =
            if ((statusesLoad as? ListLoad.Ready)?.list?.loadMoreError == null)
                ({ scope.launch { statuses.loadMore() } })
            else null,
    )
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = {
            refreshing = true
            scope.launch {
                try {
                    model.refresh()
                } catch (e: WeiboException) {
                    launch { snackbars.showError(e, navigator) }
                } finally {
                    refreshing = false
                }
            }
        },
    ) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = readingPadding(maxWidth, PaddingValues(bottom = bottom)),
            ) {
                item(key = "header") { Header(load, initial, onRetry = model::reload) }
                // A line like those between the statuses.
                item(key = "line") { HorizontalDivider() }
                pagedItems(
                    statusesLoad,
                    key = { it.id },
                    onRetry = statuses::reload,
                    onLoadMore = { scope.launch { statuses.loadMore() } },
                    emptyText = "还没有微博",
                    dividerIndent = 0.dp,
                ) {
                    StatusCard(it, actions = actions)
                }
            }
        }
    }
}

@Composable
private fun Header(load: ProfileLoad, initial: WeiboUser?, onRetry: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val muted = colors.onSurfaceVariant
    val p = load.profile
    val failed = p == null && load.error != null
    val user = p?.user ?: initial
    val verified = user?.verifiedType
    val relation =
        when {
            p == null -> null
            p.following && p.followsMe -> "互相关注"
            p.following -> "已关注"
            p.followsMe -> "关注了你"
            else -> null
        }
    Column(Modifier.fillMaxWidth()) {
        p?.coverUrl?.let { WeiboImage(it, Modifier.fillMaxWidth().height(120.dp)) }
        Column(Modifier.padding(start = 16.dp, top = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                UserAvatar(user, size = 64.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (user != null) {
                            Text(
                                user.screenName,
                                modifier = Modifier.weight(1f, fill = false),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = typography.titleLarge,
                            )
                        } else if (!failed) {
                            LinePlaceholder(120.dp, typography.titleLarge)
                        }
                        if (showsVerified(verified)) {
                            Spacer(Modifier.width(4.dp))
                            VerifiedIcon(verified!!, size = 18.dp)
                        }
                        if (relation != null) {
                            Spacer(Modifier.width(8.dp))
                            TagLabel(relation)
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    if (p != null) Counts(p)
                    else if (!failed) LinePlaceholder(160.dp, typography.bodyMedium)
                }
            }
            p?.verifiedReason?.let {
                Spacer(Modifier.height(8.dp))
                Text("微博认证：$it", style = typography.bodySmall, color = muted)
            }
            p?.description?.let {
                Spacer(Modifier.height(8.dp))
                Text(it)
            }
            if (p != null && (p.gender != null || p.location != null)) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    when (p.gender) {
                        "m" ->
                            Icon(
                                Icons.Filled.Male,
                                null,
                                Modifier.size(16.dp),
                                tint = Color(0xFF42A5F5),
                            )
                        "f" ->
                            Icon(
                                Icons.Filled.Female,
                                null,
                                Modifier.size(16.dp),
                                tint = Color(0xFFF06292),
                            )
                    }
                    p.location?.let {
                        Spacer(Modifier.width(4.dp))
                        Text(it, style = typography.bodySmall, color = muted)
                    }
                }
            }
            if (p == null && !failed) {
                Spacer(Modifier.height(8.dp))
                LinePlaceholder(240.dp, typography.bodyMedium)
            }
            if (failed) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "资料加载失败：${loadErrorMessage(load.error)}",
                        modifier = Modifier.weight(1f),
                        style = typography.bodySmall,
                        color = colors.error,
                    )
                    TextButton(onClick = onRetry) { Text("重试") }
                }
            }
        }
    }
}

/**
 * A grey bar holding the place of a line of text in [style] until the profile arrives, so the
 * header keeps its height when it does.
 */
@Composable
private fun LinePlaceholder(width: Dp, style: TextStyle) {
    Box(Modifier.width(width)) {
        // Invisible, for the height of a line.
        Text(" ", style = style)
        Box(
            Modifier.matchParentSize()
                .padding(vertical = 3.dp)
                .background(
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    RoundedCornerShape(4.dp),
                )
        )
    }
}

/** Following, follower and status counts. Display only: follower lists aren't part of maxbo. */
@Composable
private fun Counts(profile: UserProfile) {
    val colors = MaterialTheme.colorScheme
    val counts =
        listOf(
                "关注" to profile.friendsCount,
                "粉丝" to profile.followersCount,
                "微博" to profile.statusesCount,
            )
            .filter { it.second != null }
    Text(
        buildAnnotatedString {
            counts.forEachIndexed { i, (label, count) ->
                if (i > 0) append("    ")
                withStyle(SpanStyle(color = colors.onSurface, fontWeight = FontWeight.SemiBold)) {
                    append(formatCount(count!!))
                }
                append(" $label")
            }
        },
        style = MaterialTheme.typography.bodyMedium,
        color = colors.onSurfaceVariant,
    )
}
