package com.exitsense.app.domain.model

data class ReminderItem(
    val id: Long = 0,
    val profileId: Long,
    val name: String,
    val iconName: String = "check_circle",
    val priority: Int = 3,
    val isEnabled: Boolean = true,
    val learnedPriority: Float = 1.0f
) {
    val effectivePriority: Float get() = priority * learnedPriority
}
