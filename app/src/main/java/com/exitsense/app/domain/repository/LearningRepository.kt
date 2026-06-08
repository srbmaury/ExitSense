package com.exitsense.app.domain.repository

import com.exitsense.app.domain.model.UserResponse
import kotlinx.coroutines.flow.Flow

interface LearningRepository {
    suspend fun recordUserResponse(response: UserResponse)
    fun getResponsesForItem(itemId: Long): Flow<List<UserResponse>>
    suspend fun getConfirmationRateForItem(itemId: Long, profileId: Long): Float
    suspend fun analyzeAndUpdatePriorities(profileId: Long)
}
