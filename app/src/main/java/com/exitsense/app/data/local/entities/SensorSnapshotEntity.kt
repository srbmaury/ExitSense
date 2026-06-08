package com.exitsense.app.data.local.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sensor_snapshots")
data class SensorSnapshotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val wifiConnected: Boolean = false,
    val connectedSsid: String? = null,
    val motionType: String = "STILL",
    val screenState: String = "OFF",
    val pressure: Float? = null,
    val confidenceScore: Float = 0f
)
