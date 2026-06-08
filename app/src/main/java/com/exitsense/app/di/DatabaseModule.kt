package com.exitsense.app.di

import android.content.Context
import androidx.room.Room
import com.exitsense.app.data.local.dao.*
import com.exitsense.app.data.local.database.AppDatabase
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
            .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()

    @Provides fun provideProfileDao(db: AppDatabase): ReminderProfileDao = db.reminderProfileDao()
    @Provides fun provideItemDao(db: AppDatabase): ReminderItemDao = db.reminderItemDao()
    @Provides fun provideExitEventDao(db: AppDatabase): ExitEventDao = db.exitEventDao()
    @Provides fun provideUserResponseDao(db: AppDatabase): UserResponseDao = db.userResponseDao()
    @Provides fun provideSensorSnapshotDao(db: AppDatabase): SensorSnapshotDao = db.sensorSnapshotDao()
}
