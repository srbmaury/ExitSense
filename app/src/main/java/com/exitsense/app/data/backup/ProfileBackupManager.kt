package com.exitsense.app.data.backup

import android.content.Context
import android.net.Uri
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.ScheduleType
import com.exitsense.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

// ── On-disk format ────────────────────────────────────────────────────────────

@Serializable
data class AppBackupFile(
    val version: Int = 1,
    val exportedAt: Long,
    val preferences: PreferencesDto,
    val profiles: List<ProfileDto>
)

@Serializable
data class PreferencesDto(
    val homeWifiSsid: String,
    val homeNetworkIds: List<Int>,
    val homeFloor: Int,
    val exitConfidenceThreshold: Float,
    val reminderSnoozeMinutes: Int,
    val quietHoursEnabled: Boolean,
    val quietHoursStartMinute: Int,
    val quietHoursEndMinute: Int,
    val weatherEnabled: Boolean,
    val calendarEnabled: Boolean
)

@Serializable
data class ProfileDto(
    val name: String,
    val isActive: Boolean,
    val scheduleType: String,
    val activeDays: List<Int>,
    val startTimeHour: Int,
    val startTimeMinute: Int,
    val endTimeHour: Int,
    val endTimeMinute: Int,
    val items: List<ItemDto>
)

@Serializable
data class ItemDto(
    val name: String,
    val iconName: String,
    val priority: Int,
    val isEnabled: Boolean,
    val learnedPriority: Float = 1.0f
)

// ─────────────────────────────────────────────────────────────────────────────

data class ImportResult(
    val preferences: UserPreferences,
    val profiles: List<ReminderProfile>
)

@Singleton
class ProfileBackupManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { prettyPrint = true; ignoreUnknownKeys = true }

    fun exportToUri(
        preferences: UserPreferences,
        profiles: List<ReminderProfile>,
        uri: Uri
    ): Result<Int> = runCatching {
        val file = AppBackupFile(
            exportedAt = System.currentTimeMillis(),
            preferences = PreferencesDto(
                homeWifiSsid = preferences.homeWifiSsid,
                homeNetworkIds = preferences.homeNetworkIds.sorted(),
                homeFloor = preferences.homeFloor,
                exitConfidenceThreshold = preferences.exitConfidenceThreshold,
                reminderSnoozeMinutes = preferences.reminderSnoozeMinutes,
                quietHoursEnabled = preferences.quietHoursEnabled,
                quietHoursStartMinute = preferences.quietHoursStartMinute,
                quietHoursEndMinute = preferences.quietHoursEndMinute,
                weatherEnabled = preferences.weatherEnabled,
                calendarEnabled = preferences.calendarEnabled
            ),
            profiles = profiles.map { p ->
                ProfileDto(
                    name = p.name,
                    isActive = p.isActive,
                    scheduleType = p.scheduleType.name,
                    activeDays = p.activeDays.sorted(),
                    startTimeHour = p.startTimeHour,
                    startTimeMinute = p.startTimeMinute,
                    endTimeHour = p.endTimeHour,
                    endTimeMinute = p.endTimeMinute,
                    items = p.items.map { item ->
                        ItemDto(
                            name = item.name,
                            iconName = item.iconName,
                            priority = item.priority,
                            isEnabled = item.isEnabled,
                            learnedPriority = item.learnedPriority
                        )
                    }
                )
            }
        )
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Could not write backup file")
        stream.use {
            stream.write(json.encodeToString(file).toByteArray(Charsets.UTF_8))
        }
        profiles.size
    }

    fun importFromUri(uri: Uri): Result<ImportResult> = runCatching {
        val text = context.contentResolver.openInputStream(uri)
            ?.use { it.readBytes().toString(Charsets.UTF_8) }
            ?: error("Could not read backup file")

        val file = json.decodeFromString<AppBackupFile>(text)
        val p = file.preferences

        val prefs = UserPreferences(
            homeWifiSsid = p.homeWifiSsid,
            homeNetworkIds = p.homeNetworkIds.toSet(),
            homeFloor = p.homeFloor,
            exitConfidenceThreshold = p.exitConfidenceThreshold,
            reminderSnoozeMinutes = p.reminderSnoozeMinutes,
            quietHoursEnabled = p.quietHoursEnabled,
            quietHoursStartMinute = p.quietHoursStartMinute,
            quietHoursEndMinute = p.quietHoursEndMinute,
            weatherEnabled = p.weatherEnabled,
            calendarEnabled = p.calendarEnabled
        )

        val profiles = file.profiles.map { dto ->
            ReminderProfile(
                name = dto.name,
                isActive = dto.isActive,
                scheduleType = runCatching { ScheduleType.valueOf(dto.scheduleType) }
                    .getOrDefault(ScheduleType.WEEKDAYS),
                activeDays = dto.activeDays.toSet(),
                startTimeHour = dto.startTimeHour,
                startTimeMinute = dto.startTimeMinute,
                endTimeHour = dto.endTimeHour,
                endTimeMinute = dto.endTimeMinute,
                items = dto.items.map { item ->
                    ReminderItem(
                        profileId = 0,
                        name = item.name,
                        iconName = item.iconName,
                        priority = item.priority,
                        isEnabled = item.isEnabled,
                        learnedPriority = item.learnedPriority
                    )
                }
            )
        }

        ImportResult(prefs, profiles)
    }
}
