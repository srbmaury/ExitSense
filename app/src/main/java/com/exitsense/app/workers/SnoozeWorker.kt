package com.exitsense.app.workers

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.notifications.ExitNotificationManager
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.flow.first

@HiltWorker
class SnoozeWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val exitEventRepository: ExitEventRepository,
    private val reminderRepository: ReminderRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: ExitNotificationManager
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "snooze_worker"
        const val KEY_EXIT_EVENT_ID = "exit_event_id"
        const val KEY_PROFILE_ID = "profile_id"
    }

    override suspend fun doWork(): Result {
        val exitEventId = inputData.getLong(KEY_EXIT_EVENT_ID, -1L)
        val profileId = inputData.getLong(KEY_PROFILE_ID, -1L)
        if (exitEventId == -1L || profileId == -1L) return Result.failure()

        val profile = reminderRepository.getProfileById(profileId) ?: return Result.failure()
        val prefs = preferencesDataStore.userPreferences.first()

        notificationManager.showExitReminder(
            exitEventId = exitEventId,
            profileId = profileId,
            profileName = profile.name,
            items = profile.items.filter { it.isEnabled }
                .sortedByDescending { it.effectivePriority },
            snoozeMinutes = prefs.reminderSnoozeMinutes
        )
        return Result.success()
    }
}
