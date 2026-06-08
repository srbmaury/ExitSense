package com.exitsense.app.domain.model

data class UserResponse(
    val id: Long = 0,
    val exitEventId: Long,
    val itemId: Long,
    val profileId: Long,
    val wasConfirmed: Boolean,
    val respondedAt: Long = System.currentTimeMillis()
)
