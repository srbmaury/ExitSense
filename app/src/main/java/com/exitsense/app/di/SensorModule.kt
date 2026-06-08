package com.exitsense.app.di

import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.impl.ExitDetectorImpl
import com.exitsense.app.sensors.*
import com.exitsense.app.sensors.impl.*
import com.exitsense.app.sensors.AmbientLightProvider
import com.exitsense.app.sensors.ChargerStateProvider
import com.exitsense.app.sensors.impl.AmbientLightProviderImpl
import com.exitsense.app.sensors.impl.ChargerStateProviderImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {

    @Binds @Singleton
    abstract fun bindMotionProvider(impl: MotionProviderImpl): MotionProvider

    @Binds @Singleton
    abstract fun bindWifiProvider(impl: WifiProviderImpl): WifiProvider

    @Binds @Singleton
    abstract fun bindPressureProvider(impl: PressureProviderImpl): PressureProvider

    @Binds @Singleton
    abstract fun bindScreenStateProvider(impl: ScreenStateProviderImpl): ScreenStateProvider

    @Binds @Singleton
    abstract fun bindStepCountProvider(impl: StepCountProviderImpl): StepCountProvider

    @Binds @Singleton
    abstract fun bindChargerStateProvider(impl: ChargerStateProviderImpl): ChargerStateProvider

    @Binds @Singleton
    abstract fun bindAmbientLightProvider(impl: AmbientLightProviderImpl): AmbientLightProvider

    @Binds @Singleton
    abstract fun bindExitDetector(impl: ExitDetectorImpl): ExitDetector
}
