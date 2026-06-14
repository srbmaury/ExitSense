package com.exitsense.app.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.GetExitHistoryUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryUiState(
    val events: List<ExitEvent> = emptyList(),
    val profileNames: Map<Long, String> = emptyMap(),
    val isLoading: Boolean = true
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    getExitHistory: GetExitHistoryUseCase,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    val uiState: StateFlow<HistoryUiState> = combine(
        getExitHistory(limit = 100),
        reminderRepository.getAllProfiles()
    ) { events, profiles ->
        HistoryUiState(
            events = events,
            profileNames = profiles.associate { it.id to it.name },
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HistoryUiState()
    )
}
