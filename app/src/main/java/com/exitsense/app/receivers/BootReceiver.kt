package com.exitsense.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.exitsense.app.workers.ExitDetectionWorker
import com.exitsense.app.workers.LearningAnalysisWorker

/**
 * Re-schedules WorkManager tasks after device reboot, since periodic work
 * is cancelled when the device restarts.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val workManager = WorkManager.getInstance(context)

        workManager.enqueueUniquePeriodicWork(
            ExitDetectionWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            ExitDetectionWorker.buildPeriodicRequest()
        )

        workManager.enqueueUniquePeriodicWork(
            LearningAnalysisWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            LearningAnalysisWorker.buildPeriodicRequest()
        )
    }
}
