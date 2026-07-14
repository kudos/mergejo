package dev.cremin.gitops.data.auth

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Holds the current Forgejo credentials (server URL + personal access token) and
 * whether we're signed in. The token is sent as `Authorization: token <token>`
 * by [AuthInterceptor]; the server URL rewrites the API host at request time, so
 * any Forgejo instance works without rebuilding.
 *
 * Sign-in is two-phase: [stage] the entered credentials so a validation call can
 * use them, then [confirm] on success (persists + flips [isAuthorized]) or
 * [rollback] on failure.
 */
@Singleton
class AuthManager @Inject constructor(
    private val store: CredentialStore,
) {
    @Volatile
    private var _serverUrl: String? = store.serverUrl

    @Volatile
    private var _token: String? = store.token

    val serverUrl: String? get() = _serverUrl
    val token: String? get() = _token

    private val _isAuthorized = MutableStateFlow(_serverUrl != null && _token != null)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    /** Apply entered credentials so the API client can make a validation request. */
    fun stage(serverUrl: String, token: String) {
        _serverUrl = serverUrl.trimEnd('/')
        _token = token
    }

    /** Persist the staged credentials and mark the session authorized. */
    fun confirm() {
        val url = _serverUrl ?: return
        val token = _token ?: return
        store.save(url, token)
        _isAuthorized.value = true
    }

    /** Discard staged credentials after a failed validation, restoring the stored ones. */
    fun rollback() {
        _serverUrl = store.serverUrl
        _token = store.token
    }

    fun signOut() {
        store.clear()
        _serverUrl = null
        _token = null
        _isAuthorized.value = false
    }
}
