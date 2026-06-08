package com.exitsense.app.rules

import com.exitsense.app.domain.model.ReminderProfile

/**
 * Core contract for the exit-detection engine.
 * Implementations combine signals from sensor providers into a confidence score
 * and emit an [ExitDetectionResult] whenever monitoring is active.
 */
interface ExitDetector {

    /**
     * Evaluate current sensor state against the given profiles and return a detection result.
     * This is a pure calculation – it has no side effects.
     */
    suspend fun evaluate(
        activeProfiles: List<ReminderProfile>,
        homeWifiSsid: String,
        threshold: Float
    ): ExitDetectionResult
}

data class ExitDetectionResult(
    val confidenceScore: Float,
    val signals: List<ExitSignal>,
    val isExitDetected: Boolean,
    val matchedProfile: ReminderProfile? = null
)
