package io.github.leonfox28.maxbo.core.paging

import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.data.model.Paged
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PagedState<T>(
    val items: List<T> = emptyList(),
    /** Where the next page starts; null once the end is reached. */
    val nextCursor: String? = null,
    /** The size of the whole list, when Weibo says. */
    val total: Int? = null,
    val loadingMore: Boolean = false,
    /** Why the last attempt to load more failed; cleared by the next attempt. */
    val loadMoreError: WeiboException? = null,
) {
    val hasMore: Boolean
        get() = nextCursor != null

    /** This list with [items] in place of its own, [total] changing by [totalChange]. */
    fun withItems(items: List<T>, totalChange: Int = 0) =
        copy(items = items, total = total?.let { max(0, it + totalChange) })
}

/** A list's first page: loading, failed, or loaded. */
sealed interface ListLoad<out T> {
    data object Loading : ListLoad<Nothing>

    data class Failed(val error: WeiboException) : ListLoad<Nothing>

    data class Ready<T>(val list: PagedState<T>) : ListLoad<T>
}

/**
 * A list loaded a page at a time: the first page once [start]ed, as the list is first shown, the
 * next on [loadMore] as the user scrolls. Nothing loads on its own (PLAN §2).
 *
 * State changes happen on [scope]'s thread, the main one in the app.
 */
abstract class PagedController<T>(protected val scope: CoroutineScope) {
    private val _state = MutableStateFlow<ListLoad<T>>(ListLoad.Loading)
    val state: StateFlow<ListLoad<T>> = _state.asStateFlow()

    /** Fetches the page at [cursor], or the first page when null. */
    protected abstract suspend fun fetchPage(cursor: String?): Paged<T>

    /** Identifies [item], to drop repeats when pages overlap. */
    protected abstract fun idOf(item: T): String

    /** Bumped whenever the list is replaced, so pages loaded for an older one are dropped. */
    private var generation = 0
    private var firstPage: Job? = null

    /** Loads the first page, unless it has been. */
    fun start() {
        if (firstPage == null) reload()
    }

    /** Loads the first page again, showing it loading meanwhile; after a failure, say. */
    fun reload() {
        val generation = ++generation
        firstPage?.cancel()
        _state.value = ListLoad.Loading
        firstPage = scope.launch {
            val loaded =
                try {
                    ListLoad.Ready(fetchPage(null).toState())
                } catch (e: WeiboException) {
                    ListLoad.Failed(e)
                }
            if (generation == this@PagedController.generation) _state.value = loaded
        }
    }

    /** Forgets the list, as for another account; the next [start] loads it afresh. */
    protected fun reset(to: ListLoad<T> = ListLoad.Loading) {
        generation++
        firstPage?.cancel()
        firstPage = null
        _state.value = to
    }

    /**
     * Replaces the list with its first page, keeping it meanwhile.
     *
     * @throws WeiboException on failure, the list staying as it was, for the caller to report.
     */
    suspend fun refresh() {
        val generation = generation
        val page = fetchPage(null).toState()
        if (generation != this.generation) return
        this.generation++
        _state.value = ListLoad.Ready(page)
    }

    /**
     * Appends the next page. Failures are kept in [PagedState.loadMoreError] rather than thrown.
     */
    suspend fun loadMore() {
        val current = (_state.value as? ListLoad.Ready)?.list ?: return
        if (current.loadingMore || !current.hasMore) return
        _state.value = ListLoad.Ready(current.copy(loadingMore = true, loadMoreError = null))

        val generation = generation
        var page: Paged<T>? = null
        var error: WeiboException? = null
        try {
            page = fetchPage(current.nextCursor)
        } catch (e: WeiboException) {
            error = e
        }
        // Drop the result if a refresh replaced the list meanwhile; otherwise
        // add it to the list as it is now, which the user may have changed.
        if (generation != this.generation) return
        val latest = (_state.value as? ListLoad.Ready)?.list ?: return
        if (page == null) {
            _state.value = ListLoad.Ready(latest.copy(loadingMore = false, loadMoreError = error))
            return
        }
        val seen = latest.items.mapTo(HashSet(), ::idOf)
        _state.value =
            ListLoad.Ready(
                latest.copy(
                    items = latest.items + page.items.filter { seen.add(idOf(it)) },
                    nextCursor = page.nextCursor,
                    total = page.total ?: latest.total,
                    loadingMore = false,
                    loadMoreError = null,
                )
            )
    }

    /** Puts [item], which the user just added, atop the list. */
    fun prepend(item: T) = edit { list ->
        val id = idOf(item)
        list.withItems(listOf(item) + list.items.filter { idOf(it) != id }, totalChange = 1)
    }

    /** Takes the item [id], which the user just deleted, out of the list. */
    fun remove(id: String) = edit { list ->
        val items = list.items.filter { idOf(it) != id }
        if (items.size == list.items.size) list else list.withItems(items, totalChange = -1)
    }

    /** Replaces the item [id] with what [change] makes of it. */
    fun replace(id: String, change: (T) -> T) = edit { list ->
        list.withItems(list.items.map { if (idOf(it) == id) change(it) else it })
    }

    // Lists still loading their first page are left alone: that page will be
    // current anyway.
    private fun edit(change: (PagedState<T>) -> PagedState<T>) {
        val list = (_state.value as? ListLoad.Ready)?.list ?: return
        _state.value = ListLoad.Ready(change(list))
    }

    private fun Paged<T>.toState() =
        PagedState(items = items, nextCursor = nextCursor, total = total)
}
