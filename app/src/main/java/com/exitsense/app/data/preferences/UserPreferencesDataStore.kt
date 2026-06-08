package com.exitsense.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.exitsense.app.domain.model.UserPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "user_preferences"
)

@Singleton
class UserPreferencesDataStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private object Keys {
        val HOME_WIFI_SSID = stringPreferencesKey("home_wifi_ssid")
        val HOME_FLOOR = intPreferencesKey("home_floor")
        val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val EXIT_CONFIDENCE_THRESHOLD = floatPreferencesKey("exit_confidence_threshold")
        val REMINDER_SNOOZE_MINUTES = intPreferencesKey("reminder_snooze_minutes")
        val LAST_EXIT_TIMESTAMP = longPreferencesKey("last_exit_timestamp")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                homeWifiSsid = prefs[Keys.HOME_WIFI_SSID] ?: "",
                homeFloor = prefs[Keys.HOME_FLOOR] ?: 0,
                isSetupComplete = prefs[Keys.IS_SETUP_COMPLETE] ?: false,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                exitConfidenceThreshold = prefs[Keys.EXIT_CONFIDENCE_THRESHOLD] ?: 90f,
                reminderSnoozeMinutes = prefs[Keys.REMINDER_SNOOZE_MINUTES] ?: 2,
                lastExitTimestamp = prefs[Keys.LAST_EXIT_TIMESTAMP] ?: 0L
            )
        }

    suspend fun updateHomeWifiSsid(ssid: String) {
        context.dataStore.edit { it[Keys.HOME_WIFI_SSID] = ssid }
    }

    suspend fun updateHomeFloor(floor: Int) {
        context.dataStore.edit { it[Keys.HOME_FLOOR] = floor }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.IS_SETUP_COMPLETE] = complete }
    }

    suspend fun updateNotificationsEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.NOTIFICATIONS_ENABLED] = enabled }
    }

    suspend fun updateConfidenceThreshold(threshold: Float) {
        context.dataStore.edit { it[Keys.EXIT_CONFIDENCE_THRESHOLD] = threshold }
    }

    suspend fun updateSnoozeMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.REMINDER_SNOOZE_MINUTES] = minutes }
    }

    suspend fun updateLastExitTimestamp(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_EXIT_TIMESTAMP] = timestamp }
    }
}
