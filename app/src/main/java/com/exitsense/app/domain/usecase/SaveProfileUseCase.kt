package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import javax.inject.Inject

class SaveProfileUseCase @Inject constructor(
    private val repository: ReminderRepository
) {
    suspend operator fun invoke(profile: ReminderProfile): Result<Long> = runCatching {
        require(profile.name.isNotBlank()) { "Profile name cannot be empty" }
        require(profile.items.isNotEmpty()) { "Profile must have at least one item" }
        if (profile.id == 0L) {
            repository.saveProfile(profile)
        } else {
            repository.updateProfile(profile)
            profile.id
        }
    }
}
