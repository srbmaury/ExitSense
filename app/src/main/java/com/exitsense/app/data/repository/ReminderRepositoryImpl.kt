package com.exitsense.app.data.repository

import androidx.room.withTransaction
import com.exitsense.app.data.local.dao.ReminderItemDao
import com.exitsense.app.data.local.dao.ReminderProfileDao
import com.exitsense.app.data.local.database.AppDatabase
import com.exitsense.app.data.local.mapper.*
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ReminderRepositoryImpl @Inject constructor(
    private val database: AppDatabase,
    private val profileDao: ReminderProfileDao,
    private val itemDao: ReminderItemDao
) : ReminderRepository {

    override fun getAllProfiles(): Flow<List<ReminderProfile>> =
        profileDao.getAllProfiles().map { list ->
            list.map { entity ->
                val items = itemDao.getItemsByProfileSync(entity.id).map { it.toDomain() }
                entity.toDomain(items)
            }
        }

    override fun getActiveProfiles(): Flow<List<ReminderProfile>> =
        profileDao.getActiveProfiles().map { list ->
            list.map { entity ->
                val items = itemDao.getItemsByProfileSync(entity.id).map { it.toDomain() }
                entity.toDomain(items)
            }
        }

    override suspend fun getProfileById(id: Long): ReminderProfile? {
        val entity = profileDao.getProfileById(id) ?: return null
        val items = itemDao.getItemsByProfileSync(entity.id).map { it.toDomain() }
        return entity.toDomain(items)
    }

    override suspend fun saveProfile(profile: ReminderProfile): Long {
        val profileId = profileDao.insertProfile(profile.toEntity())
        val items = profile.items.map { it.copy(profileId = profileId).toEntity() }
        if (items.isNotEmpty()) itemDao.insertItems(items)
        return profileId
    }

    override suspend fun updateProfile(profile: ReminderProfile) {
        database.withTransaction {
            profileDao.updateProfile(profile.toEntity())
            itemDao.deleteItemsForProfile(profile.id)
            val items = profile.items.map { it.copy(profileId = profile.id).toEntity() }
            if (items.isNotEmpty()) itemDao.insertItems(items)
        }
    }

    override suspend fun deleteProfile(profileId: Long) {
        profileDao.deleteProfile(profileId)
    }

    override suspend fun toggleProfile(profileId: Long, isActive: Boolean) {
        profileDao.toggleProfile(profileId, isActive)
    }

    override fun getItemsForProfile(profileId: Long): Flow<List<ReminderItem>> =
        itemDao.getItemsByProfile(profileId).map { list -> list.map { it.toDomain() } }

    override suspend fun saveItem(item: ReminderItem): Long =
        itemDao.insertItem(item.toEntity())

    override suspend fun updateItem(item: ReminderItem) {
        itemDao.updateItem(item.toEntity())
    }

    override suspend fun deleteItem(itemId: Long) {
        itemDao.deleteItem(itemId)
    }

    override suspend fun updateItemLearnedPriority(itemId: Long, priority: Float) {
        itemDao.updateLearnedPriority(itemId, priority)
    }

    override suspend fun updateProfileLastNotifiedAt(profileId: Long, timestamp: Long) {
        profileDao.updateLastNotifiedAt(profileId, timestamp)
    }
}
