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
import android.content.pm.ServiceInfo
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.Calendar
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
    private var monitoringJob: Job? = null

    companion object {
        const val ACTION_START = "com.exitsense.app.START_MONITORING"
        const val ACTION_STOP = "com.exitsense.app.STOP_MONITORING"
        private const val COOLDOWN_MS = 24 * 60 * 60 * 1000L
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        val notification = notificationManager.buildServiceNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ExitNotificationManager.NOTIFICATION_ID_SERVICE,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ExitNotificationManager.NOTIFICATION_ID_SERVICE, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        startMonitoring()
        return START_STICKY
    }

    @OptIn(FlowPreview::class)
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        motionProvider.startMonitoring()
        wifiProvider.startMonitoring()
        pressureProvider.startMonitoring()
        screenStateProvider.startMonitoring()
        stepCountProvider.startMonitoring()
        chargerStateProvider.startMonitoring()
        ambientLightProvider.startMonitoring()

        // React to score-affecting changes only — wrap high-frequency sensor flows so
        // continuous lux/pressure/step readings don't prevent the debounce from firing.
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
            .debounce(2_000L)
            .onEach { evaluateAndNotify() }
            .also { monitoringJob = it.launchIn(scope) }
    }

    private fun isInQuietHours(prefs: com.exitsense.app.domain.model.UserPreferences): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = prefs.quietHoursStartMinute
        val end = prefs.quietHoursEndMinute
        return if (start > end) nowMinute >= start || nowMinute < end
               else nowMinute >= start && nowMinute < end
    }

    private suspend fun evaluateAndNotify() {
        val prefs = preferencesDataStore.userPreferences.first()
        if (!prefs.isSetupComplete || !prefs.notificationsEnabled) return
        if (isInQuietHours(prefs)) return

        val now = System.currentTimeMillis()
        val activeProfiles = reminderRepository.getActiveProfiles().first()
        if (activeProfiles.isEmpty()) return

        val result = exitDetector.evaluate(
            activeProfiles = activeProfiles,
            homeWifiSsid = prefs.homeWifiSsid,
            homeNetworkIds = prefs.homeNetworkIds,
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
                items = profile.notifiableItems(),
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
