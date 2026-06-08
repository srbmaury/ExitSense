package com.exitsense.app.data.local.dao

import androidx.room.*
import com.exitsense.app.data.local.entities.SensorSnapshotEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SensorSnapshotDao {

    @Insert
    suspend fun insertSnapshot(snapshot: SensorSnapshotEntity): Long

    @Query("SELECT * FROM sensor_snapshots ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentSnapshots(limit: Int = 100): Flow<List<SensorSnapshotEntity>>

    @Query("SELECT * FROM sensor_snapshots ORDER BY timestamp DESC LIMIT 1")
    suspend fun getLatestSnapshot(): SensorSnapshotEntity?

    @Query("DELETE FROM sensor_snapshots WHERE timestamp < :cutoffMs")
    suspend fun deleteOldSnapshots(cutoffMs: Long)
}
