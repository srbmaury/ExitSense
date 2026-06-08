package com.exitsense.app.sensors.impl

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.exitsense.app.domain.model.MotionType
import com.exitsense.app.sensors.MotionProvider
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.sqrt

@Singleton
class MotionProviderImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : MotionProvider, SensorEventListener {

    private val sensorManager =
        context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val accelerometer: Sensor? =
        sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    private val _currentMotion = MutableStateFlow(MotionType.STILL)
    override val currentMotion: StateFlow<MotionType> = _currentMotion

    // Ring buffer of recent net-acceleration readings
    private val readings = ArrayDeque<Float>()
    private var lastClassifyMs = 0L

    companion object {
        private const val BUFFER_SIZE = 25
        private const val CLASSIFY_INTERVAL_MS = 2_000L
        // Net acceleration above gravity in m/s² thresholds
        private const val WALK_THRESHOLD = 1.2f
        private const val RUN_THRESHOLD = 4.5f
        // Sustained high magnitude in sparse context can indicate vehicle (low variance, non-zero)
        private const val DRIVE_VARIANCE_MAX = 0.8f
        private const val DRIVE_MAG_MIN = 0.3f
    }

    override fun startMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun stopMonitoring() {
        sensorManager.unregisterListener(this)
        readings.clear()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val netAccel = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)

        readings.addLast(netAccel)
        if (readings.size > BUFFER_SIZE) readings.removeFirst()

        val now = System.currentTimeMillis()
        if (now - lastClassifyMs >= CLASSIFY_INTERVAL_MS) {
            lastClassifyMs = now
            _currentMotion.value = classify()
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

    private fun classify(): MotionType {
        if (readings.size < 5) return MotionType.STILL
        val avg = readings.average().toFloat()
        val variance = readings.map { (it - avg) * (it - avg) }.average().toFloat()

        return when {
            avg > RUN_THRESHOLD -> MotionType.RUNNING
            avg > WALK_THRESHOLD -> MotionType.WALKING
            // Low variance with non-trivial sustained acceleration → vehicle
            variance < DRIVE_VARIANCE_MAX && avg > DRIVE_MAG_MIN -> MotionType.DRIVING
            else -> MotionType.STILL
        }
    }
}
