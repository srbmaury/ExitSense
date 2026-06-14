package com.exitsense.app.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.WorkManager
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.di.ApplicationScope
import com.exitsense.app.workers.ExitDetectionWorker
import com.exitsense.app.workers.LearningAnalysisWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Re-schedules WorkManager tasks after device reboot, since periodic work
 * is cancelled when the device restarts. Workers are only enqueued when
 * setup is complete to avoid unnecessary background work before first use.
 */
@AndroidEntryPoint
class BootReceiver : BroadcastReceiver() {

    @Inject lateinit var preferencesDataStore: UserPreferencesDataStore
    @Inject @ApplicationScope lateinit var applicationScope: CoroutineScope

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED &&
            intent?.action != Intent.ACTION_MY_PACKAGE_REPLACED) return

        val pendingResult = goAsync()
        applicationScope.launch {
            try {
                val prefs = preferencesDataStore.userPreferences.first()
                if (!prefs.isSetupComplete) return@launch

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
            } finally {
                pendingResult.finish()
            }
        }
    }
}
