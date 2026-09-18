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
import com.exitsense.app.util.DebugLog
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
    private val weights: SignalWeight,
    private val departureTracker: DepartureTracker
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
        val homeConfigured = homeWifiSsid.isNotBlank() || homeNetworkIds.isNotEmpty()

        // Both networkId and SSID are redacted unless the app holds fine location (with location
        // services on) and can use it right now. Match on whichever identity is readable.
        val networkIdKnown = homeNetworkIds.isNotEmpty() && wifi.networkId != -1
        val ssidKnown = homeWifiSsid.isNotBlank() && wifi.ssid != null
        val onHomeWifi = wifi.isConnected && (
            (networkIdKnown && wifi.networkId in homeNetworkIds) ||
            (ssidKnown && matchesHomeWifiSsid(homeWifiSsid, wifi.ssid))
        )

        // Short-circuit: still on home Wi-Fi → definitely at home, skip all other checks
        if (onHomeWifi) {
            return atHome(ExitSignal(ExitSignalType.WIFI_CONNECTED_HOME, 0f, "On home Wi-Fi"))
        }

        // On Wi-Fi but the network can't be identified (no location access right now) →
        // most likely still home, so don't let the other signals add up to a false alarm.
        if (wifi.isConnected && homeConfigured && !networkIdKnown && !ssidKnown) {
            return atHome(ExitSignal(ExitSignalType.WIFI_UNVERIFIED, 0f, "On Wi-Fi, network unknown"))
        }

        val signals = mutableListOf<ExitSignal>()
        var score = 0f

        // ── Wi-Fi signal ────────────────────────────────────────────────────
        val leftHomeWifi = if (departureTracker.isTracking) {
            // Only a recent drop from the home network counts, and only until the user settles
            departureTracker.departure.value?.let { leftFromHome(it, homeWifiSsid, homeNetworkIds) } == true
        } else {
            // One-off check with no Wi-Fi history: disconnected, on cellular, or on another network
            val onDifferentWifi = wifi.isConnected && (networkIdKnown || ssidKnown)
            wifi.justDisconnected || (!wifi.isConnected && homeConfigured) || onDifferentWifi
        }
        if (leftHomeWifi) {
            val s = weights.wifiDisconnected
            score += s
            signals += ExitSignal(ExitSignalType.WIFI_DISCONNECTED, s, "Left home Wi-Fi")
        }

        // ── Motion signal ───────────────────────────────────────────────────
        val motion = motionProvider.currentMotion.value
        when (motion) {
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

        // Unlocking, unplugging and bright light happen all the time at home, so they only
        // count when Wi-Fi or motion already suggests the user is on the move.
        val isMoving = motion == MotionType.WALKING || motion == MotionType.RUNNING ||
            motion == MotionType.DRIVING
        val supportingSignalsAllowed = leftHomeWifi || isMoving

        // ── Screen unlock signal ────────────────────────────────────────────
        if (supportingSignalsAllowed && screenStateProvider.recentlyUnlocked.value) {
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
        if (supportingSignalsAllowed && charger.lastUnpluggedAt > 0 &&
            System.currentTimeMillis() - charger.lastUnpluggedAt < chargerWindowMs
        ) {
            val s = weights.chargerUnplugged
            score += s
            signals += ExitSignal(ExitSignalType.CHARGER_UNPLUGGED, s, "Unplugged recently")
        }

        // ── Ambient light signal ────────────────────────────────────────────
        val light = ambientLightProvider.lightData.value
        if (supportingSignalsAllowed && light.isAvailable && light.isOutdoor) {
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

        DebugLog.d("detector") {
            "score=$score threshold=$threshold wifi=$wifi signals=${signals.map { it.type }} " +
                "profile=${matchedProfile?.name}"
        }
        return ExitDetectionResult(
            confidenceScore = score,
            signals = signals,
            isExitDetected = score >= threshold,
            matchedProfile = matchedProfile
        )
    }

    /** Networks that couldn't be identified count as home, matching the check above. */
    private fun leftFromHome(departure: Departure, homeWifiSsid: String, homeNetworkIds: Set<Int>): Boolean {
        val networkIdKnown = homeNetworkIds.isNotEmpty() && departure.fromNetworkId != -1
        val ssidKnown = homeWifiSsid.isNotBlank() && departure.fromSsid != null
        if (!networkIdKnown && !ssidKnown) return true
        return (networkIdKnown && departure.fromNetworkId in homeNetworkIds) ||
            (ssidKnown && matchesHomeWifiSsid(homeWifiSsid, departure.fromSsid))
    }

    private fun atHome(signal: ExitSignal): ExitDetectionResult {
        DebugLog.d("detector") { "at home: ${signal.description}" }
        return ExitDetectionResult(
            confidenceScore = 0f,
            signals = listOf(signal),
            isExitDetected = false,
            matchedProfile = null
        )
    }
}
