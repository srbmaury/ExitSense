package com.exitsense.app.usecase

import android.net.Uri
import com.exitsense.app.data.backup.BackupRestoreSummary
import com.exitsense.app.data.backup.ImportResult
import com.exitsense.app.data.backup.ProfileBackupManager
import com.exitsense.app.data.backup.RestoreBackupUseCase
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.ExportBackupUseCase
import com.exitsense.app.domain.usecase.ImportBackupUseCase
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class BackupUseCasesTest {

    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var backupManager: ProfileBackupManager
    private lateinit var restoreBackup: RestoreBackupUseCase

    @Before
    fun setUp() {
        dataStore = mockk()
        reminderRepository = mockk()
        backupManager = mockk()
        restoreBackup = mockk()
    }

    @Test
    fun `export use case writes current preferences and profiles`() = runTest {
        val uri = mockk<Uri>()
        val prefs = UserPreferences(homeWifiSsid = "Home")
        val profiles = listOf(ReminderProfile(name = "Office"))
        every { dataStore.userPreferences } returns flowOf(prefs)
        every { reminderRepository.getAllProfiles() } returns flowOf(profiles)
        every { backupManager.exportToUri(prefs, profiles, uri) } returns Result.success(1)
        val useCase = ExportBackupUseCase(dataStore, reminderRepository, backupManager)

        val result = useCase(uri)

        assertTrue(result.isSuccess)
        assertEquals(1, result.getOrNull())
    }

    @Test
    fun `import use case parses file and restores backup`() = runTest {
        val uri = mockk<Uri>()
        val importResult = ImportResult(UserPreferences(homeWifiSsid = "Home"), emptyList())
        val summary = BackupRestoreSummary(addedProfileCount = 0, updatedProfileCount = 1, skippedProfileCount = 0)
        every { backupManager.importFromUri(uri) } returns Result.success(importResult)
        coEvery { restoreBackup(importResult, markSetupComplete = true) } returns summary
        val useCase = ImportBackupUseCase(backupManager, restoreBackup)

        val result = useCase(uri, markSetupComplete = true)

        assertTrue(result.isSuccess)
        assertEquals(summary, result.getOrNull())
        coVerify(exactly = 1) { restoreBackup(importResult, markSetupComplete = true) }
    }
}
