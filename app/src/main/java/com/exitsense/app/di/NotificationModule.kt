package com.exitsense.app.di

import com.exitsense.app.rules.SignalWeight
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NotificationModule {

    /** Provides default signal weights. Override in tests to customise scoring. */
    @Provides
    @Singleton
    fun provideSignalWeight(): SignalWeight = SignalWeight()

    /**
     * Application-lifetime scope shared across Hilt-injected components.
     * SupervisorJob ensures individual child failures don't cancel the scope.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
