package com.exitsense.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.*
import com.exitsense.app.domain.model.UserResponse
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.RecordUserResponseUseCase
import com.exitsense.app.workers.SnoozeWorker
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit
import javax.inject.Inject

@AndroidEntryPoint
class NotificationActionReceiver : BroadcastReceiver() {

    @Inject lateinit var notificationManager: ExitNotificationManager
    @Inject lateinit var reminderRepository: ReminderRepository
    @Inject lateinit var recordUserResponse: RecordUserResponseUseCase

    override fun onReceive(context: Context, intent: Intent) {
        val exitEventId = intent.getLongExtra(ExitNotificationManager.EXTRA_EXIT_EVENT_ID, -1L)
        val profileId = intent.getLongExtra(ExitNotificationManager.EXTRA_PROFILE_ID, -1L)

        when (intent.action) {
            ExitNotificationManager.ACTION_CONFIRM -> {
                notificationManager.dismissExitReminder()
                if (exitEventId == -1L || profileId == -1L) return
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
                    try {
                        val profile = reminderRepository.getProfileById(profileId) ?: return@launch
                        val responses = profile.items
                            .filter { it.isEnabled }
                            .map { item ->
                                UserResponse(
                                    exitEventId = exitEventId,
                                    itemId = item.id,
                                    profileId = profileId,
                                    wasConfirmed = true
                                )
                            }
                        if (responses.isNotEmpty()) {
                            recordUserResponse(responses, exitEventId)
                        }
                    } finally {
                        pendingResult.finish()
                    }
                }
            }
            ExitNotificationManager.ACTION_SNOOZE -> {
                notificationManager.dismissExitReminder()
                val snoozeMinutes = intent.getIntExtra(ExitNotificationManager.EXTRA_SNOOZE_MINUTES, 5)
                val snoozeWork = OneTimeWorkRequestBuilder<SnoozeWorker>()
                    .setInitialDelay(snoozeMinutes.toLong(), TimeUnit.MINUTES)
                    .setInputData(
                        workDataOf(
                            SnoozeWorker.KEY_EXIT_EVENT_ID to exitEventId,
                            SnoozeWorker.KEY_PROFILE_ID to profileId
                        )
                    )
                    .addTag(SnoozeWorker.TAG)
                    .build()
                WorkManager.getInstance(context).enqueue(snoozeWork)
            }
        }
    }
}
