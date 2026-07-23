package dev.cremin.mergejo.ui.screen.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.mergejo.BuildConfig
import dev.cremin.mergejo.data.auth.AuthManager
import dev.cremin.mergejo.data.preferences.SettingsRepository
import dev.cremin.mergejo.data.remote.api.ForgejoApi
import dev.cremin.mergejo.domain.model.MergeStyle
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val userLogin: String? = null,
    val defaultMergeStyle: MergeStyle = MergeStyle.SQUASH,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authManager: AuthManager,
    private val settings: SettingsRepository,
    private val api: ForgejoApi,
) : ViewModel() {

    val serverUrl: String = authManager.serverUrl ?: BuildConfig.FORGEJO_BASE_URL
    val versionName: String = BuildConfig.VERSION_NAME

    private val userLogin = MutableStateFlow<String?>(null)

    val uiState: StateFlow<SettingsUiState> = combine(
        userLogin,
        settings.defaultMergeStyle,
    ) { user, style ->
        SettingsUiState(userLogin = user, defaultMergeStyle = style)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    init {
        viewModelScope.launch {
            runCatching { api.getAuthenticatedUser() }.onSuccess { userLogin.value = it.login }
        }
    }

    fun setMergeStyle(style: MergeStyle) {
        viewModelScope.launch { settings.setDefaultMergeStyle(style) }
    }

    fun signOut() {
        authManager.signOut()
    }
}
