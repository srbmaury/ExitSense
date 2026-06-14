package com.exitsense.app.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.backup.ProfileBackupManager
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.WifiProvider
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val currentWifiSsid: String? = null,
    val currentNetworkId: Int = -1,
    val isWifiConnected: Boolean = false,
    val hasBarometer: Boolean = true,
    val exportMessage: String? = null,
    val importMessage: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: UserPreferencesDataStore,
    private val wifiProvider: WifiProvider,
    private val pressureProvider: PressureProvider,
    private val reminderRepository: ReminderRepository,
    private val backupManager: ProfileBackupManager
) : ViewModel() {

    private val hasBarometer = pressureProvider.pressureData.value.isAvailable

    private val _exportMessage = MutableStateFlow<String?>(null)
    private val _importMessage = MutableStateFlow<String?>(null)

    init {
        wifiProvider.startMonitoring()
        wifiProvider.refresh()
    }

    override fun onCleared() {
        super.onCleared()
        wifiProvider.stopMonitoring()
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        dataStore.userPreferences,
        wifiProvider.wifiState,
        _exportMessage,
        _importMessage
    ) { prefs, wifi, exportMsg, importMsg ->
        SettingsUiState(
            preferences = prefs,
            isLoading = false,
            currentWifiSsid = wifi.ssid,
            currentNetworkId = wifi.networkId,
            isWifiConnected = wifi.isConnected,
            hasBarometer = hasBarometer,
            exportMessage = exportMsg,
            importMessage = importMsg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsUiState(hasBarometer = hasBarometer)
    )

    fun refreshWifiSsid() { wifiProvider.refresh() }

    fun updateHomeWifi(ssid: String) {
        viewModelScope.launch { dataStore.updateHomeWifiSsid(ssid) }
    }

    fun addHomeNetworkId(networkId: Int) {
        viewModelScope.launch { dataStore.addHomeNetworkId(networkId) }
    }

    fun removeHomeNetworkId(networkId: Int) {
        viewModelScope.launch { dataStore.removeHomeNetworkId(networkId) }
    }

    fun updateHomeFloor(floor: Int) {
        viewModelScope.launch { dataStore.updateHomeFloor(floor) }
    }

    fun updateNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateNotificationsEnabled(enabled) }
    }

    fun updateConfidenceThreshold(threshold: Float) {
        viewModelScope.launch { dataStore.updateConfidenceThreshold(threshold) }
    }

    fun updateSnoozeMinutes(minutes: Int) {
        viewModelScope.launch { dataStore.updateSnoozeMinutes(minutes) }
    }

    fun updateQuietHoursEnabled(enabled: Boolean) {
        viewModelScope.launch { dataStore.updateQuietHoursEnabled(enabled) }
    }

    fun updateQuietHoursStart(minuteFromMidnight: Int) {
        viewModelScope.launch { dataStore.updateQuietHoursStartMinute(minuteFromMidnight) }
    }

    fun updateQuietHoursEnd(minuteFromMidnight: Int) {
        viewModelScope.launch { dataStore.updateQuietHoursEndMinute(minuteFromMidnight) }
    }

    fun exportProfiles(uri: Uri) {
        viewModelScope.launch {
            val prefs = dataStore.userPreferences.first()
            val profiles = reminderRepository.getAllProfiles().first()
            backupManager.exportToUri(prefs, profiles, uri)
                .onSuccess { count ->
                    _exportMessage.value = "Exported $count profile${if (count == 1) "" else "s"} with all settings"
                }
                .onFailure { _exportMessage.value = "Export failed: ${it.message}" }
        }
    }

    fun importProfiles(uri: Uri) {
        viewModelScope.launch {
            backupManager.importFromUri(uri)
                .onSuccess { result ->
                    val p = result.preferences
                    // Restore all settings (skip operational flags: setup complete, monitoring, last exit)
                    dataStore.updateHomeWifiSsid(p.homeWifiSsid)
                    dataStore.restoreHomeNetworkIds(p.homeNetworkIds)
                    dataStore.updateHomeFloor(p.homeFloor)
                    dataStore.updateConfidenceThreshold(p.exitConfidenceThreshold)
                    dataStore.updateSnoozeMinutes(p.reminderSnoozeMinutes)
                    dataStore.updateQuietHoursEnabled(p.quietHoursEnabled)
                    dataStore.updateQuietHoursStartMinute(p.quietHoursStartMinute)
                    dataStore.updateQuietHoursEndMinute(p.quietHoursEndMinute)
                    dataStore.updateWeatherEnabled(p.weatherEnabled)
                    dataStore.updateCalendarEnabled(p.calendarEnabled)
                    // Restore profiles
                    result.profiles.forEach { reminderRepository.saveProfile(it) }
                    val profileCount = result.profiles.size
                    _importMessage.value = "Restored $profileCount profile${if (profileCount == 1) "" else "s"} and all settings"
                }
                .onFailure { _importMessage.value = "Import failed: ${it.message}" }
        }
    }

    fun clearExportMessage() { _exportMessage.value = null }
    fun clearImportMessage() { _importMessage.value = null }
}
