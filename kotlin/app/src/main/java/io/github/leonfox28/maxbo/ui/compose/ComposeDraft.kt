package io.github.leonfox28.maxbo.ui.compose

import io.github.leonfox28.maxbo.core.session.SessionController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.StatusVisibility
import io.github.leonfox28.maxbo.ui.interactions.OpenLists
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How many pictures a post can have. */
const val MAX_DRAFT_PICTURES = 9

/** A picture picked for a new post or comment, by its URI; [pid] once uploaded. */
data class DraftPicture(val uri: String, val pid: String? = null)

/** The post the user is writing. */
data class ComposeDraft(
    val text: String = "",
    val pictures: List<DraftPicture> = emptyList(),
    val visibility: StatusVisibility = StatusVisibility.Public,
) {
    /** Weibo wants some text, pictures or not, as weibo.com's composer does. */
    val canSend: Boolean
        get() = text.isNotBlank()
}

/**
 * The draft of a new post. It outlives the compose page, so closing it loses nothing, and starts
 * over for another account.
 */
class ComposeDraftController(
    private val api: WeiboApi,
    private val pictures: PictureReader,
    private val openLists: OpenLists,
    session: SessionController,
    scope: CoroutineScope,
) {
    private val _draft = MutableStateFlow(ComposeDraft())
    val draft: StateFlow<ComposeDraft> = _draft.asStateFlow()

    /** Bumped when the draft starts over, so a send for the last account changes nothing. */
    private var generation = 0

    init {
        scope.launch {
            session.uid.drop(1).collect {
                generation++
                _draft.value = ComposeDraft()
            }
        }
    }

    fun setText(text: String) = _draft.update { it.copy(text = text) }

    fun setVisibility(visibility: StatusVisibility) = _draft.update {
        it.copy(visibility = visibility)
    }

    /** Adds the pictures at [uris], as many as fit, and returns how many were left out. */
    fun addPictures(uris: List<String>): Int {
        val room = MAX_DRAFT_PICTURES - _draft.value.pictures.size
        _draft.update { it.copy(pictures = it.pictures + uris.take(room).map(::DraftPicture)) }
        return max(0, uris.size - room)
    }

    fun removePicture(index: Int) = _draft.update {
        it.copy(pictures = it.pictures.filterIndexed { i, _ -> i != index })
    }

    /**
     * Uploads the pictures not yet uploaded, one at a time, then posts the draft and clears it.
     * [onUpload] hears which picture (from 0) goes up.
     *
     * The new status goes atop the open lists that show the user's posts. Throws a `WeiboException`
     * on failure; the pictures uploaded by then keep their pids, so sending again doesn't upload
     * them again.
     */
    suspend fun send(onUpload: (index: Int) -> Unit = {}) {
        val generation = generation
        val pids = mutableListOf<String>()
        for ((i, picture) in _draft.value.pictures.withIndex()) {
            val pid =
                picture.pid
                    ?: run {
                        onUpload(i)
                        val pid = api.uploadPicture(pictures.read(picture.uri))
                        if (generation != this.generation) return
                        uploaded(picture, pid)
                        pid
                    }
            pids += pid
        }
        val draft = _draft.value
        val status =
            api.postStatus(text = draft.text.trim(), pids = pids, visibility = draft.visibility)
        if (generation != this.generation) return
        _draft.value = ComposeDraft()
        status?.let(openLists::prependOwn)
    }

    private fun uploaded(picture: DraftPicture, pid: String) = _draft.update { draft ->
        draft.copy(pictures = draft.pictures.map { if (it === picture) it.copy(pid = pid) else it })
    }
}
