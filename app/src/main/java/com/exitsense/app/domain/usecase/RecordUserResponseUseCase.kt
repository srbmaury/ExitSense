package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.model.UserResponse
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.LearningRepository
import javax.inject.Inject

class RecordUserResponseUseCase @Inject constructor(
    private val learningRepository: LearningRepository,
    private val exitEventRepository: ExitEventRepository
) {
    suspend operator fun invoke(responses: List<UserResponse>, exitEventId: Long) {
        responses.forEach { learningRepository.recordUserResponse(it) }
        val event = exitEventRepository.getExitEventById(exitEventId) ?: return
        exitEventRepository.updateExitEvent(event.copy(userResponded = true))
        // Trigger lightweight priority analysis after each response batch
        responses.firstOrNull()?.profileId?.let {
            learningRepository.analyzeAndUpdatePriorities(it)
        }
    }
}
