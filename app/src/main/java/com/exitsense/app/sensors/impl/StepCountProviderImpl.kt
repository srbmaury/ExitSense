package com.exitsense.app.sensors.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.exitsense.app.sensors.StepCountProvider
import com.exitsense.app.sensors.StepData
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class StepCountProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : StepCountProvider, SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val stepDetector: Sensor? = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)

    private val _stepData = MutableStateFlow(StepData(isAvailable = stepDetector != null))
    override val stepData: StateFlow<StepData> = _stepData

    // Rolling timestamps of each detected step; pruned to a 60-second window
    private val stepTimestamps = ArrayDeque<Long>()
    private val windowMs = 60_000L

    @Volatile private var isRunning = false

    override fun startMonitoring() {
        if (isRunning || stepDetector == null) return
        isRunning = true
        sensorManager.registerListener(this, stepDetector, SensorManager.SENSOR_DELAY_NORMAL)
    }

    override fun stopMonitoring() {
        if (!isRunning) return
        isRunning = false
        sensorManager.unregisterListener(this)
        stepTimestamps.clear()
        _stepData.update { it.copy(stepsLastMinute = 0) }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_STEP_DETECTOR) return

        val now = System.currentTimeMillis()
        stepTimestamps.addLast(now)
        while (stepTimestamps.isNotEmpty() && now - stepTimestamps.first() > windowMs) {
            stepTimestamps.removeFirst()
        }
        _stepData.update { it.copy(stepsLastMinute = stepTimestamps.size, isAvailable = true) }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
