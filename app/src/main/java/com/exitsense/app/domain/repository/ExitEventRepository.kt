package com.exitsense.app.domain.repository

import com.exitsense.app.domain.model.ExitEvent
import kotlinx.coroutines.flow.Flow

interface ExitEventRepository {
    fun getRecentExitEvents(limit: Int = 50): Flow<List<ExitEvent>>
    suspend fun saveExitEvent(event: ExitEvent): Long
    suspend fun updateExitEvent(event: ExitEvent)
    suspend fun getExitEventById(id: Long): ExitEvent?
}
