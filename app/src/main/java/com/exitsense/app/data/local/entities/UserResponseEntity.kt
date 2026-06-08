package com.exitsense.app.data.local.entities

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "user_responses",
    indices = [Index("exitEventId"), Index("itemId"), Index("profileId")]
)
data class UserResponseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val exitEventId: Long,
    val itemId: Long,
    val profileId: Long,
    val wasConfirmed: Boolean,
    val respondedAt: Long = System.currentTimeMillis()
)
