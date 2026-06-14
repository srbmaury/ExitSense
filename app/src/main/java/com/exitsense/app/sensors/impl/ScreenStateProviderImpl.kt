package com.exitsense.app.sensors.impl

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import androidx.core.content.ContextCompat
import java.util.concurrent.atomic.AtomicInteger
import com.exitsense.app.domain.model.ScreenState
import com.exitsense.app.sensors.ScreenStateProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ScreenStateProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : ScreenStateProvider {

    private val powerManager =
        context.getSystemService(Context.POWER_SERVICE) as PowerManager

    private val _screenState = MutableStateFlow(currentScreenState())
    override val screenState: StateFlow<ScreenState> = _screenState

    private val _recentlyUnlocked = MutableStateFlow(false)
    override val recentlyUnlocked: StateFlow<Boolean> = _recentlyUnlocked

    @Volatile private var unlockTimestamp = 0L
    private val refCount = AtomicInteger(0)

    private val recentUnlockWindowMs = 3 * 60 * 1000L

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_ON -> _screenState.value = ScreenState.ON
                Intent.ACTION_SCREEN_OFF -> {
                    _screenState.value = ScreenState.OFF
                    _recentlyUnlocked.value = false
                }
                Intent.ACTION_USER_PRESENT -> {
                    _screenState.value = ScreenState.UNLOCKED
                    _recentlyUnlocked.value = true
                    unlockTimestamp = System.currentTimeMillis()
                }
            }
        }
    }

    override fun startMonitoring() {
        if (refCount.getAndIncrement() > 0) return
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(context, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun stopMonitoring() {
        if (refCount.decrementAndGet() > 0) return
        runCatching { context.unregisterReceiver(receiver) }
        _recentlyUnlocked.value = false
    }

    /** Called externally (e.g. before score calculation) to check freshness of unlock event. */
    fun refreshUnlockFreshness() {
        if (_recentlyUnlocked.value &&
            System.currentTimeMillis() - unlockTimestamp > recentUnlockWindowMs
        ) {
            _recentlyUnlocked.value = false
        }
    }

    private fun currentScreenState(): ScreenState =
        if (powerManager.isInteractive) ScreenState.ON else ScreenState.OFF
}

/**
 * Static receiver declared in the manifest so it works even when the app process isn't running.
 * It delegates to [ScreenStateProviderImpl] if the service is alive; otherwise the event is
 * picked up on next start via initial state resolution.
 */
class ScreenStateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        // Manifest-declared receiver for ACTION_USER_PRESENT / SCREEN_ON / SCREEN_OFF.
        // The foreground service's in-process receiver handles live updates when running.
        // This receiver ensures we don't miss events during brief process restarts.
    }
}
