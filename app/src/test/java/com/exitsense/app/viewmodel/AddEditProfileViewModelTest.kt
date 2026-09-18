package com.exitsense.app.viewmodel

import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.SaveProfileUseCase
import com.exitsense.app.presentation.profiles.AddEditProfileViewModel
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AddEditProfileViewModelTest {
    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var repository: ReminderRepository
    private lateinit var viewModel: AddEditProfileViewModel

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        repository = mockk(relaxed = true)
        coEvery { repository.saveProfile(any()) } returns 1L
        viewModel = AddEditProfileViewModel(
            reminderRepository = repository,
            saveProfileUseCase = SaveProfileUseCase(repository),
            learningRepository = mockk<LearningRepository>(relaxed = true)
        )
        viewModel.onNameChanged("Night shift")
        viewModel.onNewItemNameChanged("Badge")
        viewModel.addItem()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `overnight schedule can be saved`() = runTest {
        viewModel.onStartTimeChanged(22, 0)
        viewModel.onEndTimeChanged(2, 0)

        viewModel.saveProfile()

        coVerify {
            repository.saveProfile(match<ReminderProfile> {
                it.startTimeHour == 22 && it.endTimeHour == 2
            })
        }
    }

    @Test
    fun `equal start and end times are rejected`() = runTest {
        viewModel.onStartTimeChanged(8, 30)
        viewModel.onEndTimeChanged(8, 30)

        viewModel.saveProfile()

        assertEquals("Start and end time must be different", viewModel.uiState.value.error)
        coVerify(exactly = 0) { repository.saveProfile(any()) }
    }
}
