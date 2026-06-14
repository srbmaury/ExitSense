package com.exitsense.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.exitsense.app.data.local.dao.ExitEventDao
import com.exitsense.app.data.local.dao.SensorSnapshotDao
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.repository.ReminderRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

@HiltWorker
class LearningAnalysisWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val learningRepository: LearningRepository,
    private val reminderRepository: ReminderRepository,
    private val sensorSnapshotDao: SensorSnapshotDao,
    private val exitEventDao: ExitEventDao
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "learning_analysis_periodic"
        const val TAG = "learning_analysis"

        fun buildPeriodicRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<LearningAnalysisWorker>(1, TimeUnit.DAYS)
                .addTag(TAG)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresCharging(true)
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()
    }

    override suspend fun doWork(): Result {
        return try {
            val profiles = reminderRepository.getAllProfiles().first()
            profiles.forEach { profile ->
                learningRepository.analyzeAndUpdatePriorities(profile.id)
            }
            val cutoff = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(30)
            sensorSnapshotDao.deleteOldSnapshots(cutoff)
            exitEventDao.deleteOldEvents(cutoff)
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
