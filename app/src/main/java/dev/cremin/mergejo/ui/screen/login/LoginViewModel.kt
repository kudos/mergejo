package dev.cremin.mergejo.ui.screen.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.mergejo.BuildConfig
import dev.cremin.mergejo.data.auth.AuthManager
import dev.cremin.mergejo.data.remote.api.ForgejoApi
import java.io.IOException
import java.net.UnknownHostException
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

data class LoginUiState(
    val serverUrl: String = "",
    val token: String = "",
    val loading: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val api: ForgejoApi,
) : ViewModel() {

    private val _state = MutableStateFlow(
        LoginUiState(serverUrl = authManager.serverUrl ?: BuildConfig.FORGEJO_BASE_URL),
    )
    val state = _state.asStateFlow()

    fun onServerUrlChange(value: String) = _state.update { it.copy(serverUrl = value, error = null) }
    fun onTokenChange(value: String) = _state.update { it.copy(token = value, error = null) }

    /** Access-tokens page on the entered server, for the "create a token" link. */
    fun tokenPageUrl(): String = normalizeUrl(_state.value.serverUrl) + "/user/settings/applications"

    fun signIn() {
        val url = normalizeUrl(_state.value.serverUrl)
        val token = _state.value.token.trim()
        if (url.isBlank() || token.isBlank()) {
            _state.update { it.copy(error = "Enter a server URL and access token") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authManager.stage(url, token)
            runCatching { api.getAuthenticatedUser() }
                .onSuccess { authManager.confirm() } // isAuthorized flips -> AppNavHost navigates
                .onFailure { e ->
                    authManager.rollback()
                    _state.update { it.copy(loading = false, error = e.toLoginError()) }
                }
        }
    }

    private fun normalizeUrl(raw: String): String {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isEmpty()) return ""
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            trimmed
        } else {
            "https://$trimmed"
        }
    }

    private fun Throwable.toLoginError(): String = when (this) {
        is HttpException -> if (code() == 401) "Invalid or expired token" else "Server error (HTTP ${code()})"
        is UnknownHostException -> "Can't reach that server"
        is IOException -> "Network error — check the server URL"
        else -> message ?: "Sign-in failed"
    }
}
