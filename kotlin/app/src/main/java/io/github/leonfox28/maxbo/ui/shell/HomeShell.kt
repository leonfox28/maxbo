package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.ui.notifications.NotificationsPage
import io.github.leonfox28.maxbo.ui.notifications.UNREAD_RECHECK_MS
import io.github.leonfox28.maxbo.ui.profile.MePage
import io.github.leonfox28.maxbo.ui.search.SearchPage
import io.github.leonfox28.maxbo.ui.theme.UnreadColor
import io.github.leonfox28.maxbo.ui.timeline.TimelinePage
import io.github.leonfox28.maxbo.ui.widget.AppSnackbarHost
import io.github.leonfox28.maxbo.ui.widget.BarsOnScroll
import io.github.leonfox28.maxbo.ui.widget.FloatingPill
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch

private enum class Tab(val icon: ImageVector, val selectedIcon: ImageVector, val label: String) {
    Home(Icons.Outlined.Home, Icons.Filled.Home, "首页"),
    Search(Icons.Outlined.Search, Icons.Filled.Search, "搜索"),
    Notifications(Icons.Outlined.Notifications, Icons.Filled.Notifications, "通知"),
    Me(Icons.Outlined.Person, Icons.Filled.Person, "我"),
}

/**
 * Hosts the top-level tabs, switched by a floating pill. Each tab keeps its state while another is
 * open, as the Flutter app's IndexedStack did.
 */
@Composable
fun HomeShell() {
    var current by rememberSaveable { mutableIntStateOf(Tab.Home.ordinal) }
    val tabs = rememberSaveableStateHolder()
    val threshold = with(LocalDensity.current) { BarsOnScroll.Threshold.toPx() }
    val bars = remember(threshold) { BarsOnScroll(threshold) }
    val reselects = remember {
        Tab.entries.map { MutableSharedFlow<Unit>(extraBufferCapacity = 1) }
    }
    val hidden by
        animateFloatAsState(if (bars.visible) 0f else 1f, BarsOnScroll.spec(), label = "pill")
    val graph = LocalAppGraph.current
    val unread by graph.unread.state.collectAsStateWithLifecycle()
    UnreadChecks()
    Scaffold(
        Modifier.nestedScroll(bars.connection),
        // Lists scroll on behind the pill; the padding below makes room for it.
        bottomBar = {
            FloatingNavBar(
                selected = Tab.entries[current],
                alerts = if (unread.any) setOf(Tab.Notifications) else emptySet(),
                onSelected = {
                    bars.show()
                    if (it.ordinal == current) reselects[current].tryEmit(Unit)
                    else current = it.ordinal
                },
                modifier = Modifier.graphicsLayer { translationY = hidden * 1.5f * size.height },
            )
        },
        snackbarHost = { AppSnackbarHost() },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val tab = Tab.entries[current]
        CompositionLocalProvider(LocalTabReselect provides reselects[tab.ordinal]) {
            tabs.SaveableStateProvider(tab) { TabPage(tab, padding) }
        }
    }
}

/**
 * Checks for unread notifications when the user signs in or opens the app, and when they come back
 * to it (at most once a minute), never in between (PLAN §2).
 */
@Composable
private fun UnreadChecks() {
    val graph = LocalAppGraph.current
    val session by graph.session.state.collectAsStateWithLifecycle()
    val uid = (session as? SessionState.SignedIn)?.account?.uid
    LaunchedEffect(uid) { if (uid != null) graph.unread.check() }
    // Coming back to the app, not opening it: that is the check above.
    var away by remember { mutableStateOf(false) }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) { away = true }
    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        if (away) graph.unread.requestCheck(UNREAD_RECHECK_MS)
        away = false
    }
}

@Composable
private fun TabPage(tab: Tab, padding: PaddingValues) {
    when (tab) {
        Tab.Home -> TimelinePage(padding)
        Tab.Search -> SearchPage(padding)
        Tab.Notifications -> NotificationsPage(padding)
        Tab.Me -> MePage(padding)
    }
}

@Composable
private fun FloatingNavBar(
    selected: Tab,
    alerts: Set<Tab>,
    onSelected: (Tab) -> Unit,
    modifier: Modifier = Modifier,
) {
    // As wide as its items, not the screen.
    FloatingPill(modifier) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            for (tab in Tab.entries) {
                NavItem(
                    tab,
                    selected = tab == selected,
                    alert = tab in alerts,
                    onClick = { onSelected(tab) },
                )
            }
        }
    }
}

private const val ITEM_DURATION = 250
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * An icon, which when selected widens into a chip with its label. With [alert], something new is
 * there: the icon turns red, except on the selected chip, where red would hardly show against the
 * primary colour. A long press shows the label, as a tooltip that screen readers skip: they read
 * the label anyway.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun NavItem(tab: Tab, selected: Boolean, alert: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val foreground = if (selected) colors.onPrimary else colors.onSurfaceVariant
    val background by
        animateColorAsState(
            if (selected) colors.primary else Color.Transparent,
            tween(ITEM_DURATION, easing = EaseOutCubic),
        )
    val horizontal by
        animateDpAsState(
            if (selected) 20.dp else 16.dp,
            tween(ITEM_DURATION, easing = EaseOutCubic),
        )
    val tooltip = rememberTooltipState()
    val scope = rememberCoroutineScope()
    TooltipBox(
        positionProvider =
            TooltipDefaults.rememberTooltipPositionProvider(TooltipAnchorPosition.Above),
        tooltip = { PlainTooltip { Text(tab.label) } },
        state = tooltip,
        focusable = false,
        enableUserInput = false,
    ) {
        Row(
            Modifier.height(48.dp)
                .clip(CircleShape)
                .background(background)
                .clearAndSetSemantics {
                    contentDescription = if (alert) "${tab.label}，有未读" else tab.label
                    role = Role.Tab
                    this.selected = selected
                    onClick {
                        onClick()
                        true
                    }
                    // Screen readers offer no click on a selected tab, so a
                    // second tap (back to the top, or a refresh) is an action.
                    if (selected) {
                        customActions =
                            listOf(
                                CustomAccessibilityAction("回到顶部或刷新") {
                                    onClick()
                                    true
                                }
                            )
                    }
                }
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { scope.launch { tooltip.show() } },
                )
                .padding(horizontal = horizontal),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (selected) tab.selectedIcon else tab.icon,
                contentDescription = null,
                tint = if (alert && !selected) UnreadColor else foreground,
            )
            AnimatedVisibility(
                visible = selected,
                enter = expandHorizontally(tween(ITEM_DURATION, easing = EaseOutCubic)) + fadeIn(),
                exit = shrinkHorizontally(tween(ITEM_DURATION, easing = EaseOutCubic)) + fadeOut(),
            ) {
                Text(
                    tab.label,
                    modifier = Modifier.padding(start = 8.dp),
                    color = foreground,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}
