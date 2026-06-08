package com.exitsense.app.presentation.profiles

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.DeleteProfileUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ProfilesUiState(
    val profiles: List<ReminderProfile> = emptyList(),
    val isLoading: Boolean = true,
    val error: String? = null,
    val showDeleteConfirm: ReminderProfile? = null
)

@HiltViewModel
class ProfilesViewModel @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val deleteProfileUseCase: DeleteProfileUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfilesUiState())
    val uiState: StateFlow<ProfilesUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reminderRepository.getAllProfiles()
                .catch { e -> _uiState.update { it.copy(error = e.message, isLoading = false) } }
                .collect { profiles ->
                    _uiState.update { it.copy(profiles = profiles, isLoading = false) }
                }
        }
    }

    fun toggleProfile(profileId: Long, isActive: Boolean) {
        viewModelScope.launch {
            reminderRepository.toggleProfile(profileId, isActive)
        }
    }

    fun requestDelete(profile: ReminderProfile) {
        _uiState.update { it.copy(showDeleteConfirm = profile) }
    }

    fun cancelDelete() {
        _uiState.update { it.copy(showDeleteConfirm = null) }
    }

    fun confirmDelete() {
        val profile = _uiState.value.showDeleteConfirm ?: return
        viewModelScope.launch {
            deleteProfileUseCase(profile.id)
            _uiState.update { it.copy(showDeleteConfirm = null) }
        }
    }
}
