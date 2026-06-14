package com.exitsense.app.presentation.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.WifiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// FLOOR step is skipped on devices without a barometer — order stays the same, step just isn't shown.
enum class SetupStep { WIFI, FLOOR, PROFILES, PERMISSIONS }

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.WIFI,
    val homeWifiSsid: String = "",
    val homeFloor: Int = 0,
    val detectedSsid: String? = null,
    val detectedNetworkId: Int = -1,
    val isComplete: Boolean = false,
    val isLoading: Boolean = false,
    val hasBarometer: Boolean = true,
    val wifiSsidError: Boolean = false
)

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
    private val reminderRepository: ReminderRepository,
    private val wifiProvider: WifiProvider,
    private val pressureProvider: PressureProvider
) : ViewModel() {

    private val hasBarometer = pressureProvider.pressureData.value.isAvailable

    private val _uiState = MutableStateFlow(SetupUiState(hasBarometer = hasBarometer))
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        wifiProvider.startMonitoring()
        wifiProvider.refresh()
        viewModelScope.launch {
            wifiProvider.wifiState.collect { wifi ->
                _uiState.update {
                    it.copy(detectedSsid = wifi.ssid, detectedNetworkId = wifi.networkId)
                }
            }
        }
    }

    override fun onCleared() {
        wifiProvider.stopMonitoring()
        super.onCleared()
    }

    fun onWifiSsidChanged(ssid: String) = _uiState.update { it.copy(homeWifiSsid = ssid, wifiSsidError = false) }
    fun onFloorChanged(floor: Int) = _uiState.update { it.copy(homeFloor = floor) }

    fun useDetectedSsid() {
        _uiState.update { it.copy(homeWifiSsid = it.detectedSsid ?: "") }
    }

    fun goToStep(step: SetupStep) = _uiState.update { it.copy(currentStep = step) }

    fun nextStep() {
        val state = _uiState.value
        if (state.currentStep == SetupStep.WIFI && state.homeWifiSsid.isBlank()) {
            _uiState.update { it.copy(wifiSsidError = true) }
            return
        }
        val next = when (state.currentStep) {
            SetupStep.WIFI -> if (hasBarometer) SetupStep.FLOOR else SetupStep.PROFILES
            SetupStep.FLOOR -> SetupStep.PROFILES
            SetupStep.PROFILES -> SetupStep.PERMISSIONS
            SetupStep.PERMISSIONS -> return
        }
        _uiState.update { it.copy(currentStep = next, wifiSsidError = false) }
    }

    fun previousStep() {
        val prev = when (_uiState.value.currentStep) {
            SetupStep.WIFI -> return
            SetupStep.FLOOR -> SetupStep.WIFI
            SetupStep.PROFILES -> if (hasBarometer) SetupStep.FLOOR else SetupStep.WIFI
            SetupStep.PERMISSIONS -> SetupStep.PROFILES
        }
        _uiState.update { it.copy(currentStep = prev) }
    }

    fun createDefaultOfficeProfile() {
        viewModelScope.launch {
            val profile = ReminderProfile(
                name = "Office",
                scheduleType = ScheduleType.WEEKDAYS,
                activeDays = setOf(1, 2, 3, 4, 5),
                startTimeHour = 8,
                startTimeMinute = 0,
                endTimeHour = 10,
                endTimeMinute = 0,
                items = listOf(
                    ReminderItem(profileId = 0, name = "Office ID", priority = 5),
                    ReminderItem(profileId = 0, name = "Laptop", priority = 5),
                    ReminderItem(profileId = 0, name = "Wallet", priority = 5),
                    ReminderItem(profileId = 0, name = "Keys", priority = 4),
                    ReminderItem(profileId = 0, name = "Phone Charger", priority = 3)
                )
            )
            reminderRepository.saveProfile(profile)
        }
    }

    fun finishSetup() {
        val state = _uiState.value
        viewModelScope.launch {
            dataStore.updateHomeWifiSsid(state.homeWifiSsid)
            if (state.detectedNetworkId != -1) dataStore.addHomeNetworkId(state.detectedNetworkId)
            dataStore.updateHomeFloor(state.homeFloor)
            dataStore.setSetupComplete(true)
            _uiState.update { it.copy(isComplete = true) }
        }
    }
}
