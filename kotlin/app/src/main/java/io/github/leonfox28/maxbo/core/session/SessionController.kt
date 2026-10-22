package io.github.leonfox28.maxbo.core.session

import io.github.leonfox28.maxbo.core.net.WeiboCookieJar
import io.github.leonfox28.maxbo.core.net.WeiboException
import io.github.leonfox28.maxbo.core.net.WeiboHttp
import io.github.leonfox28.maxbo.core.util.devLog
import io.github.leonfox28.maxbo.data.api.WeiboApi
import io.github.leonfox28.maxbo.data.model.Account
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SessionState {
    /** [expired]: Weibo ended the session, as opposed to the user signing out. */
    data class SignedOut(val expired: Boolean = false) : SessionState

    data class SignedIn(val account: Account) : SessionState
}

/**
 * Who is signed in, and the cookies every API request is sent with ([jar]).
 *
 * @param restored the session saved at the last launch, if any.
 * @param httpFor makes a client on a cookie jar, to check cookies before adopting them.
 * @param scope where saves run, outliving any page.
 * @param clock as [WeiboApi]'s, to tell which rejections came before a renewal.
 */
class SessionController(
    restored: StoredSession?,
    private val storage: SessionStorage,
    private val webCookies: WebCookies,
    private val renewer: SessionRenewer,
    private val httpFor: (WeiboCookieJar) -> WeiboHttp,
    private val scope: CoroutineScope,
    private val clock: () -> Long = System::nanoTime,
) {
    val jar = WeiboCookieJar(restored?.cookies.orEmpty())

    private val _state =
        MutableStateFlow<SessionState>(
            restored?.let { SessionState.SignedIn(it.account) } ?: SessionState.SignedOut()
        )
    val state: StateFlow<SessionState> = _state.asStateFlow()

    // One storage operation at a time, each saving the session as it is
    // then, so an older one can never overwrite a newer one.
    private val storing = Mutex()
    @Volatile private var lastSave: Job? = null

    private val renewalLock = Any()
    private var renewal: Deferred<Boolean>? = null
    private var renewedAt: Long? = null

    init {
        // Weibo rotates cookies (e.g. XSRF-TOKEN) on responses; keep the
        // stored copy current so the next launch starts with what the server
        // expects.
        jar.addListener { save() }
    }

    /**
     * Signs in with cookies taken from the login WebView.
     *
     * @throws io.github.leonfox28.maxbo.core.net.SessionExpiredException if the cookies don't
     *   belong to a signed-in user (visitors get a `SUB` cookie too), or another [WeiboException]
     *   if the check itself failed.
     */
    suspend fun completeLogin(cookies: Cookies) {
        val (account, verified) = verify(cookies)
        _state.value = SessionState.SignedIn(account)
        jar.replaceAll(verified)
        lastSave?.join()
    }

    suspend fun logout() {
        _state.value = SessionState.SignedOut()
        jar.clear()
        storing.withLock { storage.clear() }
        // Sign the embedded browser out too, so the next login starts fresh.
        webCookies.clearAll()
    }

    /**
     * Called when Weibo rejects the session of a request sent at [sentAt].
     *
     * Renews the session as a browser would (see [SessionRenewer]) and returns whether it is good
     * again; if it can't be, signs out. Rejections that come in together share one renewal, and one
     * for a session renewed since the request went out needs none.
     */
    suspend fun renewOrExpire(sentAt: Long): Boolean {
        if (_state.value !is SessionState.SignedIn) return false
        val pending =
            synchronized(renewalLock) {
                renewedAt?.let { if (it > sentAt) return true }
                renewal
                    ?: scope
                        .async { renew() }
                        .also { started ->
                            renewal = started
                            started.invokeOnCompletion {
                                synchronized(renewalLock) {
                                    if (renewal === started) renewal = null
                                }
                            }
                        }
            }
        return pending.await()
    }

    private suspend fun renew(): Boolean {
        val uid = (_state.value as? SessionState.SignedIn)?.account?.uid ?: return false
        var renewed = false
        try {
            renewed =
                renewer.renew(lapsedSub = jar.value(WeiboCookieJar.WEB, "SUB")) { cookies ->
                    try {
                        val (account, verified) = verify(cookies)
                        // The user may have signed out meanwhile.
                        if (account.uid != uid || _state.value !is SessionState.SignedIn) {
                            false
                        } else {
                            jar.replaceAll(verified)
                            synchronized(renewalLock) { renewedAt = clock() }
                            true
                        }
                    } catch (_: WeiboException) {
                        false
                    }
                }
        } catch (e: Exception) {
            devLog("session renewal: ${e.javaClass.simpleName}")
        }
        devLog("session renewal: ${if (renewed) "renewed" else "failed"}")
        if (!renewed) expire()
        return renewed
    }

    /**
     * Signs out after Weibo rejected the session for good.
     *
     * WebView cookies are kept: if the browser session is still alive there, opening the login page
     * signs the user straight back in.
     */
    fun expire() {
        if (_state.value !is SessionState.SignedIn) return
        _state.value = SessionState.SignedOut(expired = true)
        jar.clear()
        scope.launch { storing.withLock { storage.clear() } }
    }

    /**
     * Checks [cookies] against the API, returning the account they sign in as and the cookies with
     * any the server set meanwhile.
     */
    private suspend fun verify(cookies: Cookies): Pair<Account, Cookies> {
        val candidate = WeiboCookieJar(cookies)
        val account = WeiboApi(httpFor(candidate)).fetchCurrentAccount()
        return account to candidate.toMap()
    }

    private fun save() {
        lastSave = scope.launch {
            storing.withLock {
                val current = _state.value as? SessionState.SignedIn ?: return@withLock
                storage.save(StoredSession(current.account, jar.toMap()))
            }
        }
    }
}
