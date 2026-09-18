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
import java.util.Calendar

class ExitDetectorTest {

    private lateinit var motionProvider: MotionProvider
    private lateinit var wifiProvider: WifiProvider
    private lateinit var pressureProvider: PressureProvider
    private lateinit var screenStateProvider: ScreenStateProvider
    private lateinit var stepCountProvider: StepCountProvider
    private lateinit var chargerStateProvider: ChargerStateProvider
    private lateinit var ambientLightProvider: AmbientLightProvider
    private lateinit var departureTracker: DepartureTracker
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

        departureTracker = mockk()
        every { departureTracker.isTracking } returns false
        every { departureTracker.departure } returns MutableStateFlow(null)

        detector = ExitDetectorImpl(
            motionProvider, wifiProvider, pressureProvider, screenStateProvider,
            stepCountProvider, chargerStateProvider, ambientLightProvider, defaultWeights,
            departureTracker
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
    fun `comma separated wifi names trim spaces and preserve underscores and digits`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "Airtel_Saur_2345")
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.RUNNING)

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "OfficeNet, Airtel_Saur_2345, Backup5G",
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_CONNECTED_HOME })
    }

    @Test
    fun `comma separated wifi names also work without spaces`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "B")
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "A,B,C",
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
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

    // ── networkId-based Wi-Fi detection ───────────────────────────────────────

    @Test
    fun `matching networkId suppresses exit even when SSID is null`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = null, networkId = 42)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            homeNetworkIds = setOf(42),
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_CONNECTED_HOME })
    }

    @Test
    fun `networkId not in saved set fires wifi disconnected signal`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = null, networkId = 99)
        )

        // wifi(50) + timeWindow(5) = 55
        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            homeNetworkIds = setOf(42),
            threshold = 70f
        )

        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
        assertEquals(55f, result.confidenceScore, 0.1f)
        assertFalse(result.isExitDetected)
    }

    @Test
    fun `any networkId in multi-set suppresses exit`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = null, networkId = 7)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            homeNetworkIds = setOf(3, 7, 42),  // 7 is in the set
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
    }

    @Test
    fun `empty networkId set falls back to SSID matching`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "HomeWifi", networkId = 7)
        )

        // homeNetworkIds empty → use SSID path → SSID matches → at home → score 0
        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            homeNetworkIds = emptySet(),
            threshold = 70f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
    }

    // ── Unidentifiable network ───────────────────────────────────────────────

    @Test
    fun `connected to unidentifiable wifi is treated as home`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = null, networkId = -1)
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.DRIVING)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(true)

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            homeNetworkIds = setOf(42),
            threshold = 30f
        )

        assertFalse(result.isExitDetected)
        assertEquals(0f, result.confidenceScore, 0.0f)
        assertEquals(listOf(ExitSignalType.WIFI_UNVERIFIED), result.signals.map { it.type })
    }

    @Test
    fun `ssid match counts as home when networkId is unreadable`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "HomeWifi", networkId = -1)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            homeNetworkIds = setOf(42),
            threshold = 70f
        )

        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_CONNECTED_HOME })
    }

    @Test
    fun `ssid match counts as home when saved networkId has changed`() = runTest {
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "HomeWifi", networkId = 43)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "HomeWifi",
            homeNetworkIds = setOf(42),
            threshold = 70f
        )

        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_CONNECTED_HOME })
        assertEquals(0f, result.confidenceScore, 0.0f)
    }

    // ── Supporting signals ───────────────────────────────────────────────────

    @Test
    fun `unlock charger and light are ignored without wifi or motion`() = runTest {
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(true)
        every { chargerStateProvider.chargerData } returns MutableStateFlow(
            ChargerData(lastUnpluggedAt = System.currentTimeMillis())
        )
        every { ambientLightProvider.lightData } returns MutableStateFlow(
            LightData(isAvailable = true, luxLevel = 20_000f, isOutdoor = true)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            threshold = 70f
        )

        val types = result.signals.map { it.type }
        assertFalse(ExitSignalType.SCREEN_UNLOCKED in types)
        assertFalse(ExitSignalType.CHARGER_UNPLUGGED in types)
        assertFalse(ExitSignalType.AMBIENT_LIGHT in types)
        assertEquals(defaultWeights.withinTimeWindow, result.confidenceScore, 0.1f)
    }

    @Test
    fun `unlock and charger count once the user is walking`() = runTest {
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.WALKING)
        every { screenStateProvider.recentlyUnlocked } returns MutableStateFlow(true)
        every { chargerStateProvider.chargerData } returns MutableStateFlow(
            ChargerData(lastUnpluggedAt = System.currentTimeMillis())
        )

        val result = detector.evaluate(
            activeProfiles = listOf(testProfile),
            homeWifiSsid = "",
            threshold = 70f
        )

        val types = result.signals.map { it.type }
        assertTrue(ExitSignalType.SCREEN_UNLOCKED in types)
        assertTrue(ExitSignalType.CHARGER_UNPLUGGED in types)
    }

    // ── Profile matching ─────────────────────────────────────────────────────

    @Test
    fun `no profile is matched outside every schedule`() = runTest {
        val offHours = Calendar.getInstance().get(Calendar.HOUR_OF_DAY).let { (it + 12) % 24 }
        val outOfSchedule = testProfile.copy(
            startTimeHour = offHours, startTimeMinute = 0,
            endTimeHour = offHours, endTimeMinute = 1
        )
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = false, justDisconnected = true)
        )

        val result = detector.evaluate(
            activeProfiles = listOf(outOfSchedule),
            homeWifiSsid = "HomeWifi",
            threshold = 70f
        )

        assertNull(result.matchedProfile)
    }

    // ── Departure tracking ───────────────────────────────────────────────────

    private fun tracking(departure: Departure?) {
        every { departureTracker.isTracking } returns true
        every { departureTracker.departure } returns MutableStateFlow(departure)
    }

    @Test
    fun `open departure from home wifi scores the wifi signal`() = runTest {
        tracking(Departure(startedAt = 0L, fromSsid = "HomeWifi", fromNetworkId = 42))

        val result = detector.evaluate(listOf(testProfile), "HomeWifi", setOf(42), threshold = 70f)

        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
    }

    @Test
    fun `being off wifi without an open departure does not score`() = runTest {
        tracking(null)
        every { wifiProvider.wifiState } returns MutableStateFlow(WifiState(isConnected = false))
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.WALKING)

        val result = detector.evaluate(listOf(testProfile), "HomeWifi", threshold = 70f)

        assertFalse(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
        assertFalse(result.isExitDetected)
    }

    @Test
    fun `departure from another network does not score`() = runTest {
        tracking(Departure(startedAt = 0L, fromSsid = "CafeWifi", fromNetworkId = 7))

        val result = detector.evaluate(listOf(testProfile), "HomeWifi", setOf(42), threshold = 70f)

        assertFalse(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
    }

    @Test
    fun `departure from an unidentified network counts as leaving home`() = runTest {
        tracking(Departure(startedAt = 0L, fromSsid = null, fromNetworkId = -1))

        val result = detector.evaluate(listOf(testProfile), "HomeWifi", setOf(42), threshold = 70f)

        assertTrue(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
    }

    @Test
    fun `sitting on office wifi does not score while tracking`() = runTest {
        tracking(null)
        every { wifiProvider.wifiState } returns MutableStateFlow(
            WifiState(isConnected = true, ssid = "OfficeWifi", networkId = 9)
        )
        every { motionProvider.currentMotion } returns MutableStateFlow(MotionType.WALKING)

        val result = detector.evaluate(listOf(testProfile), "HomeWifi", setOf(42), threshold = 70f)

        assertFalse(result.signals.any { it.type == ExitSignalType.WIFI_DISCONNECTED })
    }
}
