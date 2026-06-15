package com.exitsense.app.usecase

import com.exitsense.app.data.backup.ImportResult
import com.exitsense.app.data.backup.RestoreBackupUseCase
import com.exitsense.app.data.backup.toUserMessage
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.domain.repository.ReminderRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import io.mockk.Runs
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class RestoreBackupUseCaseTest {

    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var useCase: RestoreBackupUseCase

    @Before
    fun setUp() {
        dataStore = mockk(relaxed = true)
        reminderRepository = mockk()
        useCase = RestoreBackupUseCase(dataStore, reminderRepository)
    }

    @Test
    fun `new imported profile is added`() = runTest {
        val imported = profile(name = "Office", items = listOf(item("Laptop")))
        everyProfiles(emptyList())
        coEvery { reminderRepository.saveProfile(any()) } returns 10L

        val summary = useCase(importResult(imported))

        assertEquals(1, summary.addedProfileCount)
        assertEquals(0, summary.updatedProfileCount)
        assertEquals(0, summary.skippedProfileCount)
        coVerify(exactly = 1) { reminderRepository.saveProfile(imported) }
        coVerify(exactly = 0) { reminderRepository.updateProfile(any()) }
    }

    @Test
    fun `same imported profile is skipped`() = runTest {
        val existing = profile(id = 7L, name = "Office", items = listOf(item("Laptop", profileId = 7L)))
        val imported = existing.copy(id = 0L, items = listOf(item("Laptop")))
        everyProfiles(listOf(existing))

        val summary = useCase(importResult(imported))

        assertEquals(0, summary.addedProfileCount)
        assertEquals(0, summary.updatedProfileCount)
        assertEquals(1, summary.skippedProfileCount)
        assertTrue(summary.toUserMessage().contains("already present"))
        coVerify(exactly = 0) { reminderRepository.saveProfile(any()) }
        coVerify(exactly = 0) { reminderRepository.updateProfile(any()) }
    }

    @Test
    fun `matching changed profile updates existing profile instead of duplicating`() = runTest {
        val existing = profile(id = 7L, name = "Office", items = listOf(item("Laptop", profileId = 7L)))
        val imported = profile(name = "Office", items = listOf(item("Laptop"), item("Badge")))
        everyProfiles(listOf(existing))
        coEvery { reminderRepository.updateProfile(any()) } just Runs

        val summary = useCase(importResult(imported))

        assertEquals(0, summary.addedProfileCount)
        assertEquals(1, summary.updatedProfileCount)
        assertEquals(0, summary.skippedProfileCount)
        coVerify(exactly = 0) { reminderRepository.saveProfile(any()) }
        coVerify(exactly = 1) {
            reminderRepository.updateProfile(
                match { profile ->
                    profile.id == 7L &&
                        profile.items.map { it.profileId }.all { it == 7L } &&
                        profile.items.any { it.name == "Badge" }
                }
            )
        }
    }

    @Test
    fun `setup import marks setup complete`() = runTest {
        everyProfiles(emptyList())

        useCase(importResult(), markSetupComplete = true)

        coVerify(exactly = 1) { dataStore.setSetupComplete(true) }
    }

    private fun everyProfiles(profiles: List<ReminderProfile>) {
        coEvery { reminderRepository.getAllProfiles() } returns flowOf(profiles)
        coEvery { reminderRepository.saveProfile(any()) } returns 1L
        coEvery { reminderRepository.updateProfile(any()) } just Runs
    }

    private fun importResult(vararg profiles: ReminderProfile): ImportResult =
        ImportResult(
            preferences = UserPreferences(
                homeWifiSsid = "Home",
                homeNetworkIds = setOf(4),
                homeFloor = 2,
                weatherEnabled = true,
                calendarEnabled = true
            ),
            profiles = profiles.toList()
        )

    private fun profile(
        id: Long = 0L,
        name: String,
        items: List<ReminderItem>
    ): ReminderProfile =
        ReminderProfile(
            id = id,
            name = name,
            startTimeHour = 8,
            startTimeMinute = 0,
            endTimeHour = 10,
            endTimeMinute = 0,
            items = items
        )

    private fun item(name: String, profileId: Long = 0L): ReminderItem =
        ReminderItem(profileId = profileId, name = name)
}
