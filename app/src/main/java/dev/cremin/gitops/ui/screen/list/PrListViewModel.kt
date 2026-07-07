package dev.cremin.gitops.ui.screen.list

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.cremin.gitops.R
import dev.cremin.gitops.data.repository.RenovateRepository
import dev.cremin.gitops.domain.model.CiStatus
import dev.cremin.gitops.domain.model.RenovatePr
import dev.cremin.gitops.domain.model.UpdateType
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class PrFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    PASSING(R.string.filter_passing),
    FAILING(R.string.filter_failing),
    SECURITY(R.string.filter_security),
    MAJOR(R.string.filter_major),
    MINOR(R.string.filter_minor),
    PATCH(R.string.filter_patch);

    fun matches(pr: RenovatePr): Boolean = when (this) {
        ALL -> true
        PASSING -> pr.ciStatus == CiStatus.SUCCESS
        FAILING -> pr.ciStatus == CiStatus.FAILURE
        SECURITY -> pr.isSecurity
        MAJOR -> pr.updateType == UpdateType.MAJOR
        MINOR -> pr.updateType == UpdateType.MINOR
        PATCH -> pr.updateType == UpdateType.PATCH
    }
}

data class PrListUiState(
    val prs: List<RenovatePr> = emptyList(),
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val filter: PrFilter = PrFilter.ALL,
    val totalCount: Int = 0,
)

@HiltViewModel
class PrListViewModel @Inject constructor(
    private val repository: RenovateRepository,
) : ViewModel() {

    private val filter = MutableStateFlow(PrFilter.ALL)

    val uiState: StateFlow<PrListUiState> = combine(
        repository.pullRequests,
        repository.isRefreshing,
        repository.error,
        filter,
    ) { prs, refreshing, error, activeFilter ->
        PrListUiState(
            prs = prs.filter(activeFilter::matches),
            isRefreshing = refreshing,
            error = error,
            filter = activeFilter,
            totalCount = prs.size,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PrListUiState())

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch { repository.refresh() }
    }

    fun setFilter(newFilter: PrFilter) {
        filter.value = newFilter
    }
}
