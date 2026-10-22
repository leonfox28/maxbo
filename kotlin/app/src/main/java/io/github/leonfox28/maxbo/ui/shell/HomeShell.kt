package io.github.leonfox28.maxbo.ui.shell

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateDpAsState
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.unit.dp
import io.github.leonfox28.maxbo.ui.profile.MePage
import io.github.leonfox28.maxbo.ui.theme.UnreadColor
import io.github.leonfox28.maxbo.ui.widget.FloatingPill
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
    Scaffold(
        // Lists scroll on behind the pill; the padding below makes room for it.
        bottomBar = {
            FloatingNavBar(
                selected = Tab.entries[current],
                alerts = emptySet(),
                onSelected = { current = it.ordinal },
            )
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        val tab = Tab.entries[current]
        tabs.SaveableStateProvider(tab) { TabPage(tab, padding) }
    }
}

@Composable
private fun TabPage(tab: Tab, padding: PaddingValues) {
    when (tab) {
        Tab.Home -> PlaceholderTab(tab.label, padding)
        Tab.Search -> PlaceholderTab(tab.label, padding)
        Tab.Notifications -> PlaceholderTab(tab.label, padding)
        Tab.Me -> MePage(padding)
    }
}

@Composable
private fun FloatingNavBar(selected: Tab, alerts: Set<Tab>, onSelected: (Tab) -> Unit) {
    // As wide as its items, not the screen.
    FloatingPill {
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
