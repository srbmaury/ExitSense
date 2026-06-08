package com.exitsense.app.domain.usecase

import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.repository.ReminderRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

/**
 * Returns items for a profile sorted by effective priority (base × learned multiplier).
 * Items consistently confirmed get surfaced first; rarely-needed items sink.
 */
class GetItemRecommendationsUseCase @Inject constructor(
    private val reminderRepository: ReminderRepository,
    private val learningRepository: LearningRepository
) {
    operator fun invoke(profileId: Long): Flow<List<ReminderItem>> =
        reminderRepository.getItemsForProfile(profileId).map { items ->
            items.filter { it.isEnabled }
                .sortedByDescending { it.effectivePriority }
        }
}
