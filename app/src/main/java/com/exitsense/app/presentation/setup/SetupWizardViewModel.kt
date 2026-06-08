package com.exitsense.app.presentation.setup

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.domain.repository.ReminderRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class SetupStep { WIFI, FLOOR, PROFILES, PERMISSIONS }

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.WIFI,
    val homeWifiSsid: String = "",
    val homeFloor: Int = 0,
    val detectedSsid: String? = null,
    val isComplete: Boolean = false,
    val isLoading: Boolean = false
)

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: UserPreferencesDataStore,
    private val reminderRepository: ReminderRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(SetupUiState())
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    init {
        detectCurrentWifi()
    }

    @Suppress("DEPRECATION")
    private fun detectCurrentWifi() {
        try {
            val wifiManager = context.applicationContext
                .getSystemService(Context.WIFI_SERVICE) as WifiManager
            val info = wifiManager.connectionInfo
            val ssid = info?.ssid?.removeSurrounding("\"")
                ?.takeIf { it.isNotBlank() && it != "<unknown ssid>" }
            _uiState.update { it.copy(detectedSsid = ssid) }
        } catch (_: Exception) {}
    }

    fun onWifiSsidChanged(ssid: String) = _uiState.update { it.copy(homeWifiSsid = ssid) }
    fun onFloorChanged(floor: Int) = _uiState.update { it.copy(homeFloor = floor) }

    fun useDetectedSsid() {
        _uiState.update { it.copy(homeWifiSsid = it.detectedSsid ?: "") }
    }

    fun goToStep(step: SetupStep) = _uiState.update { it.copy(currentStep = step) }

    fun nextStep() {
        val next = when (_uiState.value.currentStep) {
            SetupStep.WIFI -> SetupStep.FLOOR
            SetupStep.FLOOR -> SetupStep.PROFILES
            SetupStep.PROFILES -> SetupStep.PERMISSIONS
            SetupStep.PERMISSIONS -> return
        }
        _uiState.update { it.copy(currentStep = next) }
    }

    fun previousStep() {
        val prev = when (_uiState.value.currentStep) {
            SetupStep.WIFI -> return
            SetupStep.FLOOR -> SetupStep.WIFI
            SetupStep.PROFILES -> SetupStep.FLOOR
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
            dataStore.updateHomeFloor(state.homeFloor)
            dataStore.setSetupComplete(true)
            _uiState.update { it.copy(isComplete = true) }
        }
    }
}
