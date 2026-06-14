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
import java.util.concurrent.atomic.AtomicInteger
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

    // Ring buffer and classifier state — written on the sensor binder thread, cleared on the
    // calling thread in stopMonitoring(). All access is guarded by this lock.
    private val lock = Any()
    private val readings = ArrayDeque<Float>()
    private var lastClassifyMs = 0L
    private var pendingMotion = MotionType.STILL
    private var pendingCount = 0
    private val refCount = AtomicInteger(0)

    companion object {
        private const val BUFFER_SIZE = 30
        private const val CLASSIFY_INTERVAL_MS = 2_000L
        // Net acceleration above gravity in m/s²
        private const val WALK_THRESHOLD = 1.4f   // ~0.14g — deliberate walking cadence
        private const val RUN_THRESHOLD = 6.0f
        // Driving: very low variance (smooth sustained vibration) + non-trivial baseline
        private const val DRIVE_VARIANCE_MAX = 0.3f
        private const val DRIVE_MAG_MIN = 1.0f
        // Walking needs only 1 confirmation round; DRIVING/RUNNING need 2 to avoid false positives
        private const val CONFIRM_ROUNDS_WALK = 1
        private const val CONFIRM_ROUNDS_OTHER = 2
    }

    override fun startMonitoring() {
        if (refCount.getAndIncrement() > 0) return
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL)
        }
    }

    override fun stopMonitoring() {
        if (refCount.decrementAndGet() > 0) return
        sensorManager.unregisterListener(this)
        synchronized(lock) {
            readings.clear()
            pendingMotion = MotionType.STILL
            pendingCount = 0
        }
    }

    override fun onSensorChanged(event: SensorEvent?) {
        val e = event ?: return
        if (e.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = e.values[0]; val y = e.values[1]; val z = e.values[2]
        val magnitude = sqrt(x * x + y * y + z * z)
        val netAccel = kotlin.math.abs(magnitude - SensorManager.GRAVITY_EARTH)

        val motionToEmit: MotionType? = synchronized(lock) {
            readings.addLast(netAccel)
            if (readings.size > BUFFER_SIZE) readings.removeFirst()

            val now = System.currentTimeMillis()
            if (now - lastClassifyMs < CLASSIFY_INTERVAL_MS) return@synchronized null
            lastClassifyMs = now
            val candidate = classify()
            val requiredRounds = if (candidate == MotionType.WALKING) CONFIRM_ROUNDS_WALK
                                 else CONFIRM_ROUNDS_OTHER
            if (candidate == pendingMotion) {
                pendingCount++
                if (pendingCount >= requiredRounds) candidate else null
            } else {
                pendingMotion = candidate
                pendingCount = 1
                // STILL and WALKING are emitted immediately without waiting for confirmation
                if (candidate == MotionType.STILL || candidate == MotionType.WALKING) candidate
                else null
            }
        }
        motionToEmit?.let { _currentMotion.value = it }
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
