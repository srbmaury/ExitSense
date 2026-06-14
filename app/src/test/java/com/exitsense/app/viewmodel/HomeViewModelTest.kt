package com.exitsense.app.viewmodel

import android.content.Context
import app.cash.turbine.test
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.domain.model.*
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.domain.usecase.GetActiveProfilesUseCase
import com.exitsense.app.notifications.ExitNotificationManager
import com.exitsense.app.presentation.home.HomeViewModel
import com.exitsense.app.rules.ExitDetectionResult
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.ExitSignal
import com.exitsense.app.sensors.*
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.ChargerData
import com.exitsense.app.sensors.LightData
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.StepData
import io.mockk.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()

    private lateinit var context: Context
    private lateinit var notificationManager: ExitNotificationManager
    private lateinit var getActiveProfiles: GetActiveProfilesUseCase
    private lateinit var exitEventRepository: ExitEventRepository
    private lateinit var reminderRepository: ReminderRepository
    private lateinit var exitDetector: ExitDetector
    private lateinit var preferencesDataStore: UserPreferencesDataStore
    private lateinit var motionProvider: MotionProvider
    private lateinit var wifiProvider: WifiProvider
    private lateinit var screenStateProvider: ScreenStateProvider
    private lateinit var pressureProvider: PressureProvider
    private lateinit var stepCountProvider: StepCountProvider
    private lateinit var chargerStateProvider: ChargerStateProvider
    private lateinit var ambientLightProvider: AmbientLightProvider

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        context = mockk(relaxed = true)
        notificationManager = mockk(relaxed = true)
        getActiveProfiles = mockk()
        exitEventRepository = mockk()
        reminderRepository = mockk(relaxed = true)
        exitDetector = mockk()
        preferencesDataStore = mockk()
        motionProvider = mockk(relaxed = true)
        wifiProvider = mockk(relaxed = true)
        screenStateProvider = mockk(relaxed = true)
        pressureProvider = mockk(relaxed = true)
        stepCountProvider = mockk(relaxed = true)
        chargerStateProvider = mockk(relaxed = true)
        ambientLightProvider = mockk(relaxed = true)

        every { getActiveProfiles() } returns flowOf(emptyList())
        every { reminderRepository.getActiveProfiles() } returns flowOf(emptyList())
        every { exitEventRepository.getRecentExitEvents(any()) } returns flowOf(emptyList())
        every { preferencesDataStore.userPreferences } returns flowOf(UserPreferences(isSetupComplete = true))
        coEvery { exitDetector.evaluate(any(), any(), any(), any()) } returns ExitDetectionResult(
            confidenceScore = 0f, signals = emptyList<ExitSignal>(), isExitDetected = false
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.STILL)
        every { wifiProvider.wifiState } returns MutableStateFlow(WifiState())
        every { screenStateProvider.screenState } returns MutableStateFlow(ScreenState.OFF)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(false)
        every { pressureProvider.pressureData } returns MutableStateFlow(PressureData())
        every { stepCountProvider.stepData } returns MutableStateFlow(StepData())
        every { chargerStateProvider.chargerData } returns MutableStateFlow(ChargerData())
        every { ambientLightProvider.lightData } returns MutableStateFlow(LightData())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = HomeViewModel(
        context = context,
        getActiveProfiles = getActiveProfiles,
        exitEventRepository = exitEventRepository,
        reminderRepository = reminderRepository,
        exitDetector = exitDetector,
        preferencesDataStore = preferencesDataStore,
        notificationManager = notificationManager,
        motionProvider = motionProvider,
        wifiProvider = wifiProvider,
        screenStateProvider = screenStateProvider,
        pressureProvider = pressureProvider,
        stepCountProvider = stepCountProvider,
        chargerStateProvider = chargerStateProvider,
        ambientLightProvider = ambientLightProvider
    )

    @Test
    fun `initial state is not loading after preferences loaded`() = runTest {
        val viewModel = createViewModel()

        viewModel.uiState.test {
            val state = awaitItem()
            assertFalse(state.isLoading)
            assertTrue(state.isSetupComplete)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `motion state updates reflected in ui state`() = runTest {
        val motionFlow = MutableStateFlow(MotionType.STILL)
        every { motionProvider.currentMotion } returns motionFlow

        val viewModel = createViewModel()

        viewModel.uiState.test {
            // Consume initial state
            val initial = awaitItem()
            assertEquals(MotionType.STILL, initial.currentMotion)

            // Trigger a motion change
            motionFlow.value = MotionType.WALKING

            val updated = awaitItem()
            assertEquals(MotionType.WALKING, updated.currentMotion)
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun `runManualDetection updates detectionResult`() = runTest {
        val mockResult = ExitDetectionResult(
            confidenceScore = 80f,
            signals = emptyList(),
            isExitDetected = true
        )
        coEvery {
            exitDetector.evaluate(any(), any(), any(), any())
        } returns mockResult

        val viewModel = createViewModel()
        viewModel.runManualDetection()

        viewModel.uiState.test {
            val state = expectMostRecentItem()
            assertNotNull(state.detectionResult)
            assertEquals(80f, state.detectionResult?.confidenceScore)
            cancelAndIgnoreRemainingEvents()
        }
    }
}
