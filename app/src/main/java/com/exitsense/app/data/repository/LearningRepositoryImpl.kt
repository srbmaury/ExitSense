package com.exitsense.app.data.repository

import com.exitsense.app.data.local.dao.ReminderItemDao
import com.exitsense.app.data.local.dao.UserResponseDao
import com.exitsense.app.data.local.mapper.toDomain
import com.exitsense.app.data.local.mapper.toEntity
import com.exitsense.app.domain.model.UserResponse
import com.exitsense.app.domain.repository.LearningRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min

@Singleton
class LearningRepositoryImpl @Inject constructor(
    private val userResponseDao: UserResponseDao,
    private val reminderItemDao: ReminderItemDao
) : LearningRepository {

    override suspend fun recordUserResponse(response: UserResponse) {
        userResponseDao.insertResponse(response.toEntity())
    }

    override fun getResponsesForItem(itemId: Long): Flow<List<UserResponse>> =
        userResponseDao.getResponsesForItem(itemId).map { list -> list.map { it.toDomain() } }

    override suspend fun getConfirmationRateForItem(itemId: Long, profileId: Long): Float =
        userResponseDao.getConfirmationRate(itemId, profileId) ?: 1.0f

    /**
     * Adjusts learnedPriority for every item in the profile based on recent confirmation rates.
     * Rate ≥ 0.8  → priority multiplier approaches 2.0 (always needed).
     * Rate ≤ 0.2  → priority multiplier approaches 0.2 (rarely needed, kept but de-ranked).
     * Items with no history stay at 1.0.
     */
    override suspend fun analyzeAndUpdatePriorities(profileId: Long) {
        val itemIds = userResponseDao.getDistinctItemIds(profileId)
        itemIds.forEach { itemId ->
            val rate = userResponseDao.getConfirmationRate(itemId, profileId) ?: return@forEach
            // Sigmoid-inspired scaling: maps [0.0, 1.0] confirmation rate to [0.2, 2.0]
            val newPriority = max(0.2f, min(2.0f, 0.2f + 1.8f * rate))
            reminderItemDao.updateLearnedPriority(itemId, newPriority)
        }
    }
}
