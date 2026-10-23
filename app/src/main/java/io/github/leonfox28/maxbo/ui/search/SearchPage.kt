package io.github.leonfox28.maxbo.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.clearText
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VerticalAlignTop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.leonfox28.maxbo.LocalAppGraph
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.session.SessionState
import io.github.leonfox28.maxbo.core.util.formatCount
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.HotSearch
import io.github.leonfox28.maxbo.data.model.HotSearchItem
import io.github.leonfox28.maxbo.ui.auth.LoginPrompt
import io.github.leonfox28.maxbo.ui.navigation.LocalNavigator
import io.github.leonfox28.maxbo.ui.shell.LocalTabReselect
import io.github.leonfox28.maxbo.ui.status.openSearchResults
import io.github.leonfox28.maxbo.ui.widget.ErrorRetry
import io.github.leonfox28.maxbo.ui.widget.LocalSnackbars
import io.github.leonfox28.maxbo.ui.widget.ReadingWidth
import io.github.leonfox28.maxbo.ui.widget.TagLabel
import io.github.leonfox28.maxbo.ui.widget.readingPadding
import io.github.leonfox28.maxbo.ui.widget.showError
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The hot search list as loaded, or why it couldn't be. */
data class HotLoad(val hot: HotSearch? = null, val error: WeiboException? = null)

/** Loaded when the tab is first opened, and again only on pull to refresh. */
class HotSearchViewModel(private val api: WeiboApi) : ViewModel() {
    private val _state = MutableStateFlow(HotLoad())
    val state: StateFlow<HotLoad> = _state.asStateFlow()

    init {
        reload()
    }

    fun reload() {
        _state.value = HotLoad()
        viewModelScope.launch {
            try {
                _state.value = HotLoad(api.fetchHotSearch())
            } catch (e: WeiboException) {
                _state.update { it.copy(error = e) }
            }
        }
    }

    /** @throws WeiboException on failure, the list staying as it was. */
    suspend fun refresh() {
        _state.value = HotLoad(api.fetchHotSearch())
    }
}

/**
 * The search tab: a search bar over the hot search list. A search, as a hot search entry, opens the
 * posts found for it. No app bar: the tab bar names the tab.
 */
@Composable
fun SearchPage(padding: PaddingValues) {
    val session by LocalAppGraph.current.session.state.collectAsStateWithLifecycle()
    val area =
        Modifier.fillMaxSize()
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
            )
    when (val state = session) {
        is SessionState.SignedOut -> LoginPrompt(state.expired, area)
        is SessionState.SignedIn ->
            Column(area) {
                SearchField()
                Box(Modifier.weight(1f)) { HotSearchList(padding) }
            }
    }
}

@Composable
private fun SearchField() {
    val navigator = LocalNavigator.current
    val focus = LocalFocusManager.current
    val text = rememberTextFieldState()
    val colors = MaterialTheme.colorScheme
    fun search() {
        val query = text.text.toString().trim()
        if (query.isEmpty()) return
        // Or the keyboard comes back with the field when the results close.
        focus.clearFocus()
        openSearchResults(navigator, query)
    }
    // Over the list's column, not spread across a wide window.
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Surface(
            Modifier.widthIn(max = ReadingWidth)
                .fillMaxWidth()
                .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 4.dp),
            shape = CircleShape,
            color = colors.surfaceContainerHigh,
        ) {
            Row(
                Modifier.height(56.dp).padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Filled.Search,
                    contentDescription = null,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
                BasicTextField(
                    text,
                    Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = colors.onSurface),
                    lineLimits = TextFieldLineLimits.SingleLine,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    onKeyboardAction = { search() },
                    cursorBrush = SolidColor(colors.primary),
                    decorator = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (text.text.isEmpty()) {
                                Text(
                                    "搜索微博",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = colors.onSurfaceVariant,
                                )
                            }
                            field()
                        }
                    },
                )
                if (text.text.isNotEmpty()) {
                    IconButton(onClick = { text.clearText() }) {
                        Icon(Icons.Filled.Close, contentDescription = "清除")
                    }
                } else {
                    Spacer(Modifier.width(12.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HotSearchList(padding: PaddingValues) {
    val api = LocalAppGraph.current.api
    val model = viewModel { HotSearchViewModel(api) }
    val load by model.state.collectAsStateWithLifecycle()
    val navigator = LocalNavigator.current
    val snackbars = LocalSnackbars.current
    val focus = LocalFocusManager.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    var refreshing by remember { mutableStateOf(false) }
    fun refresh() {
        if (refreshing) return
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
    }
    val reselects = LocalTabReselect.current
    LaunchedEffect(reselects) {
        reselects.collect {
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                listState.animateScrollToItem(0)
            } else {
                refresh()
            }
        }
    }
    val hot = load.hot
    if (hot == null) {
        val error = load.error
        if (error != null) {
            ErrorRetry(error, model::reload, Modifier.fillMaxSize())
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        return
    }
    // Dragging the list puts the keyboard away.
    val dismissKeyboard = remember {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (source == NestedScrollSource.UserInput) focus.clearFocus()
                return Offset.Zero
            }
        }
    }
    PullToRefreshBox(isRefreshing = refreshing, onRefresh = ::refresh) {
        BoxWithConstraints(Modifier.fillMaxSize().nestedScroll(dismissKeyboard)) {
            LazyColumn(
                Modifier.fillMaxSize(),
                state = listState,
                contentPadding = readingPadding(maxWidth, padding),
            ) {
                items(hot.pinned) { HotSearchTile(it, pinned = true) }
                items(hot.items) { HotSearchTile(it) }
            }
        }
    }
}

// Weibo's colours for the top three.
private val TopColors = listOf(Color(0xFFF5222D), Color(0xFFFF6A00), Color(0xFFFAA90E))

@Composable
private fun HotSearchTile(item: HotSearchItem, pinned: Boolean = false) {
    val navigator = LocalNavigator.current
    val focus = LocalFocusManager.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val rank = item.rank
    Row(
        Modifier.fillMaxWidth()
            .clickable {
                focus.clearFocus()
                openSearchResults(navigator, item.query)
            }
            .padding(start = 12.dp, top = 13.dp, end = 16.dp, bottom = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(32.dp), contentAlignment = Alignment.Center) {
            when {
                pinned ->
                    Icon(
                        Icons.Filled.VerticalAlignTop,
                        null,
                        Modifier.size(20.dp),
                        tint = TopColors[0],
                    )
                rank != null ->
                    Text(
                        "$rank",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (rank <= 3) TopColors[rank - 1] else muted,
                    )
                // Inserted by Weibo outside the ranking.
                else ->
                    Icon(
                        Icons.AutoMirrored.Filled.TrendingUp,
                        null,
                        Modifier.size(18.dp),
                        tint = muted,
                    )
            }
        }
        Spacer(Modifier.width(8.dp))
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Text(
                item.word,
                modifier = Modifier.weight(1f, fill = false),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            item.badge?.let {
                Spacer(Modifier.width(6.dp))
                TagLabel(it, color = item.badgeColor?.let { color -> Color(color) })
            }
        }
        val heat = item.heat
        if (heat != null && heat > 0) {
            Spacer(Modifier.width(12.dp))
            Text(formatCount(heat), style = MaterialTheme.typography.bodySmall, color = muted)
        }
    }
}
