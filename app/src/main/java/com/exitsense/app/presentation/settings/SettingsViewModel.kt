package com.exitsense.app.presentation.settings

import android.content.Context
import android.net.wifi.WifiManager
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.UserPreferences
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val preferences: UserPreferences = UserPreferences(),
    val isLoading: Boolean = true,
    val availableSsids: List<String> = emptyList(),
    val isSaved: Boolean = false
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: UserPreferencesDataStore
) : ViewModel() {

    val uiState: StateFlow<SettingsUiState> = dataStore.userPreferences
        .map { prefs -> SettingsUiState(preferences = prefs, isLoading = false) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = SettingsUiState()
        )

    fun updateHomeWifi(ssid: String) {
        viewModelScope.launch { dataStore.updateHomeWifiSsid(ssid) }
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

    @Suppress("DEPRECATION")
    fun scanAvailableWifi(): List<String> {
        val wifiManager = context.applicationContext
            .getSystemService(Context.WIFI_SERVICE) as WifiManager
        return try {
            wifiManager.scanResults
                .mapNotNull { it.SSID.takeIf { s -> s.isNotBlank() } }
                .distinct()
                .sorted()
        } catch (_: SecurityException) {
            emptyList()
        }
    }
}
