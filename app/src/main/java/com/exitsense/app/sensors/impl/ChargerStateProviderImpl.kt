package com.exitsense.app.sensors.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger
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

    private val refCount = AtomicInteger(0)

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
        if (refCount.getAndIncrement() > 0) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun stopMonitoring() {
        if (refCount.decrementAndGet() > 0) return
        try { context.unregisterReceiver(receiver) } catch (_: IllegalArgumentException) {}
    }
}
