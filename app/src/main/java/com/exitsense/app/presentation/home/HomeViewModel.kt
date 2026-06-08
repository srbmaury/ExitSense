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
import com.exitsense.app.rules.ExitDetectionResult
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.TimeRuleEvaluator
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
    val confidenceThreshold: Float = 70f,
    val pressureData: PressureData = PressureData()
)

private const val NOTIFICATION_COOLDOWN_MS = 24 * 60 * 60 * 1000L // 1 per day

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
                        confidenceThreshold = prefs.exitConfidenceThreshold,
                        isLoading = false
                    )
                }
            }
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
                    it.copy(wifiConnected = wifi.isConnected, wifiSsid = wifi.ssid)
                }
            }
        }
        viewModelScope.launch {
            pressureProvider.pressureData.collect { pressure ->
                _uiState.update { it.copy(pressureData = pressure) }
            }
        }
        // Re-run detection automatically whenever any sensor value changes.
        combine(
            combine(
                motionProvider.currentMotion,
                wifiProvider.wifiState,
                screenStateProvider.recentlyUnlocked,
                pressureProvider.pressureData,
                stepCountProvider.stepData
            ) { _, _, _, _, _ -> Unit },
            combine(
                chargerStateProvider.chargerData,
                ambientLightProvider.lightData
            ) { _, _ -> Unit }
        ) { _, _ -> Unit }
            .drop(1)         // skip the initial combined emission
            .debounce(2_000L)
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
        val profiles = _uiState.value.activeProfiles
        val result = exitDetector.evaluate(
            activeProfiles = profiles,
            homeWifiSsid = prefs.homeWifiSsid,
            threshold = prefs.exitConfidenceThreshold
        )
        val now = System.currentTimeMillis()
        _uiState.update { it.copy(detectionResult = result, detectionResultTime = now) }

        if (result.isExitDetected && prefs.notificationsEnabled) {
            val profile = profiles.firstOrNull {
                TimeRuleEvaluator.isWithinSchedule(it) &&
                    now - it.lastNotifiedAt >= NOTIFICATION_COOLDOWN_MS
            } ?: return
            val event = ExitEvent(
                confidenceScore = result.confidenceScore,
                triggeredSignals = result.signals.map { it.type },
                notificationShown = true,
                profileId = profile.id
            )
            val eventId = exitEventRepository.saveExitEvent(event)
            reminderRepository.updateProfileLastNotifiedAt(profile.id, now)
            notificationManager.showExitReminder(
                exitEventId = eventId,
                profileId = profile.id,
                profileName = profile.name,
                items = profile.items.filter { it.isEnabled }.sortedByDescending { it.effectivePriority },
                snoozeMinutes = prefs.reminderSnoozeMinutes
            )
        }
    }

    fun calibratePressureBaseline() {
        pressureProvider.calibrateBaseline()
    }

    fun startMonitoringService() {
        val intent = Intent(context, ExitMonitoringService::class.java)
            .setAction(ExitMonitoringService.ACTION_START)
        context.startForegroundService(intent)
        _uiState.update { it.copy(isMonitoring = true) }
    }

    fun stopMonitoringService() {
        val intent = Intent(context, ExitMonitoringService::class.java)
            .setAction(ExitMonitoringService.ACTION_STOP)
        context.startService(intent)
        _uiState.update { it.copy(isMonitoring = false) }
    }
}
