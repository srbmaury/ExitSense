package com.exitsense.app.presentation.setup

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.backup.toUserMessage
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.ImportBackupUseCase
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.WifiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

// FLOOR step is skipped on devices without a barometer — order stays the same, step just isn't shown.
// WIFI is last so location permission is already granted when the user reaches it (scan works).
enum class SetupStep { FLOOR, PROFILES, PERMISSIONS, WIFI }

data class SetupUiState(
    val currentStep: SetupStep = SetupStep.FLOOR,
    val homeWifiSsid: String = "",
    val homeFloor: Int = 0,
    val detectedSsid: String? = null,
    val detectedNetworkId: Int = -1,
    val isComplete: Boolean = false,
    val isLoading: Boolean = false,
    val hasBarometer: Boolean = true,
    val wifiSsidError: Boolean = false,
    val importMessage: String? = null,
    val availableNetworks: List<String> = emptyList(),
    val isScanningNetworks: Boolean = false
)

@HiltViewModel
class SetupWizardViewModel @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
    private val reminderRepository: ReminderRepository,
    private val wifiProvider: WifiProvider,
    private val pressureProvider: PressureProvider,
    private val importBackupUseCase: ImportBackupUseCase
) : ViewModel() {

    private val hasBarometer = pressureProvider.pressureData.value.isAvailable

    private val _uiState = MutableStateFlow(SetupUiState(
        hasBarometer = hasBarometer,
        currentStep = if (hasBarometer) SetupStep.FLOOR else SetupStep.PROFILES
    ))
    val uiState: StateFlow<SetupUiState> = _uiState.asStateFlow()

    private var scanTimeoutJob: Job? = null

    init {
        wifiProvider.startMonitoring()
        wifiProvider.refresh()
        viewModelScope.launch {
            wifiProvider.wifiState.collect { wifi ->
                _uiState.update { state ->
                    // Auto-fill the SSID field the moment we detect the connected network,
                    // but only if we're on the WiFi step and the user hasn't typed anything yet.
                    val shouldAutoFill = state.currentStep == SetupStep.WIFI &&
                        state.homeWifiSsid.isBlank() &&
                        wifi.ssid != null
                    state.copy(
                        detectedSsid = wifi.ssid,
                        detectedNetworkId = wifi.networkId,
                        homeWifiSsid = if (shouldAutoFill) wifi.ssid!! else state.homeWifiSsid
                    )
                }
            }
        }
        viewModelScope.launch {
            wifiProvider.scanResults.collect { ssids ->
                _uiState.update { it.copy(availableNetworks = ssids, isScanningNetworks = false) }
                scanTimeoutJob?.cancel()
            }
        }
        wifiProvider.triggerScan()
    }

    override fun onCleared() {
        wifiProvider.stopMonitoring()
        super.onCleared()
    }

    fun onWifiSsidChanged(ssid: String) = _uiState.update { it.copy(homeWifiSsid = ssid, wifiSsidError = false) }
    fun onFloorChanged(floor: Int) = _uiState.update { it.copy(homeFloor = floor) }

    fun triggerScan() = startScan()

    private fun startScan() {
        _uiState.update { it.copy(isScanningNetworks = true) }
        wifiProvider.triggerScan()
        scanTimeoutJob?.cancel()
        scanTimeoutJob = viewModelScope.launch {
            delay(5_000L)
            _uiState.update { it.copy(isScanningNetworks = false) }
        }
    }

    fun useDetectedSsid() {
        _uiState.update { it.copy(homeWifiSsid = it.detectedSsid ?: "") }
    }

    fun goToStep(step: SetupStep) = _uiState.update { it.copy(currentStep = step) }

    fun nextStep() {
        val state = _uiState.value
        val next = when (state.currentStep) {
            SetupStep.FLOOR -> SetupStep.PROFILES
            SetupStep.PROFILES -> SetupStep.PERMISSIONS
            SetupStep.PERMISSIONS -> {
                // Arriving at WiFi step with permissions granted — refresh SSID and scan now.
                wifiProvider.refresh()
                startScan()
                // Also pre-fill immediately if we already have a detected SSID.
                if (state.homeWifiSsid.isBlank() && state.detectedSsid != null) {
                    _uiState.update { it.copy(homeWifiSsid = state.detectedSsid) }
                }
                SetupStep.WIFI
            }
            SetupStep.WIFI -> return
        }
        _uiState.update { it.copy(currentStep = next, wifiSsidError = false) }
    }

    fun previousStep() {
        val prev = when (_uiState.value.currentStep) {
            SetupStep.FLOOR -> return
            SetupStep.PROFILES -> if (hasBarometer) SetupStep.FLOOR else return
            SetupStep.PERMISSIONS -> SetupStep.PROFILES
            SetupStep.WIFI -> SetupStep.PERMISSIONS
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

    fun importBackup(uri: Uri) {
        viewModelScope.launch {
            importBackupUseCase(uri, markSetupComplete = false)
                .onSuccess { summary ->
                    val restoredPrefs = dataStore.userPreferences.first()
                    _uiState.update {
                        it.copy(
                            currentStep = SetupStep.PERMISSIONS,
                            homeWifiSsid = restoredPrefs.homeWifiSsid,
                            homeFloor = restoredPrefs.homeFloor,
                            importMessage = "${summary.toUserMessage()} — grant permissions then confirm your Wi-Fi to finish.",
                            isComplete = false,
                            wifiSsidError = false
                        )
                    }
                }
                .onFailure { error ->
                    _uiState.update { it.copy(importMessage = "Import failed: ${error.message}") }
                }
        }
    }

    fun clearImportMessage() {
        _uiState.update { it.copy(importMessage = null) }
    }
}
