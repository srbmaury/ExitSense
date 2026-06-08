package com.exitsense.app.rules

import com.exitsense.app.domain.model.*
import com.exitsense.app.rules.impl.ExitDetectorImpl
import com.exitsense.app.sensors.*
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.ChargerData
import com.exitsense.app.sensors.LightData
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.StepData
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class ExitDetectorTest {

    private lateinit var motionProvider: MotionProvider
    private lateinit var wifiProvider: WifiProvider
    private lateinit var pressureProvider: PressureProvider
    private lateinit var screenStateProvider: ScreenStateProvider
    private lateinit var stepCountProvider: StepCountProvider
    private lateinit var chargerStateProvider: ChargerStateProvider
    private lateinit var ambientLightProvider: AmbientLightProvider
    private lateinit var detector: ExitDetectorImpl

    private val defaultWeights = SignalWeight()
    private val testProfile = ReminderProfile(
        id = 1L,
        name = "Office",
        scheduleType = ScheduleType.ALL_DAYS,
        startTimeHour = 0,
        startTimeMinute = 0,
        endTimeHour = 23,
        endTimeMinute = 59,
        items = listOf(ReminderItem(id = 1, profileId = 1, name = "Laptop"))
    )

    @Before
    fun setUp() {
        motionProvider = mockk()
        wifiProvider = mockk()
        pressureProvider = mockk()
        screenStateProvider = mockk()
        stepCountProvider = mockk()
        chargerStateProvider = mockk()
        ambientLightProvider = mockk()

        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.STILL)
        every { wifiProvider.wifiState } returns MutableStateFlow(WifiState())
        every { pressureProvider.pressureData } returns MutableStateFlow(PressureData(isAvailable = false))
        every { screenStateProvider.screenState } returns MutableStateFlow(ScreenState.OFF)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(false)
        every { stepCountProvider.stepData } returns MutableStateFlow(StepData(isAvailable = false))
        every { chargerStateProvider.chargerData } returns MutableStateFlow(ChargerData(lastUnpluggedAt = 0L))
        every { ambientLightProvider.lightData } returns MutableStateFlow(LightData(isAvailable = false))

        detector = ExitDetectorImpl(
            motionProvider, wifiProvider, pressureProvider, screenStateProvider,
            stepCountProvider, chargerStateProvider, ambientLightProvider, defaultWeights
        )
    }

    @Test
    fun `wifi disconnected alone gives score 50 but does not reach threshold`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = false, justDisconnected = true)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            threshold = 70f
        )

        // Wi-Fi: +50, time window (ALL_DAYS 00:00-23:59 always matches): +5 = 55
        assertEquals(55f, result.confidenceScore, 0.1f)
        assertFalse(result.isExitDetected)
        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
    }

    @Test
    fun `wifi disconnected + walking + screen unlocked exceeds threshold`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = false, justDisconnected = true)
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.WALKING)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(true)

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            threshold = 70f
        )

        // 50 (wifi) + 15 (walking) + 5 (screen) + 5 (time window) = 75
        assertTrue(result.confidenceScore >= 70f)
        assertTrue(result.isExitDetected)
    }

    @Test
    fun `connected to home wifi suppresses exit even with other signals`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "HomeWifi")
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.RUNNING)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(true)

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertTrue(result.confidenceScore <= 0f)
    }

    @Test
    fun `barometer descent adds 20 points`() = runTest {
        every { pressureProvider.pressureData } returns MutableStateFlow(
            PressureData(isAvailable = true, isDescending = true)
        )
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = false, justDisconnected = true)
        )

        // wifi 50 + barometer 10 + time window 5 = 65
        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            threshold = 64f
        )

        assertTrue(result.signals.any { it.type == ExitSignalType.BAROMETER_DESCENT })
        assertTrue(result.isExitDetected)
        assertEquals(65f, result.confidenceScore, 0.1f)
    }

    @Test
    fun `no wifi signals produces only time-window score`() = runTest {
        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            threshold = 70f
        )

        // Only time window signal fires (ALL_DAYS profile covers current time)
        assertEquals(defaultWeights.withinTimeWindow, result.confidenceScore, 0.1f)
        assertFalse(result.isExitDetected)
    }

    @Test
    fun `empty profile list returns no exit`() = runTest {
        val result = detector.evaluate(
            activeProfiles = emptyList(),
            homeWifiSsid = "HomeWifi",
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertNull(result.matchedProfile)
    }

    @Test
    fun `driving motion contributes correct score`() = runTest {
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.DRIVING)

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            threshold = 70f
        )

        assertTrue(result.signals.any { it.type == ExitSignalType.MOTION_DRIVING })
        assertTrue(result.confidenceScore >= defaultWeights.motionDriving)
    }
}
