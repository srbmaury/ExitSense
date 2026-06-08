package com.exitsense.app.usecase

import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.SaveProfileUseCase
import io.mockk.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class SaveProfileUseCaseTest {

    private lateinit var repository: ReminderRepository
    private lateinit var useCase: SaveProfileUseCase

    @Before
    fun setUp() {
        repository = mockk()
        useCase = SaveProfileUseCase(repository)
    }

    @Test
    fun `new profile is saved with generated id`() = runTest {
        val profile = ReminderProfile(
            id = 0L,
            name = "Office",
            items = listOf(ReminderItem(profileId = 0, name = "Laptop"))
        )
        coEvery { repository.saveProfile(any()) } returns 42L

        val result = useCase(profile)

        assertTrue(result.isSuccess)
        assertEquals(42L, result.getOrNull())
        coVerify(exactly = 1) { repository.saveProfile(profile) }
    }

    @Test
    fun `existing profile calls update not save`() = runTest {
        val profile = ReminderProfile(
            id = 5L,
            name = "Gym",
            items = listOf(ReminderItem(profileId = 5, name = "Shoes"))
        )
        coEvery { repository.updateProfile(any()) } just Runs

        val result = useCase(profile)

        assertTrue(result.isSuccess)
        assertEquals(5L, result.getOrNull())
        coVerify(exactly = 1) { repository.updateProfile(profile) }
        coVerify(exactly = 0) { repository.saveProfile(any()) }
    }

    @Test
    fun `blank name returns failure`() = runTest {
        val profile = ReminderProfile(
            id = 0L,
            name = "",
            items = listOf(ReminderItem(profileId = 0, name = "Laptop"))
        )

        val result = useCase(profile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("name") == true)
        coVerify(exactly = 0) { repository.saveProfile(any()) }
    }

    @Test
    fun `empty items list returns failure`() = runTest {
        val profile = ReminderProfile(id = 0L, name = "Office", items = emptyList())

        val result = useCase(profile)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("item") == true)
    }
}
