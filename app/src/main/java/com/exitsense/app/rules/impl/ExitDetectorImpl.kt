package com.exitsense.app.rules.impl

import com.exitsense.app.domain.model.ExitSignalType
import com.exitsense.app.domain.model.MotionType
import com.exitsense.app.domain.model.ReminderProfile
import com.exitsense.app.rules.*
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.PressureProvider
import com.exitsense.app.sensors.ScreenStateProvider
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.impl.ScreenStateProviderImpl
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExitDetectorImpl @Inject constructor(
    private val motionProvider: MotionProvider,
    private val wifiProvider: WifiProvider,
    private val pressureProvider: PressureProvider,
    private val screenStateProvider: ScreenStateProvider,
    private val stepCountProvider: StepCountProvider,
    private val chargerStateProvider: ChargerStateProvider,
    private val ambientLightProvider: AmbientLightProvider,
    private val weights: SignalWeight
) : ExitDetector {

    override suspend fun evaluate(
        activeProfiles: List<ReminderProfile>,
        homeWifiSsid: String,
        homeNetworkIds: Set<Int>,
        threshold: Float
    ): ExitDetectionResult {

        // Refresh stale unlock flag before scoring
        (screenStateProvider as? ScreenStateProviderImpl)?.refreshUnlockFreshness()

        val wifi = wifiProvider.wifiState.value

        // networkIds are available without location permission in NetworkCallback context.
        // SSID requires ACCESS_FINE_LOCATION + location services on API 29+, so use it only
        // as a fallback when no networkIds have been saved yet.
        val onHomeWifi = wifi.isConnected && (
            (homeNetworkIds.isNotEmpty() && wifi.networkId != -1 && wifi.networkId in homeNetworkIds) ||
            (homeNetworkIds.isEmpty() && matchesHomeWifiSsid(homeWifiSsid, wifi.ssid))
        )

        // Short-circuit: still on home Wi-Fi → definitely at home, skip all other checks
        if (onHomeWifi) {
            return ExitDetectionResult(
                confidenceScore = 0f,
                signals = listOf(ExitSignal(ExitSignalType.WIFI_CONNECTED_HOME, 0f, "On home Wi-Fi")),
                isExitDetected = false,
                matchedProfile = activeProfiles.firstOrNull()
            )
        }

        val signals = mutableListOf<ExitSignal>()
        var score = 0f

        // ── Wi-Fi signal ────────────────────────────────────────────────────
        // Fire when: explicitly disconnected, on cellular (not connected at all),
        // OR connected to a *different* known network (SSID readable and not home).
        val onDifferentKnownWifi = wifi.isConnected && (
            (homeNetworkIds.isNotEmpty() && wifi.networkId != -1 && wifi.networkId !in homeNetworkIds) ||
            (homeNetworkIds.isEmpty() && wifi.ssid != null && homeWifiSsid.isNotBlank() &&
                !matchesHomeWifiSsid(homeWifiSsid, wifi.ssid))
        )
        if (wifi.justDisconnected ||
            (!wifi.isConnected && homeWifiSsid.isNotBlank()) ||
            onDifferentKnownWifi
        ) {
            val s = weights.wifiDisconnected
            score += s
            signals += ExitSignal(ExitSignalType.WIFI_DISCONNECTED, s, "Left home Wi-Fi")
        }

        // ── Motion signal ───────────────────────────────────────────────────
        when (motionProvider.currentMotion.value) {
            MotionType.WALKING -> {
                val s = weights.motionWalking
                score += s
                signals += ExitSignal(ExitSignalType.MOTION_WALKING, s, "Walking detected")
            }
            MotionType.RUNNING -> {
                val s = weights.motionRunning
                score += s
                signals += ExitSignal(ExitSignalType.MOTION_RUNNING, s, "Running detected")
            }
            MotionType.DRIVING -> {
                val s = weights.motionDriving
                score += s
                signals += ExitSignal(ExitSignalType.MOTION_DRIVING, s, "Driving detected")
            }
            else -> {}
        }

        // ── Screen unlock signal ────────────────────────────────────────────
        if (screenStateProvider.recentlyUnlocked.value) {
            val s = weights.screenUnlocked
            score += s
            signals += ExitSignal(ExitSignalType.SCREEN_UNLOCKED, s, "Screen recently unlocked")
        }

        // ── Barometer / descent signal ──────────────────────────────────────
        val pressure = pressureProvider.pressureData.value
        if (pressure.isAvailable && pressure.isDescending) {
            val s = weights.barometerDescent
            score += s
            signals += ExitSignal(ExitSignalType.BAROMETER_DESCENT, s, "Descending floor")
        }

        // ── Step count signal ───────────────────────────────────────────────
        val steps = stepCountProvider.stepData.value
        if (steps.isAvailable && steps.stepsLastMinute >= 20) {
            val s = weights.stepCount
            score += s
            signals += ExitSignal(ExitSignalType.STEP_COUNT, s, "${steps.stepsLastMinute} steps/min")
        }

        // ── Charger unplugged signal ────────────────────────────────────────
        val charger = chargerStateProvider.chargerData.value
        val chargerWindowMs = 30 * 60 * 1000L
        if (charger.lastUnpluggedAt > 0 &&
            System.currentTimeMillis() - charger.lastUnpluggedAt < chargerWindowMs
        ) {
            val s = weights.chargerUnplugged
            score += s
            signals += ExitSignal(ExitSignalType.CHARGER_UNPLUGGED, s, "Unplugged recently")
        }

        // ── Ambient light signal ────────────────────────────────────────────
        val light = ambientLightProvider.lightData.value
        if (light.isAvailable && light.isOutdoor) {
            val s = weights.ambientLight
            score += s
            signals += ExitSignal(
                ExitSignalType.AMBIENT_LIGHT, s,
                "%.0f lux (outdoor)".format(light.luxLevel ?: 0f)
            )
        }

        // ── Time window signal ──────────────────────────────────────────────
        val matchedProfile = activeProfiles.firstOrNull { profile ->
            TimeRuleEvaluator.isWithinSchedule(profile)
        }
        if (matchedProfile != null) {
            val s = weights.withinTimeWindow
            score += s
            signals += ExitSignal(ExitSignalType.TIME_WINDOW_MATCH, s,
                "Within schedule for '${matchedProfile.name}'")
        }

        return ExitDetectionResult(
            confidenceScore = score,
            signals = signals,
            isExitDetected = score >= threshold,
            matchedProfile = matchedProfile ?: activeProfiles.firstOrNull()
        )
    }
}
