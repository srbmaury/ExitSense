package com.exitsense.app.data.local.mapper

import com.exitsense.app.data.local.entities.*
import com.exitsense.app.domain.model.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val json = Json { ignoreUnknownKeys = true }

// ── Profile ──────────────────────────────────────────────────────────────────

fun ReminderProfileEntity.toDomain(items: List<ReminderItem> = emptyList()): ReminderProfile =
    ReminderProfile(
        id = id,
        name = name,
        isActive = isActive,
        scheduleType = runCatching { ScheduleType.valueOf(scheduleType) }
            .getOrDefault(ScheduleType.WEEKDAYS),
        activeDays = if (activeDays.isBlank()) emptySet()
        else activeDays.split(",").mapNotNull { it.trim().toIntOrNull() }.toSet(),
        startTimeHour = startTimeHour,
        startTimeMinute = startTimeMinute,
        endTimeHour = endTimeHour,
        endTimeMinute = endTimeMinute,
        items = items,
        createdAt = createdAt,
        lastNotifiedAt = lastNotifiedAt
    )

fun ReminderProfile.toEntity(): ReminderProfileEntity =
    ReminderProfileEntity(
        id = id,
        name = name,
        isActive = isActive,
        scheduleType = scheduleType.name,
        activeDays = activeDays.sorted().joinToString(","),
        startTimeHour = startTimeHour,
        startTimeMinute = startTimeMinute,
        endTimeHour = endTimeHour,
        endTimeMinute = endTimeMinute,
        createdAt = createdAt,
        lastNotifiedAt = lastNotifiedAt
    )

// ── Item ──────────────────────────────────────────────────────────────────────

fun ReminderItemEntity.toDomain(): ReminderItem =
    ReminderItem(
        id = id,
        profileId = profileId,
        name = name,
        iconName = iconName,
        priority = priority,
        isEnabled = isEnabled,
        learnedPriority = learnedPriority
    )

fun ReminderItem.toEntity(): ReminderItemEntity =
    ReminderItemEntity(
        id = id,
        profileId = profileId,
        name = name,
        iconName = iconName,
        priority = priority,
        isEnabled = isEnabled,
        learnedPriority = learnedPriority
    )

// ── ExitEvent ────────────────────────────────────────────────────────────────

fun ExitEventEntity.toDomain(): ExitEvent {
    val signals = try {
        json.decodeFromString<List<String>>(triggeredSignals)
            .mapNotNull { runCatching { ExitSignalType.valueOf(it) }.getOrNull() }
    } catch (_: Exception) {
        emptyList()
    }
    return ExitEvent(
        id = id,
        timestamp = timestamp,
        confidenceScore = confidenceScore,
        triggeredSignals = signals,
        notificationShown = notificationShown,
        profileId = profileId,
        userResponded = userResponded
    )
}

fun ExitEvent.toEntity(): ExitEventEntity =
    ExitEventEntity(
        id = id,
        timestamp = timestamp,
        confidenceScore = confidenceScore,
        triggeredSignals = json.encodeToString(triggeredSignals.map { it.name }),
        notificationShown = notificationShown,
        profileId = profileId,
        userResponded = userResponded
    )

// ── UserResponse ──────────────────────────────────────────────────────────────

fun UserResponseEntity.toDomain(): UserResponse =
    UserResponse(
        id = id,
        exitEventId = exitEventId,
        itemId = itemId,
        profileId = profileId,
        wasConfirmed = wasConfirmed,
        respondedAt = respondedAt
    )

fun UserResponse.toEntity(): UserResponseEntity =
    UserResponseEntity(
        id = id,
        exitEventId = exitEventId,
        itemId = itemId,
        profileId = profileId,
        wasConfirmed = wasConfirmed,
        respondedAt = respondedAt
    )

// ── SensorSnapshot ────────────────────────────────────────────────────────────

fun SensorSnapshotEntity.toDomain(): SensorSnapshot =
    SensorSnapshot(
        id = id,
        timestamp = timestamp,
        wifiConnected = wifiConnected,
        connectedSsid = connectedSsid,
        motionType = runCatching { MotionType.valueOf(motionType) }.getOrDefault(MotionType.UNKNOWN),
        screenState = runCatching { ScreenState.valueOf(screenState) }.getOrDefault(ScreenState.OFF),
        pressure = pressure,
        confidenceScore = confidenceScore
    )

fun SensorSnapshot.toEntity(): SensorSnapshotEntity =
    SensorSnapshotEntity(
        id = id,
        timestamp = timestamp,
        wifiConnected = wifiConnected,
        connectedSsid = connectedSsid,
        motionType = motionType.name,
        screenState = screenState.name,
        pressure = pressure,
        confidenceScore = confidenceScore
    )
