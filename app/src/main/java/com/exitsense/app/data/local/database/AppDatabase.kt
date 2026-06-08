package com.exitsense.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 3,
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

        // v1→v2: added learnedPriority column to reminder_items
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminder_items ADD COLUMN learnedPriority REAL NOT NULL DEFAULT 1.0"
                )
            }
        }

        // v2→v3: added lastNotifiedAt column to reminder_profiles for per-profile cooldown
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE reminder_profiles ADD COLUMN lastNotifiedAt INTEGER NOT NULL DEFAULT 0"
                )
            }
        }
    }
}
