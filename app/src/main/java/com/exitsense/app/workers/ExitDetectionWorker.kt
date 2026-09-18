package com.exitsense.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.exitsense.app.data.local.dao.SensorSnapshotDao
import com.exitsense.app.data.local.mapper.toEntity
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.SensorSnapshot
import com.exitsense.app.notifications.ExitReminderDispatcher
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.ScreenStateProvider
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.util.DebugLog
import kotlinx.coroutines.delay
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

/**
 * Low-power fallback for when real-time monitoring is off: every 15 minutes, take a quick
 * sensor reading and remind if the user appears to have left.
 */
@HiltWorker
class ExitDetectionWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val reminderDispatcher: ExitReminderDispatcher,
    private val sensorSnapshotDao: SensorSnapshotDao,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val wifiProvider: WifiProvider,
    private val motionProvider: MotionProvider,
    private val pressureProvider: PressureProvider,
    private val screenStateProvider: ScreenStateProvider,
    private val stepCountProvider: StepCountProvider,
    private val chargerStateProvider: ChargerStateProvider,
    private val ambientLightProvider: AmbientLightProvider
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "exit_detection_periodic"
        const val TAG = "exit_detection"

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
            if (prefs.isMonitoringEnabled) {
                // The monitoring service is watching continuously and owns reminders
                DebugLog.d("worker") { "skipped: monitoring service is on" }
                return Result.success()
            }

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

                val result = reminderDispatcher.evaluateAndNotify() ?: return Result.success()

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
