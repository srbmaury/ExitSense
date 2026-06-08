package com.exitsense.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_profiles")
data class ReminderProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val isActive: Boolean = true,
    val scheduleType: String = "WEEKDAYS",
    val activeDays: String = "1,2,3,4,5",
    val startTimeHour: Int = 8,
    val startTimeMinute: Int = 0,
    val endTimeHour: Int = 10,
    val endTimeMinute: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastNotifiedAt: Long = 0L
)
