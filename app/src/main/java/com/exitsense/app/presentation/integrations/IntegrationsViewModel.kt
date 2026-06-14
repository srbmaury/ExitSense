package com.exitsense.app.presentation.integrations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.data.repository.CalendarRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class IntegrationsUiState(
    val weatherEnabled: Boolean = false,
    val calendarEnabled: Boolean = false,
    val calendarEvents: List<String> = emptyList()
)

@HiltViewModel
class IntegrationsViewModel @Inject constructor(
    private val preferencesDataStore: UserPreferencesDataStore,
    private val calendarRepository: CalendarRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(IntegrationsUiState())
    val uiState: StateFlow<IntegrationsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesDataStore.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        weatherEnabled = prefs.weatherEnabled,
                        calendarEnabled = prefs.calendarEnabled
                    )
                }
                if (prefs.calendarEnabled) refreshCalendarEvents()
                else _uiState.update { it.copy(calendarEvents = emptyList()) }
            }
        }
    }

    fun setWeatherEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.updateWeatherEnabled(enabled) }
    }

    fun setCalendarEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesDataStore.updateCalendarEnabled(enabled) }
    }

    fun refreshCalendarEvents() {
        viewModelScope.launch(Dispatchers.IO) {
            val events = calendarRepository.getUpcomingEvents()
            _uiState.update { it.copy(calendarEvents = events) }
        }
    }
}
