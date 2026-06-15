package com.exitsense.app.data.backup

import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

data class BackupRestoreSummary(
    val addedProfileCount: Int,
    val updatedProfileCount: Int,
    val skippedProfileCount: Int
)

class RestoreBackupUseCase @Inject constructor(
    private val dataStore: UserPreferencesDataStore,
    private val reminderRepository: ReminderRepository
) {
    suspend operator fun invoke(
        result: ImportResult,
        markSetupComplete: Boolean = false
    ): BackupRestoreSummary {
        restorePreferences(result, markSetupComplete)

        val existingByIdentity = reminderRepository.getAllProfiles()
            .first()
            .associateBy { it.importIdentity() }
            .toMutableMap()

        var added = 0
        var updated = 0
        var skipped = 0
        result.profiles.forEach { imported ->
            val identity = imported.importIdentity()
            val existing = existingByIdentity[identity]
            when {
                existing == null -> {
                    reminderRepository.saveProfile(imported)
                    existingByIdentity[identity] = imported
                    added++
                }
                existing.importContent() == imported.importContent() -> {
                    skipped++
                }
                else -> {
                    val updatedProfile = imported.copy(
                        id = existing.id,
                        createdAt = existing.createdAt,
                        lastNotifiedAt = existing.lastNotifiedAt,
                        items = imported.items.map { it.copy(profileId = existing.id) }
                    )
                    reminderRepository.updateProfile(updatedProfile)
                    existingByIdentity[identity] = updatedProfile
                    updated++
                }
            }
        }

        return BackupRestoreSummary(
            addedProfileCount = added,
            updatedProfileCount = updated,
            skippedProfileCount = skipped
        )
    }

    private suspend fun restorePreferences(result: ImportResult, markSetupComplete: Boolean) {
        val p = result.preferences
        dataStore.updateHomeWifiSsid(p.homeWifiSsid)
        dataStore.restoreHomeNetworkIds(p.homeNetworkIds)
        dataStore.updateHomeFloor(p.homeFloor)
        dataStore.updateConfidenceThreshold(p.exitConfidenceThreshold)
        dataStore.updateSnoozeMinutes(p.reminderSnoozeMinutes)
        dataStore.updateQuietHoursEnabled(p.quietHoursEnabled)
        dataStore.updateQuietHoursStartMinute(p.quietHoursStartMinute)
        dataStore.updateQuietHoursEndMinute(p.quietHoursEndMinute)
        dataStore.updateWeatherEnabled(p.weatherEnabled)
        dataStore.updateCalendarEnabled(p.calendarEnabled)
        if (markSetupComplete) dataStore.setSetupComplete(true)
    }
}

fun BackupRestoreSummary.toUserMessage(): String {
    val parts = buildList {
        if (addedProfileCount > 0) add("added $addedProfileCount profile${if (addedProfileCount == 1) "" else "s"}")
        if (updatedProfileCount > 0) add("updated $updatedProfileCount profile${if (updatedProfileCount == 1) "" else "s"}")
        if (skippedProfileCount > 0) add("skipped $skippedProfileCount already present")
    }
    val profileText = when {
        parts.isEmpty() -> "no profiles in backup"
        addedProfileCount == 0 && updatedProfileCount == 0 && skippedProfileCount > 0 ->
            "all $skippedProfileCount profile${if (skippedProfileCount == 1) " was" else "s were"} already present"
        else -> parts.joinToString(", ")
    }
    return "Imported settings; $profileText"
}

private fun ReminderProfile.importIdentity(): String =
    listOf(
        name.trim().lowercase(),
        scheduleType.name,
        activeDays.sorted().joinToString(","),
        startTimeHour,
        startTimeMinute,
        endTimeHour,
        endTimeMinute
    ).joinToString("#")

private fun ReminderProfile.importContent(): String =
    listOf(
        importIdentity(),
        isActive,
        items.map { it.importSignature() }.sorted().joinToString("|")
    ).joinToString("#")

private fun ReminderItem.importSignature(): String =
    listOf(
        name.trim().lowercase(),
        iconName,
        priority,
        isEnabled,
        learnedPriority
    ).joinToString(":")
