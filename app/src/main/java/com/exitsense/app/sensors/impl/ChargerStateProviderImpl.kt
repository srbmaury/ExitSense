package com.exitsense.app.sensors.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import androidx.core.content.ContextCompat
import com.exitsense.app.sensors.ChargerData
import com.exitsense.app.sensors.ChargerStateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ChargerStateProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ChargerStateProvider {

    private val _chargerData = MutableStateFlow(ChargerData())
    override val chargerData: StateFlow<ChargerData> = _chargerData

    @Volatile private var isRunning = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_POWER_DISCONNECTED ->
                    _chargerData.update { it.copy(lastUnpluggedAt = System.currentTimeMillis()) }
                Intent.ACTION_POWER_CONNECTED ->
                    _chargerData.update { it.copy(lastUnpluggedAt = 0L) }
            }
        }
    }

    override fun startMonitoring() {
        if (isRunning) return
        isRunning = true

        // Seed with current charging state so first evaluation is accurate
        val batteryStatus = ContextCompat.registerReceiver(
            context, null,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        val isCharging = batteryStatus?.let {
            val status = it.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
            status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL
        } ?: true
        if (!isCharging) {
            _chargerData.update { it.copy(lastUnpluggedAt = System.currentTimeMillis()) }
        }

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }

    override fun stopMonitoring() {
        if (!isRunning) return
        isRunning = false
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }
}
