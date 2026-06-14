package com.exitsense.app.workers

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.exitsense.app.data.local.dao.SensorSnapshotDao
import com.exitsense.app.data.local.mapper.toEntity
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.data.repository.CalendarRepository
import com.exitsense.app.data.repository.WeatherRepository
import com.exitsense.app.domain.model.SensorSnapshot
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
import kotlinx.coroutines.delay
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.concurrent.TimeUnit

@HiltWorker
class ExitDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exitDetector: ExitDetector,
    private val reminderRepository: ReminderRepository,
    private val exitEventRepository: ExitEventRepository,
    private val sensorSnapshotDao: SensorSnapshotDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: ExitNotificationManager,
    private val wifiProvider: WifiProvider,
    private val motionProvider: MotionProvider,
    private val pressureProvider: PressureProvider,
    private val screenStateProvider: ScreenStateProvider,
    private val stepCountProvider: StepCountProvider,
    private val chargerStateProvider: ChargerStateProvider,
    private val ambientLightProvider: AmbientLightProvider,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository
) : CoroutineWorker(context, params) {

    private fun isInQuietHours(prefs: com.exitsense.app.domain.model.UserPreferences): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = prefs.quietHoursStartMinute
        val end = prefs.quietHoursEndMinute
        return if (start > end) nowMinute >= start || nowMinute < end   // overnight window
               else nowMinute >= start && nowMinute < end
    }

    @SuppressLint("MissingPermission")
    private suspend fun fetchWeatherAlert(): String? {
        val hasFine = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val hasCoarse = ContextCompat.checkSelfPermission(
            applicationContext,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (!hasFine && !hasCoarse) return null
        val locationManager = applicationContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            ?: locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            ?: return null
        return weatherRepository.getRainForecast(location.latitude, location.longitude)
    }

    companion object {
        const val WORK_NAME = "exit_detection_periodic"
        const val TAG = "exit_detection"
        private const val COOLDOWN_MS = 24 * 60 * 60 * 1000L  // one notification per day

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<ExitDetectionWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)
                        .build()
                )
                .build()
    }

    override suspend fun doWork(): Result {
        return try {
            val prefs = preferencesDataStore.userPreferences.first()
            if (!prefs.isSetupComplete || !prefs.notificationsEnabled) return Result.success()
            if (isInQuietHours(prefs)) return Result.success()

            // Start all providers so evaluate() sees current sensor state rather than defaults.
            // WiFi's onCapabilitiesChanged fires within ~100 ms; the delay below absorbs that.
            // Motion/pressure/step/light need sustained monitoring to accumulate useful readings,
            // so those signals will typically contribute 0 in the worker — that is acceptable
            // because WiFi and charger are the reliable instant-read signals for background checks.
            wifiProvider.startMonitoring()
            motionProvider.startMonitoring()
            pressureProvider.startMonitoring()
            screenStateProvider.startMonitoring()
            stepCountProvider.startMonitoring()
            chargerStateProvider.startMonitoring()
            ambientLightProvider.startMonitoring()
            try {
                delay(500L) // allow WiFi onCapabilitiesChanged to fire

                val now = System.currentTimeMillis()
                val activeProfiles = reminderRepository.getActiveProfiles().first()
                if (activeProfiles.isEmpty()) return Result.success()

                val result = exitDetector.evaluate(
                    activeProfiles = activeProfiles,
                    homeWifiSsid = prefs.homeWifiSsid,
                    homeNetworkIds = prefs.homeNetworkIds,
                    threshold = prefs.exitConfidenceThreshold
                )

                val wifi = wifiProvider.wifiState.value
                val snapshot = SensorSnapshot(
                    confidenceScore = result.confidenceScore,
                    wifiConnected = wifi.isConnected,
                    connectedSsid = wifi.ssid,
                    motionType = motionProvider.currentMotion.value,
                    screenState = screenStateProvider.screenState.value,
                    pressure = pressureProvider.pressureData.value.currentPressure
                )
                sensorSnapshotDao.insertSnapshot(snapshot.toEntity())

                if (result.isExitDetected) {
                    val profile = activeProfiles.firstOrNull {
                        TimeRuleEvaluator.isWithinSchedule(it) && now - it.lastNotifiedAt >= COOLDOWN_MS
                    } ?: return Result.success()
                    val event = ExitEvent(
                        confidenceScore = result.confidenceScore,
                        triggeredSignals = result.signals.map { it.type },
                        notificationShown = true,
                        profileId = profile.id
                    )
                    val eventId = exitEventRepository.saveExitEvent(event)
                    reminderRepository.updateProfileLastNotifiedAt(profile.id, now)

                    val weatherAlert = if (prefs.weatherEnabled) fetchWeatherAlert() else null
                    val calendarEvents = if (prefs.calendarEnabled) calendarRepository.getUpcomingEvents() else emptyList()

                    notificationManager.showExitReminder(
                        exitEventId = eventId,
                        profileId = profile.id,
                        profileName = profile.name,
                        items = profile.notifiableItems(),
                        snoozeMinutes = prefs.reminderSnoozeMinutes,
                        weatherAlert = weatherAlert,
                        upcomingEvents = calendarEvents
                    )
                }
            } finally {
                wifiProvider.stopMonitoring()
                motionProvider.stopMonitoring()
                pressureProvider.stopMonitoring()
                screenStateProvider.stopMonitoring()
                stepCountProvider.stopMonitoring()
                chargerStateProvider.stopMonitoring()
                ambientLightProvider.stopMonitoring()
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
