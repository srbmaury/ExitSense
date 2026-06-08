package com.exitsense.app.database

import com.exitsense.app.data.local.entities.*
import com.exitsense.app.data.local.mapper.*
import com.exitsense.app.domain.model.*
import org.junit.Assert.*
import org.junit.Test

class MapperTest {

    @Test
    fun `ReminderProfileEntity round-trips through domain model`() {
        val entity = ReminderProfileEntity(
            id = 1L,
            name = "Office",
            isActive = true,
            scheduleType = "WEEKDAYS",
            activeDays = "1,2,3,4,5",
            startTimeHour = 8,
            startTimeMinute = 0,
            endTimeHour = 10,
            endTimeMinute = 0,
            createdAt = 1000L
        )

        val domain = entity.toDomain()
        val backToEntity = domain.toEntity()

        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.name, backToEntity.name)
        assertEquals(entity.scheduleType, backToEntity.scheduleType)
        assertEquals(entity.startTimeHour, backToEntity.startTimeHour)
    }

    @Test
    fun `activeDays string is correctly parsed to set`() {
        val entity = ReminderProfileEntity(
            id = 1L, name = "Test",
            activeDays = "1,3,5"
        )
        val domain = entity.toDomain()
        assertEquals(setOf(1, 3, 5), domain.activeDays)
    }

    @Test
    fun `empty activeDays string results in empty set`() {
        val entity = ReminderProfileEntity(id = 1L, name = "Test", activeDays = "")
        val domain = entity.toDomain()
        assertTrue(domain.activeDays.isEmpty())
    }

    @Test
    fun `ExitEventEntity with valid signal list round-trips`() {
        val entity = ExitEventEntity(
            id = 1L,
            confidenceScore = 85f,
            triggeredSignals = """["WIFI_DISCONNECTED","MOTION_WALKING"]"""
        )
        val domain = entity.toDomain()
        assertEquals(2, domain.triggeredSignals.size)
        assertTrue(domain.triggeredSignals.contains(ExitSignalType.WIFI_DISCONNECTED))
        assertTrue(domain.triggeredSignals.contains(ExitSignalType.MOTION_WALKING))

        val backToEntity = domain.toEntity()
        assertEquals(entity.confidenceScore, backToEntity.confidenceScore, 0.01f)
    }

    @Test
    fun `ExitEventEntity with unknown signal is gracefully skipped`() {
        val entity = ExitEventEntity(
            id = 1L,
            confidenceScore = 50f,
            triggeredSignals = """["WIFI_DISCONNECTED","UNKNOWN_SIGNAL_XYZ"]"""
        )
        val domain = entity.toDomain()
        assertEquals(1, domain.triggeredSignals.size)
        assertEquals(ExitSignalType.WIFI_DISCONNECTED, domain.triggeredSignals[0])
    }

    @Test
    fun `ReminderItemEntity round-trips`() {
        val entity = ReminderItemEntity(
            id = 1L,
            profileId = 10L,
            name = "Laptop",
            priority = 5,
            isEnabled = true,
            learnedPriority = 1.5f
        )
        val domain = entity.toDomain()
        val backToEntity = domain.toEntity()

        assertEquals(entity.id, backToEntity.id)
        assertEquals(entity.name, backToEntity.name)
        assertEquals(entity.learnedPriority, backToEntity.learnedPriority, 0.001f)
    }

    @Test
    fun `SensorSnapshotEntity with invalid motionType defaults to UNKNOWN`() {
        val entity = SensorSnapshotEntity(
            id = 1L,
            motionType = "INVALID_TYPE",
            screenState = "OFF"
        )
        val domain = entity.toDomain()
        assertEquals(MotionType.UNKNOWN, domain.motionType)
    }
}
