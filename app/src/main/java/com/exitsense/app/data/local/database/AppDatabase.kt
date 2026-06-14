package com.exitsense.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.exitsense.app.data.local.dao.*
import com.exitsense.app.data.local.entities.*

@Database(
    entities = [
        ReminderProfileEntity::class,
        ReminderItemEntity::class,
        ExitEventEntity::class,
        UserResponseEntity::class,
        SensorSnapshotEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun reminderProfileDao(): ReminderProfileDao
    abstract fun reminderItemDao(): ReminderItemDao
    abstract fun exitEventDao(): ExitEventDao
    abstract fun userResponseDao(): UserResponseDao
    abstract fun sensorSnapshotDao(): SensorSnapshotDao

    companion object {
        const val DATABASE_NAME = "exit_sense_db"
    }
}
