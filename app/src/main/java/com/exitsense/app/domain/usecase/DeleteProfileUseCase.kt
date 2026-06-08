package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.repository.ReminderRepository
import javax.inject.Inject

class DeleteProfileUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(profileId: Long) {
        repository.deleteProfile(profileId)
    }
}
