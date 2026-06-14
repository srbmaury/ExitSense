package com.exitsense.app.rules

/**
 * Configurable weights for each signal contributing to the exit confidence score.
 * Default values are tuned for typical home-departure patterns; users can adjust
 * the threshold via Settings.
 */
data class SignalWeight(
    val wifiDisconnected: Float = 50f,
    val motionWalking: Float = 15f,
    val motionRunning: Float = 15f,
    val motionDriving: Float = 25f,
    val screenUnlocked: Float = 5f,
    val withinTimeWindow: Float = 5f,
    val barometerDescent: Float = 10f,
    val stepCount: Float = 10f,
    val chargerUnplugged: Float = 10f,
    val ambientLight: Float = 5f
)

// Threshold rationale: WiFi(50) + walking(15) + steps(10) = 75 is a clear exit.
// WiFi alone(50) or walking alone(15) can't trigger it. Charger or light add helpful nudges.
const val DEFAULT_EXIT_THRESHOLD = 75f
