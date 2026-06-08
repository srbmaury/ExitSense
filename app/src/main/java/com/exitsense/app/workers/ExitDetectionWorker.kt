package com.exitsense.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.exitsense.app.data.local.dao.SensorSnapshotDao
import com.exitsense.app.data.local.mapper.toEntity
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.SensorSnapshot
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.TimeRuleEvaluator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
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
    private val notificationManager: ExitNotificationManager
) : CoroutineWorker(context, params) {

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

            // Respect cooldown so we don't spam the user
            val now = System.currentTimeMillis()
            val activeProfiles = reminderRepository.getActiveProfiles().first()
            if (activeProfiles.isEmpty()) return Result.success()

            val result = exitDetector.evaluate(
                activeProfiles = activeProfiles,
                homeWifiSsid = prefs.homeWifiSsid,
                threshold = prefs.exitConfidenceThreshold
            )

            // Always persist sensor snapshot for debugging / history
            val snapshot = SensorSnapshot(
                confidenceScore = result.confidenceScore
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

                notificationManager.showExitReminder(
                    exitEventId = eventId,
                    profileId = profile.id,
                    profileName = profile.name,
                    items = profile.items.filter { it.isEnabled }
                        .sortedByDescending { it.effectivePriority },
                    snoozeMinutes = prefs.reminderSnoozeMinutes
                )
            }

            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
