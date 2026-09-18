package com.exitsense.app.rules

import com.exitsense.app.di.ApplicationScope
import com.exitsense.app.domain.model.MotionType
import com.exitsense.app.sensors.MotionProvider
import com.exitsense.app.sensors.WifiProvider
import com.exitsense.app.sensors.WifiState
import com.exitsense.app.util.DebugLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/** A Wi-Fi drop that may be the user leaving; [fromSsid]/[fromNetworkId] identify the network left. */
data class Departure(
    val startedAt: Long,
    val fromSsid: String?,
    val fromNetworkId: Int
)

/**
 * Turns a Wi-Fi drop into a "departure" that lasts only as long as the user is on the move.
 *
 * A departure opens when Wi-Fi disconnects and closes when the user settles somewhere
 * (still for [SETTLE_MS]), when a reminder is shown for it ([consume]), or when the next drop
 * replaces it. That way the drop helps detect the walk out of the door, but can't combine
 * with an unrelated walk hours later.
 */
@Singleton
class DepartureTracker @Inject constructor(
    private val wifiProvider: WifiProvider,
    private val motionProvider: MotionProvider,
    @ApplicationScope private val scope: CoroutineScope
) {
    private val _departure = MutableStateFlow<Departure?>(null)
    val departure: StateFlow<Departure?> = _departure

    private var job: Job? = null

    /** False when nothing is watching Wi-Fi continuously (e.g. a one-off background check). */
    val isTracking: Boolean
        @Synchronized get() = job?.isActive == true

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch {
            launch { openOnWifiDrop() }
            launch { closeWhenSettled() }
        }
    }

    @Synchronized
    fun stop() {
        job?.cancel()
        job = null
        _departure.value = null
    }

    /** A reminder was shown for the current departure — it must not trigger another one. */
    fun consume() {
        _departure.value = null
    }

    private suspend fun openOnWifiDrop() {
        var lastConnected: WifiState? = null
        wifiProvider.wifiState.collect { wifi ->
            if (wifi.isConnected) {
                // Keep the latest identity: the SSID arrives after the initial connect update
                lastConnected = wifi
            } else {
                val from = lastConnected ?: return@collect
                lastConnected = null
                _departure.value = Departure(System.currentTimeMillis(), from.ssid, from.networkId)
                DebugLog.d("departure") { "opened: left ssid=${from.ssid} networkId=${from.networkId}" }
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun closeWhenSettled() {
        combine(_departure, motionProvider.currentMotion) { departure, motion ->
            departure?.takeIf { motion == MotionType.STILL }
        }
            .distinctUntilChanged()
            .collectLatest { stillDuring ->
                if (stillDuring == null) return@collectLatest
                delay(SETTLE_MS)
                if (_departure.compareAndSet(stillDuring, null)) {
                    DebugLog.d("departure") { "closed: user settled" }
                }
            }
    }

    companion object {
        const val SETTLE_MS = 5 * 60 * 1000L
    }
}
