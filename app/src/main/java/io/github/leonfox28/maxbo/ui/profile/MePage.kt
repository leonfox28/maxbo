package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.BuildConfig
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.data.model.Account
import io.github.leonfox28.maxbo.data.model.WeiboUser
import io.github.leonfox28.maxbo.ui.auth.LoginPrompt
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.navigation.Route
import io.github.leonfox28.maxbo.ui.widget.AppAlertDialog
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import kotlinx.coroutines.launch

// No app bar: the tab bar names the tab, and what an app bar would offer is
// listed here.
@Composable
fun MePage(padding: PaddingValues) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    BoxWithConstraints(
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    ) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = readingPadding(maxWidth, padding)) {
            when (val state = session) {
                // Settings stay within reach before logging in.
                is SessionState.SignedOut -> {
                    item { LoginPrompt(state.expired, Modifier.fillMaxWidth()) }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item { AppLinks() }
                }
                is SessionState.SignedIn -> {
                    item { AccountItem(state.account) }
                    item { HorizontalDivider(Modifier.padding(vertical = 8.dp)) }
                    item { AppLinks() }
                    item { LogoutItem() }
                }
            }
        }
    }
}

@Composable
private fun AccountItem(account: Account) {
    val navigator = LocalNavigator.current
    ListItem(
        modifier =
            Modifier.clickable {
                navigator.push(
                    Route.Profile(
                        account.uid,
                        account.screenName?.let { WeiboUser(account.uid, it) },
                    )
                )
            },
        headlineContent = { Text(account.screenName ?: "微博用户") },
        supportingContent = { Text("UID ${account.uid}") },
        leadingContent = {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ) {
                Box(Modifier.size(40.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Person, contentDescription = null)
                }
            }
        },
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
    )
}

/** Settings, and diagnostics in debug builds. */
@Composable
private fun AppLinks() {
    val navigator = LocalNavigator.current
    LinkItem("设置", { Icon(Icons.Outlined.Settings, contentDescription = null) }) {
        navigator.push(Route.Settings)
    }
    if (BuildConfig.DEBUG) {
        LinkItem("开发者诊断", { Icon(Icons.Outlined.BugReport, contentDescription = null) }) {
            navigator.push(Route.Diagnostics)
        }
    }
}

@Composable
private fun LinkItem(title: String, icon: @Composable () -> Unit, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = icon,
        trailingContent = {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        },
    )
}

@Composable
private fun LogoutItem() {
    val session = LocalAppGraph.current.session
    val scope = rememberCoroutineScope()
    var confirming by remember { mutableStateOf(false) }
    ListItem(
        headlineContent = { Text("退出登录") },
        modifier = Modifier.clickable { confirming = true },
        leadingContent = { Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null) },
    )
    if (confirming) {
        AppAlertDialog(
            onDismissRequest = { confirming = false },
            title = { Text("退出登录") },
            text = { Text("将清除本机保存的微博登录状态。") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirming = false
                        scope.launch { session.logout() }
                    }
                ) {
                    Text("退出")
                }
            },
            dismissButton = { TextButton(onClick = { confirming = false }) { Text("取消") } },
        )
    }
}
