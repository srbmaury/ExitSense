package com.exitsense.app.data.repository

import com.exitsense.app.data.local.dao.ExitEventDao
import com.exitsense.app.data.local.mapper.toDomain
import com.exitsense.app.data.local.mapper.toEntity
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.repository.ExitEventRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExitEventRepositoryImpl @Inject constructor(
    private val dao: ExitEventDao
) : ExitEventRepository {

    override fun getRecentExitEvents(limit: Int): Flow<List<ExitEvent>> =
        dao.getRecentEvents(limit).map { list -> list.map { it.toDomain() } }

    override suspend fun saveExitEvent(event: ExitEvent): Long =
        dao.insertEvent(event.toEntity())

    override suspend fun updateExitEvent(event: ExitEvent) {
        dao.updateEvent(event.toEntity())
    }

    override suspend fun getExitEventById(id: Long): ExitEvent? =
        dao.getEventById(id)?.toDomain()
}
