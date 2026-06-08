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
    val ambientLight: Float = 0f            // not scored; kept for display only
)

/** Scores must reach or exceed this threshold to fire an exit event. */
const val DEFAULT_EXIT_THRESHOLD = 90f
