package com.exitsense.app.di

import javax.inject.Qualifier

/** Marks the application-lifetime [kotlinx.coroutines.CoroutineScope] provided by Hilt. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
