package com.exitsense.app.domain.model

data class ExitEvent(
    val id: Long = 0,
    val timestamp: Long = System.currentTimeMillis(),
    val confidenceScore: Float,
    val triggeredSignals: List<ExitSignalType>,
    val notificationShown: Boolean = false,
    val profileId: Long? = null,
    val userResponded: Boolean = false
)
