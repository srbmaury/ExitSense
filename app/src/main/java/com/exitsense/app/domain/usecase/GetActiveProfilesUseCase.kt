package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetActiveProfilesUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    operator fun invoke(): Flow<List<ReminderProfile>> = repository.getActiveProfiles()
}
