package com.exitsense.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.usecase.GetExitHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryUiState(
    val events: List<ExitEvent> = emptyList(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getExitHistory: GetExitHistoryUseCase
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = getExitHistory(limit = 100)
        .map { events -> HistoryUiState(events = events, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = HistoryUiState()
        )
}
