package com.exitsense.app.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.domain.model.*
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.SaveProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AddEditProfileUiState(
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val isSaved: Boolean = false,
    val name: String = "",
    val scheduleType: ScheduleType = ScheduleType.WEEKDAYS,
    val activeDays: Set<Int> = setOf(1, 2, 3, 4, 5),
    val startTimeHour: Int = 8,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 10,
    val endTimeMinute: Int = 0,
    val items: List<ReminderItem> = emptyList(),
    val newItemName: String = "",
    val error: String? = null,
    val isEditMode: Boolean = false
)

@HiltViewModel
class AddEditProfileViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val saveProfileUseCase: SaveProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AddEditProfileUiState())
    val uiState: StateFlow<AddEditProfileUiState> = _uiState.asStateFlow()

    private var editingProfileId: Long = 0L

    fun loadProfile(profileId: Long) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val profile = reminderRepository.getProfileById(profileId) ?: return@launch
            editingProfileId = profileId
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isEditMode = true,
                    name = profile.name,
                    scheduleType = profile.scheduleType,
                    activeDays = profile.activeDays,
                    startTimeHour = profile.startTimeHour,
                    startTimeMinute = profile.startTimeMinute,
                    endTimeHour = profile.endTimeHour,
                    endTimeMinute = profile.endTimeMinute,
                    items = profile.items
                )
            }
        }
    }

    fun onNameChanged(name: String) = _uiState.update { it.copy(name = name, error = null) }
    fun onScheduleTypeChanged(type: ScheduleType) = _uiState.update { it.copy(scheduleType = type) }
    fun onActiveDaysChanged(days: Set<Int>) = _uiState.update { it.copy(activeDays = days) }
    fun onStartTimeChanged(hour: Int, minute: Int) =
        _uiState.update { it.copy(startTimeHour = hour, startTimeMinute = minute) }
    fun onEndTimeChanged(hour: Int, minute: Int) =
        _uiState.update { it.copy(endTimeHour = hour, endTimeMinute = minute) }
    fun onNewItemNameChanged(name: String) = _uiState.update { it.copy(newItemName = name) }

    fun addItem() {
        val name = _uiState.value.newItemName.trim()
        if (name.isBlank()) return
        val newItem = ReminderItem(
            profileId = editingProfileId,
            name = name,
            priority = 3
        )
        _uiState.update {
            it.copy(
                items = it.items + newItem,
                newItemName = ""
            )
        }
    }

    fun removeItem(index: Int) {
        _uiState.update { state ->
            state.copy(items = state.items.toMutableList().also { it.removeAt(index) })
        }
    }

    fun saveProfile() {
        val state = _uiState.value
        if (state.name.isBlank()) {
            _uiState.update { it.copy(error = "Profile name cannot be empty") }
            return
        }
        if (state.items.isEmpty()) {
            _uiState.update { it.copy(error = "Add at least one item to the profile") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }

            val profile = ReminderProfile(
                id = editingProfileId,
                name = state.name,
                scheduleType = state.scheduleType,
                activeDays = state.activeDays,
                startTimeHour = state.startTimeHour,
                startTimeMinute = state.startTimeMinute,
                endTimeHour = state.endTimeHour,
                endTimeMinute = state.endTimeMinute,
                items = state.items
            )

            saveProfileUseCase(profile)
                .onSuccess { _uiState.update { it.copy(isSaving = false, isSaved = true) } }
                .onFailure { e ->
                    _uiState.update { it.copy(isSaving = false, error = e.message) }
                }
        }
    }
}
