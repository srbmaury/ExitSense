package com.exitsense.app.di

import com.exitsense.app.data.repository.ExitEventRepositoryImpl
import com.exitsense.app.data.repository.LearningRepositoryImpl
import com.exitsense.app.data.repository.ReminderRepositoryImpl
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.LearningRepository
import com.exitsense.app.domain.repository.ReminderRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds @Singleton
    abstract fun bindReminderRepository(impl: ReminderRepositoryImpl): ReminderRepository

    @Binds @Singleton
    abstract fun bindExitEventRepository(impl: ExitEventRepositoryImpl): ExitEventRepository

    @Binds @Singleton
    abstract fun bindLearningRepository(impl: LearningRepositoryImpl): LearningRepository
}
