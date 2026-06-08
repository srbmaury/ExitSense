package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.repository.ExitEventRepository
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject

class GetExitHistoryUseCase @Inject constructor(
    private val repository: ExitEventRepository
) {
    operator fun invoke(limit: Int = 50): Flow<List<ExitEvent>> =
        repository.getRecentExitEvents(limit)
}
