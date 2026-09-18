package com.exitsense.app.rules

import com.exitsense.app.domain.model.MotionType
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.WifiState
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DepartureTrackerTest {

    private val home = WifiState(isConnected = true, ssid = "HomeWifi", networkId = 42)
    private val wifi = MutableStateFlow(home)
    private val motion = MutableStateFlow(MotionType.WALKING)

    private fun TestScope.startTracker(): DepartureTracker {
        val wifiProvider = mockk<WifiProvider> { every { wifiState } returns wifi }
        val motionProvider = mockk<MotionProvider> { every { currentMotion } returns motion }
        return DepartureTracker(wifiProvider, motionProvider, backgroundScope).also {
            it.start()
            runCurrent()
        }
    }

    private fun disconnect() {
        wifi.value = WifiState(isConnected = false, justDisconnected = true)
    }

    @Test
    fun `wifi drop opens a departure identifying the network left`() = runTest {
        val tracker = startTracker()
        assertTrue(tracker.isTracking)

        disconnect()
        runCurrent()

        val departure = tracker.departure.value
        assertNotNull(departure)
        assertEquals("HomeWifi", departure!!.fromSsid)
        assertEquals(42, departure.fromNetworkId)
    }

    @Test
    fun `departure stays open while the user keeps moving`() = runTest {
        val tracker = startTracker()
        disconnect()
        runCurrent()

        advanceTimeBy(DepartureTracker.SETTLE_MS * 3)
        runCurrent()

        assertNotNull(tracker.departure.value)
    }

    @Test
    fun `departure closes once the user has been still long enough`() = runTest {
        val tracker = startTracker()
        disconnect()
        motion.value = MotionType.STILL
        runCurrent()

        advanceTimeBy(DepartureTracker.SETTLE_MS - 1_000)
        runCurrent()
        assertNotNull(tracker.departure.value)

        advanceTimeBy(2_000)
        runCurrent()
        assertNull(tracker.departure.value)
    }

    @Test
    fun `moving again resets the settle timer`() = runTest {
        val tracker = startTracker()
        disconnect()
        motion.value = MotionType.STILL
        runCurrent()
        advanceTimeBy(DepartureTracker.SETTLE_MS - 1_000)

        motion.value = MotionType.WALKING
        runCurrent()
        motion.value = MotionType.STILL
        runCurrent()
        advanceTimeBy(DepartureTracker.SETTLE_MS - 1_000)
        runCurrent()

        assertNotNull(tracker.departure.value)
    }

    @Test
    fun `later walk after settling does not reopen the departure`() = runTest {
        val tracker = startTracker()
        disconnect()
        motion.value = MotionType.STILL
        runCurrent()
        advanceTimeBy(DepartureTracker.SETTLE_MS + 1_000)
        runCurrent()

        motion.value = MotionType.WALKING
        runCurrent()

        assertNull(tracker.departure.value)
    }

    @Test
    fun `consume closes the departure`() = runTest {
        val tracker = startTracker()
        disconnect()
        runCurrent()

        tracker.consume()

        assertNull(tracker.departure.value)
    }

    @Test
    fun `starting while already off wifi opens nothing`() = runTest {
        wifi.value = WifiState(isConnected = false)
        val tracker = startTracker()

        assertNull(tracker.departure.value)
    }

    @Test
    fun `stop ends tracking and clears the departure`() = runTest {
        val tracker = startTracker()
        disconnect()
        runCurrent()

        tracker.stop()

        assertFalse(tracker.isTracking)
        assertNull(tracker.departure.value)
    }
}
