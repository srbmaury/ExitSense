package com.exitsense.app.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.notifications.ExitReminderDispatcher
import com.exitsense.app.rules.DepartureTracker
import com.exitsense.app.rules.TimeRuleEvaluator
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.ScreenStateProvider
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.util.DebugLog
import com.exitsense.app.util.WifiNamePermission
import android.content.pm.ServiceInfo
import android.os.Build
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

/**
 * Foreground service that watches for the user leaving home while monitoring is on.
 * Wi-Fi, charger and screen events are watched all the time (they're cheap); the motion,
 * pressure, step and light sensors only run while a profile's schedule is active or about
 * to start.
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
    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var notificationManager: ExitNotificationManager
    @Inject lateinit var reminderDispatcher: ExitReminderDispatcher
    @Inject lateinit var departureTracker: DepartureTracker

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null
    private var sensorsEnabled = false

    companion object {
        const val ACTION_START = "com.exitsense.app.START_MONITORING"
        const val ACTION_STOP = "com.exitsense.app.STOP_MONITORING"
        /** Sensors start this long before a schedule so the walk out of the door is caught. */
        private const val SENSOR_LEAD_MINUTES = 15
        private const val SCHEDULE_CHECK_MS = 60_000L

        /**
         * Starts (or re-attaches to) the service. Safe to call repeatedly — monitoring is only
         * set up once. Returns false if the system refused a foreground-service start.
         */
        fun start(context: Context): Boolean {
            val intent = Intent(context, ExitMonitoringService::class.java).setAction(ACTION_START)
            return runCatching { context.startForegroundService(intent) }
                .onFailure { DebugLog.d("service") { "start refused: $it" } }
                .isSuccess
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        startInForeground()
    }

    /**
     * The location type lets the service read the connected Wi-Fi SSID in the background
     * using the user's "while in use" location grant. It can only be claimed while the app
     * is in the foreground, so fall back to special-use alone (e.g. on a sticky restart).
     */
    private fun startInForeground() {
        val id = ExitNotificationManager.NOTIFICATION_ID_SERVICE
        val notification = notificationManager.buildServiceNotification()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Uses every type declared in the manifest; pre-14 has no runtime type checks
            startForeground(id, notification)
            return
        }
        val specialUse = ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
        if (WifiNamePermission.isGranted(this)) {
            try {
                startForeground(id, notification, specialUse or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
                DebugLog.d("service") { "foreground: specialUse|location" }
                return
            } catch (_: SecurityException) {
                // App is in the background — location type not allowed right now
            }
        }
        startForeground(id, notification, specialUse)
        DebugLog.d("service") { "foreground: specialUse only (no location access)" }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopSelf()
            return START_NOT_STICKY
        }
        // Re-claim the service type: location may have been granted since onCreate
        startInForeground()
        startMonitoring()
        // Opening the app is a good moment to fetch the forecast: location access is
        // available now and the phone is most likely still on home Wi-Fi
        scope.launch { reminderDispatcher.prefetchWeather() }
        return START_STICKY
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    private fun startMonitoring() {
        if (monitoringJob?.isActive == true) return

        wifiProvider.startMonitoring()
        screenStateProvider.startMonitoring()
        chargerStateProvider.startMonitoring()
        departureTracker.start()

        // Re-check every minute (and whenever profiles change) whether any schedule needs sensors
        scope.launch {
            reminderRepository.getActiveProfiles().collectLatest { profiles ->
                while (true) {
                    val scheduleNear = profiles.any {
                        TimeRuleEvaluator.isActiveOrStartingSoon(it, SENSOR_LEAD_MINUTES)
                    }
                    setSensorsEnabled(scheduleNear)
                    if (scheduleNear && wifiProvider.wifiState.value.isConnected) {
                        reminderDispatcher.prefetchWeather()
                    }
                    delay(SCHEDULE_CHECK_MS)
                }
            }
        }

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
                ambientLightProvider.lightData.map { it.isOutdoor }.distinctUntilChanged(),
                departureTracker.departure
            ) { _, _, _ -> Unit }
        ) { _, _ -> Unit }
            .debounce(2_000L)
            .onEach { reminderDispatcher.evaluateAndNotify() }
            .also { monitoringJob = it.launchIn(scope) }
    }

    @Synchronized
    private fun setSensorsEnabled(enabled: Boolean) {
        if (enabled == sensorsEnabled) return
        sensorsEnabled = enabled
        DebugLog.d("service") { if (enabled) "sensors on (schedule near)" else "sensors off (no schedule near)" }
        if (enabled) {
            motionProvider.startMonitoring()
            pressureProvider.startMonitoring()
            stepCountProvider.startMonitoring()
            ambientLightProvider.startMonitoring()
        } else {
            motionProvider.stopMonitoring()
            pressureProvider.stopMonitoring()
            stepCountProvider.stopMonitoring()
            ambientLightProvider.stopMonitoring()
        }
    }

    override fun onDestroy() {
        scope.cancel()
        // Providers are ref-counted, so only release what startMonitoring() acquired
        if (monitoringJob != null) {
            setSensorsEnabled(false)
            departureTracker.stop()
            wifiProvider.stopMonitoring()
            screenStateProvider.stopMonitoring()
            chargerStateProvider.stopMonitoring()
        }
        super.onDestroy()
    }
}
