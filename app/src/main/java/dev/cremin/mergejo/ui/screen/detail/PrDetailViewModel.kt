package dev.cremin.mergejo.ui.screen.detail

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.mergejo.data.preferences.SettingsRepository
import dev.cremin.mergejo.data.repository.RenovateRepository
import dev.cremin.mergejo.domain.model.MergeStyle
import dev.cremin.mergejo.domain.model.PrComment
import dev.cremin.mergejo.domain.model.RenovatePr
import dev.cremin.mergejo.ui.navigation.Destinations
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.mapNotNull
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
    val comments: CommentsState = CommentsState.Loading,
)

sealed interface CommentsState {
    data object Loading : CommentsState
    data class Loaded(val comments: List<PrComment>) : CommentsState
    data class Failed(val message: String) : CommentsState
}

@HiltViewModel
class PrDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: RenovateRepository,
    settings: SettingsRepository,
) : ViewModel() {

    private val prId: String = checkNotNull(savedStateHandle[Destinations.ARG_PR_ID])

    private val actionInProgress = MutableStateFlow(false)
    private val comments = MutableStateFlow<CommentsState>(CommentsState.Loading)

    private val _events = MutableSharedFlow<DetailEvent>(extraBufferCapacity = 8)
    val events = _events.asSharedFlow()

    val uiState: StateFlow<PrDetailUiState> = combine(
        repository.pullRequests,
        repository.isRefreshing,
        actionInProgress,
        settings.defaultMergeStyle,
        comments,
    ) { prs, refreshing, inProgress, mergeStyle, comments ->
        val pr = prs.firstOrNull { it.id == prId }
        PrDetailUiState(
            pr = pr,
            loading = pr == null && refreshing,
            notFound = pr == null && !refreshing,
            actionInProgress = inProgress,
            defaultMergeStyle = mergeStyle,
            comments = comments,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrDetailUiState())

    init {
        if (repository.pullRequests.value.isEmpty()) {
            viewModelScope.launch { repository.refresh() }
        }
        // Again after a push: CI bots rewrite their comment for the new head commit.
        viewModelScope.launch {
            repository.pullRequests
                .mapNotNull { prs -> prs.firstOrNull { it.id == prId } }
                .distinctUntilChangedBy { it.headSha }
                .collectLatest { pr ->
                    comments.value = repository.comments(pr).fold(
                        onSuccess = { CommentsState.Loaded(it) },
                        onFailure = { CommentsState.Failed(it.message ?: "Unknown error") },
                    )
                }
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
