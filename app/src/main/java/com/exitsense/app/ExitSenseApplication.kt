package com.exitsense.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.workers.ExitDetectionWorker
import com.exitsense.app.workers.LearningAnalysisWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class ExitSenseApplication : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var notificationManager: ExitNotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager.createChannels()
        scheduleBackgroundWork()
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    private fun scheduleBackgroundWork() {
        val wm = WorkManager.getInstance(this)

        wm.enqueueUniquePeriodicWork(
            ExitDetectionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            ExitDetectionWorker.buildPeriodicRequest()
        )

        wm.enqueueUniquePeriodicWork(
            LearningAnalysisWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            LearningAnalysisWorker.buildPeriodicRequest()
        )
    }
}
