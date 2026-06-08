package com.exitsense.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminder_items",
    foreignKeys = [
        ForeignKey(
            entity = ReminderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("profileId")]
)
data class ReminderItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val iconName: String = "check_circle",
    val priority: Int = 3,
    val isEnabled: Boolean = true,
    val learnedPriority: Float = 1.0f
)
