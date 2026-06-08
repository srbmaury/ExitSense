package com.exitsense.app.rules

import com.exitsense.app.domain.model.ExitSignalType

/**
 * A single evaluated signal with its contribution to the overall confidence score.
 */
data class ExitSignal(
    val type: ExitSignalType,
    val score: Float,
    val description: String = ""
)
