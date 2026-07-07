package dev.cremin.gitops.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.gitops.data.preferences.SettingsRepository
import dev.cremin.gitops.data.repository.RenovateRepository
import dev.cremin.gitops.domain.model.MergeStyle
import dev.cremin.gitops.domain.model.RenovatePr
import dev.cremin.gitops.ui.navigation.Destinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface DetailEvent {
    data object Dismiss : DetailEvent
    data object RebaseRequested : DetailEvent
    data object Approved : DetailEvent
    data class Error(val text: String) : DetailEvent
}

data class PrDetailUiState(
    val pr: RenovatePr? = null,
    val loading: Boolean = true,
    val notFound: Boolean = false,
    val actionInProgress: Boolean = false,
    val defaultMergeStyle: MergeStyle = MergeStyle.SQUASH,
)

@HiltViewModel
class PrDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RenovateRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val prId: String = checkNotNull(savedStateHandle[Destinations.ARG_PR_ID])

    private val actionInProgress = MutableStateFlow(false)

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<PrDetailUiState> = combine(
        repository.pullRequests,
        repository.isRefreshing,
        actionInProgress,
        settings.defaultMergeStyle,
    ) { prs, refreshing, inProgress, mergeStyle ->
        val pr = prs.firstOrNull { it.id == prId }
        PrDetailUiState(
            pr = pr,
            loading = pr == null && refreshing,
            notFound = pr == null && !refreshing,
            actionInProgress = inProgress,
            defaultMergeStyle = mergeStyle,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrDetailUiState())

    init {
        if (repository.pullRequests.value.isEmpty()) {
            viewModelScope.launch { repository.refresh() }
        }
    }

    fun merge(style: MergeStyle) = runAction(onSuccess = DetailEvent.Dismiss) { repository.merge(it, style) }
    fun approve() = runAction(onSuccess = DetailEvent.Approved) { repository.approve(it) }
    fun rebase() = runAction(onSuccess = DetailEvent.RebaseRequested) { repository.rebase(it) }
    fun close() = runAction(onSuccess = DetailEvent.Dismiss) { repository.close(it) }

    private fun runAction(
        onSuccess: DetailEvent,
        block: suspend (RenovatePr) -> Result<Unit>,
    ) {
        val pr = uiState.value.pr ?: return
        viewModelScope.launch {
            actionInProgress.value = true
            val result = block(pr)
            actionInProgress.value = false
            result
                .onSuccess { _events.emit(onSuccess) }
                .onFailure { e -> _events.emit(DetailEvent.Error(e.message ?: "Action failed")) }
        }
    }
}
