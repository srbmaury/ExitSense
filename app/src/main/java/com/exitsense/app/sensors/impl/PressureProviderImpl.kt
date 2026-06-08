package com.exitsense.app.sensors.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.exitsense.app.sensors.PressureData
import com.exitsense.app.sensors.PressureProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PressureProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : PressureProvider, SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val pressureSensor: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

    private val _pressureData = MutableStateFlow(
        PressureData(isAvailable = pressureSensor != null)
    )
    override val pressureData: StateFlow<PressureData> = _pressureData

    // At sea level 1 hPa ≈ 8.5 m; one floor ≈ 3 m ≈ 0.35 hPa — use 0.3 to catch single floors
    private val floorDescentThresholdHpa = 0.3f

    // Smooth the sensor with a running average (last N readings)
    private val recentPressures = ArrayDeque<Float>()
    private val smoothingWindow = 4   // fewer readings → faster response

    @Volatile private var isRunning = false

    override fun startMonitoring() {
        if (isRunning || pressureSensor == null) return
        isRunning = true
        sensorManager.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stopMonitoring() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        recentPressures.clear()
    }

    override fun calibrateBaseline() {
        val current = _pressureData.value.currentPressure ?: return
        _pressureData.update { it.copy(baselinePressure = current, isDescending = false) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_PRESSURE) return

        val raw = e.values[0]
        recentPressures.addLast(raw)
        if (recentPressures.size > smoothingWindow) recentPressures.removeFirst()

        val smoothed = recentPressures.average().toFloat()
        val baseline = _pressureData.value.baselinePressure

        // Auto-calibrate on the first reading so baseline is never null
        if (baseline == null) {
            _pressureData.update { it.copy(baselinePressure = smoothed, currentPressure = smoothed, isAvailable = true) }
            return
        }

        // Higher pressure = lower altitude → device is descending
        val descending = (smoothed - baseline) > floorDescentThresholdHpa

        _pressureData.update { prev ->
            prev.copy(
                currentPressure = smoothed,
                isDescending = descending,
                isAvailable = true
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
