package com.exitsense.app.viewmodel

import android.net.Uri
import com.exitsense.app.data.backup.BackupRestoreSummary
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.ImportBackupUseCase
import com.exitsense.app.presentation.setup.SetupStep
import com.exitsense.app.presentation.setup.SetupWizardViewModel
import com.exitsense.app.sensors.PressureData
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.WifiState
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SetupWizardViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()
    private lateinit var dataStore: UserPreferencesDataStore
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var wifiProvider: WifiProvider
    private lateinit var pressureProvider: PressureProvider
    private lateinit var importBackupUseCase: ImportBackupUseCase

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        dataStore = mockk(relaxed = true)
        reminderRepository = mockk(relaxed = true)
        wifiProvider = mockk(relaxed = true)
        pressureProvider = mockk()
        importBackupUseCase = mockk()

        every { wifiProvider.wifiState } returns MutableStateFlow(WifiState())
        every { wifiProvider.scanResults } returns MutableStateFlow(emptyList())
        every { pressureProvider.pressureData } returns MutableStateFlow(PressureData(isAvailable = false))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `successful import before setup shows message and moves to permissions`() = runTest {
        val uri = mockk<Uri>()
        coEvery { importBackupUseCase(uri, markSetupComplete = false) } returns Result.success(BackupRestoreSummary(
            addedProfileCount = 1,
            updatedProfileCount = 0,
            skippedProfileCount = 0
        ))
        every { dataStore.userPreferences } returns flowOf(UserPreferences(homeWifiSsid = "RestoredHome", homeFloor = 4))
        val viewModel = createViewModel()

        viewModel.importBackup(uri)

        assertTrue(!viewModel.uiState.value.isComplete)
        assertEquals(SetupStep.PERMISSIONS, viewModel.uiState.value.currentStep)
        assertEquals("RestoredHome", viewModel.uiState.value.homeWifiSsid)
        assertEquals(4, viewModel.uiState.value.homeFloor)
        assertEquals("Imported settings; added 1 profile — grant permissions then confirm your Wi-Fi to finish.", viewModel.uiState.value.importMessage)
        coVerify(exactly = 1) { importBackupUseCase(uri, markSetupComplete = false) }
    }

    @Test
    fun `failed import before setup keeps setup open and shows error`() = runTest {
        val uri = mockk<Uri>()
        coEvery { importBackupUseCase(uri, markSetupComplete = false) } returns Result.failure(IllegalArgumentException("bad file"))
        val viewModel = createViewModel()

        viewModel.importBackup(uri)

        assertTrue(!viewModel.uiState.value.isComplete)
        assertEquals("Import failed: bad file", viewModel.uiState.value.importMessage)
    }

    private fun createViewModel(): SetupWizardViewModel =
        SetupWizardViewModel(
            dataStore = dataStore,
            reminderRepository = reminderRepository,
            wifiProvider = wifiProvider,
            pressureProvider = pressureProvider,
            importBackupUseCase = importBackupUseCase
        )
}
