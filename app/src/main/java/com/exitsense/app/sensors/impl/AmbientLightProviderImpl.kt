package com.exitsense.app.sensors.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.LightData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AmbientLightProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : AmbientLightProvider, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val lightSensor: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_LIGHT)

    private val _lightData = MutableStateFlow(LightData(isAvailable = lightSensor != null))
    override val lightData: StateFlow<LightData> = _lightData

    // Overcast outdoor day ≈ 1000 lux; clear indoor rooms typically < 500 lux
    private val outdoorThresholdLux = 3000f

    private val recentReadings = ArrayDeque<Float>()
    private val smoothingWindow = 5

    @Volatile private var isRunning = false

    override fun startMonitoring() {
        if (isRunning || lightSensor == null) return
        isRunning = true
        sensorManager.registerListener(this, lightSensor, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stopMonitoring() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        recentReadings.clear()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_LIGHT) return

        recentReadings.addLast(e.values[0])
        if (recentReadings.size > smoothingWindow) recentReadings.removeFirst()

        val smoothed = recentReadings.average().toFloat()
        _lightData.update {
            it.copy(
                luxLevel = smoothed,
                isOutdoor = smoothed >= outdoorThresholdLux,
                isAvailable = true
            )
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
