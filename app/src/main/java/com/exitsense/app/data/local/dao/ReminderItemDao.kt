package com.exitsense.app.data.local.dao

import androidx.room.*
import com.exitsense.app.data.local.entities.ReminderItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReminderItemDao {

    @Query("SELECT * FROM reminder_items WHERE profileId = :profileId ORDER BY priority DESC, name ASC")
    fun getItemsByProfile(profileId: Long): Flow<List<ReminderItemEntity>>

    @Query("SELECT * FROM reminder_items WHERE id = :id")
    suspend fun getItemById(id: Long): ReminderItemEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: ReminderItemEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<ReminderItemEntity>)

    @Update
    suspend fun updateItem(item: ReminderItemEntity)

    @Query("DELETE FROM reminder_items WHERE id = :id")
    suspend fun deleteItem(id: Long)

    @Query("UPDATE reminder_items SET learnedPriority = :priority WHERE id = :id")
    suspend fun updateLearnedPriority(id: Long, priority: Float)

    @Query("SELECT * FROM reminder_items WHERE profileId = :profileId")
    suspend fun getItemsByProfileSync(profileId: Long): List<ReminderItemEntity>
}
