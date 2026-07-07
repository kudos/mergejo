package dev.cremin.gitops.ui.navigation

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.gitops.data.auth.AuthManager
import javax.inject.Inject
import kotlinx.coroutines.flow.StateFlow

@HiltViewModel
class RootViewModel @Inject constructor(
    authManager: AuthManager,
) : ViewModel() {
    /** Drives the login gate; flips when the user signs in or out. */
    val isAuthorized: StateFlow<Boolean> = authManager.isAuthorized
}
