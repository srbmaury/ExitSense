package com.exitsense.app.presentation.home

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.*
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.GetActiveProfilesUseCase
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.rules.DEFAULT_EXIT_THRESHOLD
import com.exitsense.app.rules.ExitDetectionResult
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.PressureData
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.ScreenStateProvider
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.service.ExitMonitoringService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class HomeUiState(
    val isLoading: Boolean = true,
    val isSetupComplete: Boolean = false,
    val activeProfiles: List<ReminderProfile> = emptyList(),
    val detectionResult: ExitDetectionResult? = null,
    val detectionResultTime: Long? = null,
    val recentExitEvents: List<ExitEvent> = emptyList(),
    val isMonitoring: Boolean = false,
    val currentMotion: MotionType = MotionType.STILL,
    val wifiConnected: Boolean = false,
    val wifiSsid: String? = null,
    val wifiNetworkId: Int = -1,
    val homeWifiSsid: String = "",
    val homeNetworkIds: Set<Int> = emptySet(),
    val confidenceThreshold: Float = DEFAULT_EXIT_THRESHOLD,
    val pressureData: PressureData = PressureData()
)


@HiltViewModel
class HomeViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getActiveProfiles: GetActiveProfilesUseCase,
    private val exitEventRepository: ExitEventRepository,
    private val reminderRepository: ReminderRepository,
    private val exitDetector: ExitDetector,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: ExitNotificationManager,
    private val motionProvider: MotionProvider,
    private val wifiProvider: WifiProvider,
    private val screenStateProvider: ScreenStateProvider,
    private val pressureProvider: PressureProvider,
    private val stepCountProvider: StepCountProvider,
    private val chargerStateProvider: ChargerStateProvider,
    private val ambientLightProvider: AmbientLightProvider
) : ViewModel() {

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        notificationManager.createChannels()
        motionProvider.startMonitoring()
        wifiProvider.startMonitoring()
        // Force a fresh SSID read now that setup permissions may have just been granted.
        // startMonitoring() early-returns if the singleton was already started (e.g. in the
        // setup wizard before permissions existed), so the persistent callback may carry a
        // stale null SSID — refresh() issues a one-shot callback that re-reads it.
        wifiProvider.refresh()
        screenStateProvider.startMonitoring()
        pressureProvider.startMonitoring()
        stepCountProvider.startMonitoring()
        chargerStateProvider.startMonitoring()
        ambientLightProvider.startMonitoring()
        observePreferences()
        observeSensorStates()
        observeRecentEvents()
        runInitialDetection()
    }

    override fun onCleared() {
        motionProvider.stopMonitoring()
        wifiProvider.stopMonitoring()
        screenStateProvider.stopMonitoring()
        pressureProvider.stopMonitoring()
        stepCountProvider.stopMonitoring()
        chargerStateProvider.stopMonitoring()
        ambientLightProvider.stopMonitoring()
        super.onCleared()
    }

    private fun observePreferences() {
        viewModelScope.launch {
            preferencesDataStore.userPreferences.collect { prefs ->
                _uiState.update {
                    it.copy(
                        isSetupComplete = prefs.isSetupComplete,
                        isMonitoring = prefs.isMonitoringEnabled,
                        confidenceThreshold = prefs.exitConfidenceThreshold,
                        homeWifiSsid = prefs.homeWifiSsid,
                        homeNetworkIds = prefs.homeNetworkIds,
                        isLoading = false
                    )
                }
            }
        }
        // Re-run detection immediately when detection-affecting settings change
        viewModelScope.launch {
            preferencesDataStore.userPreferences
                .drop(1) // skip initial load — runInitialDetection() handles that
                .distinctUntilChanged { old, new ->
                    old.homeWifiSsid == new.homeWifiSsid &&
                    old.homeNetworkIds == new.homeNetworkIds &&
                    old.exitConfidenceThreshold == new.exitConfidenceThreshold
                }
                .collect { detectNow() }
        }
    }

    private fun observeSensorStates() {
        viewModelScope.launch {
            motionProvider.currentMotion.collect { motion ->
                _uiState.update { it.copy(currentMotion = motion) }
            }
        }
        viewModelScope.launch {
            wifiProvider.wifiState.collect { wifi ->
                _uiState.update {
                    it.copy(wifiConnected = wifi.isConnected, wifiSsid = wifi.ssid, wifiNetworkId = wifi.networkId)
                }
            }
        }
        viewModelScope.launch {
            pressureProvider.pressureData.collect { pressure ->
                _uiState.update { it.copy(pressureData = pressure) }
            }
        }
        // Re-run detection when a score-affecting property changes.
        // Wrap high-frequency sensor flows so only the detection-relevant field is observed,
        // preventing continuous lux/pressure/step readings from resetting the debounce.
        combine(
            combine(
                motionProvider.currentMotion,
                wifiProvider.wifiState,
                screenStateProvider.recentlyUnlocked,
                pressureProvider.pressureData.map { it.isDescending }.distinctUntilChanged(),
                stepCountProvider.stepData.map { it.stepsLastMinute >= 20 }.distinctUntilChanged()
            ) { _, _, _, _, _ -> Unit },
            combine(
                chargerStateProvider.chargerData,
                ambientLightProvider.lightData.map { it.isOutdoor }.distinctUntilChanged()
            ) { _, _ -> Unit }
        ) { _, _ -> Unit }
            .drop(1)
            .debounce(500L)
            .onEach { detectNow() }
            .launchIn(viewModelScope)
    }

    private fun observeRecentEvents() {
        viewModelScope.launch {
            exitEventRepository.getRecentExitEvents(5).collect { events ->
                _uiState.update { it.copy(recentExitEvents = events) }
            }
        }
        viewModelScope.launch {
            getActiveProfiles().collect { profiles ->
                _uiState.update { it.copy(activeProfiles = profiles) }
            }
        }
    }

    // Runs detection once on startup after preferences and profiles are ready.
    private fun runInitialDetection() {
        viewModelScope.launch {
            _uiState.first { !it.isLoading }
            delay(400L) // brief buffer for Room profiles to arrive
            detectNow()
        }
    }

    fun runManualDetection() {
        viewModelScope.launch { detectNow() }
    }

    private suspend fun detectNow() {
        val prefs = preferencesDataStore.userPreferences.first()
        val profiles = reminderRepository.getActiveProfiles().first()
        val result = exitDetector.evaluate(
            activeProfiles = profiles,
            homeWifiSsid = prefs.homeWifiSsid,
            homeNetworkIds = prefs.homeNetworkIds,
            threshold = prefs.exitConfidenceThreshold
        )
        _uiState.update { it.copy(detectionResult = result, detectionResultTime = System.currentTimeMillis()) }
        // Notification delivery is owned exclusively by ExitMonitoringService to prevent
        // duplicate notifications when both the service and the ViewModel evaluate simultaneously.
    }

    fun calibratePressureBaseline() {
        pressureProvider.calibrateBaseline()
    }

    fun startMonitoringService() {
        val intent = Intent(context, ExitMonitoringService::class.java)
            .setAction(ExitMonitoringService.ACTION_START)
        context.startForegroundService(intent)
        viewModelScope.launch { preferencesDataStore.setMonitoringEnabled(true) }
    }

    fun stopMonitoringService() {
        val intent = Intent(context, ExitMonitoringService::class.java)
            .setAction(ExitMonitoringService.ACTION_STOP)
        context.startService(intent)
        viewModelScope.launch { preferencesDataStore.setMonitoringEnabled(false) }
    }
}
