package io.github.leonfox28.maxbo.ui.profile

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.paging.PagedController
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Paged
import io.github.leonfox28.maxbo.data.model.Status
import io.github.leonfox28.maxbo.data.model.UserProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The statuses of user [uid], paged by number. */
class UserStatusesController(private val api: WeiboApi, val uid: String, scope: CoroutineScope) :
    PagedController<Status>(scope) {
    override suspend fun fetchPage(cursor: String?): Paged<Status> =
        api.fetchUserStatuses(uid = uid, page = cursor?.toInt() ?: 1)

    override fun idOf(item: Status) = item.id
}

/** The profile as loaded, or why it couldn't be. */
data class ProfileLoad(val profile: UserProfile? = null, val error: WeiboException? = null)

/**
 * What a profile page holds: the user with [uid], or else the one named [screenName], and their
 * statuses. Opened by uid it loads both at once; by name only, the statuses wait for the profile.
 */
class ProfileViewModel(
    private val uid: String?,
    private val screenName: String?,
    private val api: WeiboApi,
) : ViewModel() {
    private val _profile = MutableStateFlow(ProfileLoad())
    val profile: StateFlow<ProfileLoad> = _profile.asStateFlow()

    var statuses by mutableStateOf<UserStatusesController?>(null)
        private set

    init {
        uid?.let(::startStatuses)
        reload()
    }

    fun reload() {
        _profile.update { it.copy(error = null) }
        viewModelScope.launch {
            try {
                val profile = api.fetchProfile(uid = uid, screenName = screenName)
                _profile.value = ProfileLoad(profile)
                if (statuses == null) startStatuses(profile.user.id)
            } catch (e: WeiboException) {
                _profile.update { it.copy(error = e) }
            }
        }
    }

    private fun startStatuses(uid: String) {
        statuses = UserStatusesController(api, uid, viewModelScope).also { it.start() }
    }

    /**
     * Loads the profile and the statuses again.
     *
     * @throws WeiboException on failure, what was shown staying, for the caller to report.
     */
    suspend fun refresh() = coroutineScope {
        listOf(
                async {
                    _profile.value =
                        ProfileLoad(api.fetchProfile(uid = uid, screenName = screenName))
                },
                async { statuses?.refresh() },
            )
            .awaitAll()
        Unit
    }
}
