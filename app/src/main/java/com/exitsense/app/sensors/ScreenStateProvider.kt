package com.exitsense.app.sensors

import com.exitsense.app.domain.model.ScreenState
import kotlinx.coroutines.flow.StateFlow

interface ScreenStateProvider {
    val screenState: StateFlow<ScreenState>
    val recentlyUnlocked: StateFlow<Boolean>
    fun startMonitoring()
    fun stopMonitoring()
}
