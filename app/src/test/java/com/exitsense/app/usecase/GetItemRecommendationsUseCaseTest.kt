package com.exitsense.app.usecase

import app.cash.turbine.test
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.GetItemRecommendationsUseCase
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class GetItemRecommendationsUseCaseTest {

    private lateinit var reminderRepository: ReminderRepository
    private lateinit var learningRepository: LearningRepository
    private lateinit var useCase: GetItemRecommendationsUseCase

    @Before
    fun setUp() {
        reminderRepository = mockk()
        learningRepository = mockk(relaxed = true)
        useCase = GetItemRecommendationsUseCase(reminderRepository, learningRepository)
    }

    @Test
    fun `items sorted by effective priority descending`() = runTest {
        val low = ReminderItem(id = 1, profileId = 1, name = "Headphones", priority = 2, learnedPriority = 1.0f)   // effective 2
        val high = ReminderItem(id = 2, profileId = 1, name = "Laptop", priority = 3, learnedPriority = 2.0f)      // effective 6
        val mid = ReminderItem(id = 3, profileId = 1, name = "Badge", priority = 3, learnedPriority = 1.0f)        // effective 3

        every { reminderRepository.getItemsForProfile(1L) } returns flowOf(listOf(low, high, mid))

        useCase(1L).test {
            val result = awaitItem()
            assertEquals(listOf("Laptop", "Badge", "Headphones"), result.map { it.name })
            awaitComplete()
        }
    }

    @Test
    fun `disabled items are excluded`() = runTest {
        val enabled = ReminderItem(id = 1, profileId = 1, name = "Keys", isEnabled = true)
        val disabled = ReminderItem(id = 2, profileId = 1, name = "Notebook", isEnabled = false)

        every { reminderRepository.getItemsForProfile(1L) } returns flowOf(listOf(enabled, disabled))

        useCase(1L).test {
            val result = awaitItem()
            assertEquals(listOf("Keys"), result.map { it.name })
            awaitComplete()
        }
    }

    @Test
    fun `learned priority multiplies base priority in sort order`() = runTest {
        // base priority same (3) but different learnedPriority → effective differs
        val frequent = ReminderItem(id = 1, profileId = 1, name = "Wallet", priority = 3, learnedPriority = 2.0f)  // 6
        val rare = ReminderItem(id = 2, profileId = 1, name = "Umbrella", priority = 3, learnedPriority = 0.2f)    // 0.6

        every { reminderRepository.getItemsForProfile(1L) } returns flowOf(listOf(rare, frequent))

        useCase(1L).test {
            val result = awaitItem()
            assertEquals("Wallet", result.first().name)
            assertEquals("Umbrella", result.last().name)
            awaitComplete()
        }
    }
}
