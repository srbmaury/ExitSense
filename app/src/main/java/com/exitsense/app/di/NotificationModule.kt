package com.exitsense.app.di

import com.exitsense.app.rules.SignalWeight
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    /** Provides default signal weights. Override in tests to customise scoring. */
    @Provides
    @Singleton
    fun provideSignalWeight(): SignalWeight = SignalWeight()
}
