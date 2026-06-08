package com.exitsense.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.TimeRuleEvaluator
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.ScreenStateProvider
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.WifiProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * Foreground service that monitors sensors in real time for Wi-Fi disconnect events.
 * Started when the app detects a potential pre-departure window; stops itself after
 * the window closes or an exit event fires.
 */
@AndroidEntryPoint
class ExitMonitoringService : Service() {

    @Inject lateinit var motionProvider: MotionProvider
    @Inject lateinit var wifiProvider: WifiProvider
    @Inject lateinit var pressureProvider: PressureProvider
    @Inject lateinit var screenStateProvider: ScreenStateProvider
    @Inject lateinit var stepCountProvider: StepCountProvider
    @Inject lateinit var chargerStateProvider: ChargerStateProvider
    @Inject lateinit var ambientLightProvider: AmbientLightProvider
    @Inject lateinit var exitDetector: ExitDetector
    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var exitEventRepository: ExitEventRepository
    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore
    @Inject lateinit var notificationManager: ExitNotificationManager

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    companion object {
        const val ACTION_START = "com.exitsense.app.START_MONITORING"
        const val ACTION_STOP = "com.exitsense.app.STOP_MONITORING"
        private const val POLL_INTERVAL_MS = 30_000L
        private const val COOLDOWN_MS = 24 * 60 * 60 * 1000L // one notification per day
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        startForeground(
            ExitNotificationManager.NOTIFICATION_ID_SERVICE,
            notificationManager.buildServiceNotification()
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopSelf()
            else -> startMonitoring()
        }
        return START_STICKY
    }

    private fun startMonitoring() {
        motionProvider.startMonitoring()
        wifiProvider.startMonitoring()
        pressureProvider.startMonitoring()
        screenStateProvider.startMonitoring()
        stepCountProvider.startMonitoring()
        chargerStateProvider.startMonitoring()
        ambientLightProvider.startMonitoring()

        scope.launch {
            while (isActive) {
                evaluateAndNotify()
                delay(POLL_INTERVAL_MS)
            }
        }
    }

    private suspend fun evaluateAndNotify() {
        val prefs = preferencesDataStore.userPreferences.first()
        if (!prefs.isSetupComplete || !prefs.notificationsEnabled) return

        val now = System.currentTimeMillis()
        val activeProfiles = reminderRepository.getActiveProfiles().first()
        if (activeProfiles.isEmpty()) return

        val result = exitDetector.evaluate(
            activeProfiles = activeProfiles,
            homeWifiSsid = prefs.homeWifiSsid,
            threshold = prefs.exitConfidenceThreshold
        )

        if (result.isExitDetected) {
            val profile = activeProfiles.firstOrNull {
                TimeRuleEvaluator.isWithinSchedule(it) && now - it.lastNotifiedAt >= COOLDOWN_MS
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
                items = profile.items.filter { it.isEnabled }
                    .sortedByDescending { it.effectivePriority },
                snoozeMinutes = prefs.reminderSnoozeMinutes
            )
        }
    }

    override fun onDestroy() {
        scope.cancel()
        motionProvider.stopMonitoring()
        wifiProvider.stopMonitoring()
        pressureProvider.stopMonitoring()
        screenStateProvider.stopMonitoring()
        stepCountProvider.stopMonitoring()
        chargerStateProvider.stopMonitoring()
        ambientLightProvider.stopMonitoring()
        super.onDestroy()
    }
}
