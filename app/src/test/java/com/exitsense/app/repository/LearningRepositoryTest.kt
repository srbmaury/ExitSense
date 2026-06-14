package com.exitsense.app.repository

import com.exitsense.app.data.local.dao.ReminderItemDao
import com.exitsense.app.data.local.dao.UserResponseDao
import com.exitsense.app.data.repository.LearningRepositoryImpl
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test

class LearningRepositoryTest {

    private lateinit var userResponseDao: UserResponseDao
    private lateinit var reminderItemDao: ReminderItemDao
    private lateinit var repo: LearningRepositoryImpl

    @Before
    fun setUp() {
        userResponseDao = mockk()
        reminderItemDao = mockk()
        repo = LearningRepositoryImpl(userResponseDao, reminderItemDao)
    }

    // Formula: max(0.2, min(2.0, 0.2 + 1.8 * rate))

    @Test
    fun `rate 0 clamps to minimum multiplier 0_2`() = runTest {
        coEvery { userResponseDao.getDistinctItemIds(1L) } returns listOf(10L)
        coEvery { userResponseDao.getConfirmationRate(10L, 1L) } returns 0.0f
        coEvery { reminderItemDao.updateLearnedPriority(10L, any()) } returns Unit

        repo.analyzeAndUpdatePriorities(1L)

        coVerify { reminderItemDao.updateLearnedPriority(10L, 0.2f) }
    }

    @Test
    fun `rate 1_0 clamps to maximum multiplier 2_0`() = runTest {
        coEvery { userResponseDao.getDistinctItemIds(1L) } returns listOf(10L)
        coEvery { userResponseDao.getConfirmationRate(10L, 1L) } returns 1.0f
        coEvery { reminderItemDao.updateLearnedPriority(10L, any()) } returns Unit

        repo.analyzeAndUpdatePriorities(1L)

        coVerify { reminderItemDao.updateLearnedPriority(10L, 2.0f) }
    }

    @Test
    fun `rate 0_5 produces midpoint multiplier 1_1`() = runTest {
        coEvery { userResponseDao.getDistinctItemIds(1L) } returns listOf(10L)
        coEvery { userResponseDao.getConfirmationRate(10L, 1L) } returns 0.5f
        coEvery { reminderItemDao.updateLearnedPriority(10L, any()) } returns Unit

        repo.analyzeAndUpdatePriorities(1L)

        // 0.2 + 1.8 * 0.5 = 1.1
        coVerify { reminderItemDao.updateLearnedPriority(10L, 1.1f) }
    }

    @Test
    fun `null confirmation rate skips update for that item`() = runTest {
        coEvery { userResponseDao.getDistinctItemIds(1L) } returns listOf(10L)
        coEvery { userResponseDao.getConfirmationRate(10L, 1L) } returns null

        repo.analyzeAndUpdatePriorities(1L)

        coVerify(exactly = 0) { reminderItemDao.updateLearnedPriority(any(), any()) }
    }

    @Test
    fun `multiple items each get independent priority updates`() = runTest {
        coEvery { userResponseDao.getDistinctItemIds(1L) } returns listOf(10L, 20L, 30L)
        coEvery { userResponseDao.getConfirmationRate(10L, 1L) } returns 0.0f  // → 0.2
        coEvery { userResponseDao.getConfirmationRate(20L, 1L) } returns null  // → skip
        coEvery { userResponseDao.getConfirmationRate(30L, 1L) } returns 1.0f  // → 2.0
        coEvery { reminderItemDao.updateLearnedPriority(any(), any()) } returns Unit

        repo.analyzeAndUpdatePriorities(1L)

        coVerify { reminderItemDao.updateLearnedPriority(10L, 0.2f) }
        coVerify(exactly = 0) { reminderItemDao.updateLearnedPriority(20L, any()) }
        coVerify { reminderItemDao.updateLearnedPriority(30L, 2.0f) }
    }
}
