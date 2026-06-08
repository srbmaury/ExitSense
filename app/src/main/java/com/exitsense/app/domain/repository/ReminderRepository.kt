package com.exitsense.app.domain.repository

import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import kotlinx.coroutines.flow.Flow

interface ReminderRepository {
    fun getAllProfiles(): Flow<List<ReminderProfile>>
    fun getActiveProfiles(): Flow<List<ReminderProfile>>
    suspend fun getProfileById(id: Long): ReminderProfile?
    suspend fun saveProfile(profile: ReminderProfile): Long
    suspend fun updateProfile(profile: ReminderProfile)
    suspend fun deleteProfile(profileId: Long)
    suspend fun toggleProfile(profileId: Long, isActive: Boolean)

    fun getItemsForProfile(profileId: Long): Flow<List<ReminderItem>>
    suspend fun saveItem(item: ReminderItem): Long
    suspend fun updateItem(item: ReminderItem)
    suspend fun deleteItem(itemId: Long)
    suspend fun updateItemLearnedPriority(itemId: Long, priority: Float)
    suspend fun updateProfileLastNotifiedAt(profileId: Long, timestamp: Long)
}
