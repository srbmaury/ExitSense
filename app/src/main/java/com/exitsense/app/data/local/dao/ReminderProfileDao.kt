package com.exitsense.app.data.local.dao

import androidx.room.*
import com.exitsense.app.data.local.entities.ReminderProfileEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderProfileDao {

    @Query("SELECT * FROM reminder_profiles ORDER BY createdAt DESC")
    fun getAllProfiles(): Flow<List<ReminderProfileEntity>>

    @Query("SELECT * FROM reminder_profiles WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getActiveProfiles(): Flow<List<ReminderProfileEntity>>

    @Query("SELECT * FROM reminder_profiles WHERE id = :id")
    suspend fun getProfileById(id: Long): ReminderProfileEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: ReminderProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: ReminderProfileEntity)

    @Query("DELETE FROM reminder_profiles WHERE id = :id")
    suspend fun deleteProfile(id: Long)

    @Query("UPDATE reminder_profiles SET isActive = :isActive WHERE id = :id")
    suspend fun toggleProfile(id: Long, isActive: Boolean)

    @Query("UPDATE reminder_profiles SET lastNotifiedAt = :timestamp WHERE id = :id")
    suspend fun updateLastNotifiedAt(id: Long, timestamp: Long)
}
