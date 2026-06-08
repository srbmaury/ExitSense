package com.exitsense.app.data.local.dao

import androidx.room.*
import com.exitsense.app.data.local.entities.ExitEventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ExitEventDao {

    @Query("SELECT * FROM exit_events ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentEvents(limit: Int): Flow<List<ExitEventEntity>>

    @Query("SELECT * FROM exit_events WHERE id = :id")
    suspend fun getEventById(id: Long): ExitEventEntity?

    @Insert
    suspend fun insertEvent(event: ExitEventEntity): Long

    @Update
    suspend fun updateEvent(event: ExitEventEntity)

    @Query("DELETE FROM exit_events WHERE timestamp < :cutoffMs")
    suspend fun deleteOldEvents(cutoffMs: Long)

    @Query("SELECT COUNT(*) FROM exit_events WHERE timestamp > :sinceMs")
    suspend fun countEventsSince(sinceMs: Long): Int
}
