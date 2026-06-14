package com.exitsense.app.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "exit_events",
    foreignKeys = [
        ForeignKey(
            entity = ReminderProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [Index("profileId")]
)
data class ExitEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Float,
    val triggeredSignals: String, // JSON array of ExitSignalType names
    val notificationShown: Boolean = false,
    val profileId: Long? = null,
    val userResponded: Boolean = false
)
