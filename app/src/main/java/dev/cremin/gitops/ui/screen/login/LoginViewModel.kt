package dev.cremin.gitops.ui.screen.login

import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.gitops.BuildConfig
import dev.cremin.gitops.data.auth.AuthManager
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val loading: Boolean = false,
    val error: String? = null,
    val launchIntent: Intent? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authManager: AuthManager,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state = _state.asStateFlow()

    val serverUrl: String = BuildConfig.FORGEJO_BASE_URL
    val clientIdConfigured: Boolean = BuildConfig.OAUTH_CLIENT_ID.isNotBlank()

    fun startSignIn() {
        if (!clientIdConfigured) return
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching {
                authManager.authorizationRequestIntent(
                    issuer = BuildConfig.FORGEJO_BASE_URL,
                    clientId = BuildConfig.OAUTH_CLIENT_ID,
                    redirectUri = BuildConfig.OAUTH_REDIRECT_URI,
                    scopes = BuildConfig.OAUTH_SCOPES,
                )
            }.onSuccess { intent ->
                _state.update { it.copy(launchIntent = intent) }
            }.onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Sign-in failed") }
            }
        }
    }

    fun onLaunchHandled() {
        _state.update { it.copy(launchIntent = null) }
    }

    fun onAuthResult(data: Intent?) {
        if (data == null) {
            _state.update { it.copy(loading = false) }
            return
        }
        viewModelScope.launch {
            authManager.handleAuthorizationResponse(data).onFailure { e ->
                _state.update { it.copy(loading = false, error = e.message ?: "Sign-in failed") }
            }
            // On success, AuthManager.isAuthorized flips and AppNavHost navigates away.
        }
    }
}
