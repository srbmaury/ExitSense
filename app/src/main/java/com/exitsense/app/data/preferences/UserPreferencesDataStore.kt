package com.exitsense.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.rules.DEFAULT_EXIT_THRESHOLD
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
        val HOME_NETWORK_IDS = stringPreferencesKey("home_network_ids")  // comma-separated ints
        val HOME_FLOOR = intPreferencesKey("home_floor")
        val IS_SETUP_COMPLETE = booleanPreferencesKey("is_setup_complete")
        val IS_MONITORING_ENABLED = booleanPreferencesKey("is_monitoring_enabled")
        val NOTIFICATIONS_ENABLED = booleanPreferencesKey("notifications_enabled")
        val EXIT_CONFIDENCE_THRESHOLD = floatPreferencesKey("exit_confidence_threshold")
        val REMINDER_SNOOZE_MINUTES = intPreferencesKey("reminder_snooze_minutes")
        val LAST_EXIT_TIMESTAMP = longPreferencesKey("last_exit_timestamp")
        val WEATHER_ENABLED = booleanPreferencesKey("weather_enabled")
        val CALENDAR_ENABLED = booleanPreferencesKey("calendar_enabled")
        val QUIET_HOURS_ENABLED = booleanPreferencesKey("quiet_hours_enabled")
        val QUIET_HOURS_START_MINUTE = intPreferencesKey("quiet_hours_start_minute")
        val QUIET_HOURS_END_MINUTE = intPreferencesKey("quiet_hours_end_minute")
    }

    val userPreferences: Flow<UserPreferences> = context.dataStore.data
        .catch { exception ->
            if (exception is IOException) emit(emptyPreferences()) else throw exception
        }
        .map { prefs ->
            UserPreferences(
                homeWifiSsid = prefs[Keys.HOME_WIFI_SSID] ?: "",
                homeNetworkIds = prefs[Keys.HOME_NETWORK_IDS]
                    ?.split("|")?.mapNotNull { it.trim().toIntOrNull() }?.toSet()
                    ?: emptySet(),
                homeFloor = prefs[Keys.HOME_FLOOR] ?: 0,
                isSetupComplete = prefs[Keys.IS_SETUP_COMPLETE] ?: false,
                isMonitoringEnabled = prefs[Keys.IS_MONITORING_ENABLED] ?: false,
                notificationsEnabled = prefs[Keys.NOTIFICATIONS_ENABLED] ?: true,
                exitConfidenceThreshold = prefs[Keys.EXIT_CONFIDENCE_THRESHOLD] ?: DEFAULT_EXIT_THRESHOLD,
                reminderSnoozeMinutes = prefs[Keys.REMINDER_SNOOZE_MINUTES] ?: 2,
                lastExitTimestamp = prefs[Keys.LAST_EXIT_TIMESTAMP] ?: 0L,
                weatherEnabled = prefs[Keys.WEATHER_ENABLED] ?: false,
                calendarEnabled = prefs[Keys.CALENDAR_ENABLED] ?: false,
                quietHoursEnabled = prefs[Keys.QUIET_HOURS_ENABLED] ?: false,
                quietHoursStartMinute = prefs[Keys.QUIET_HOURS_START_MINUTE] ?: (22 * 60),
                quietHoursEndMinute = prefs[Keys.QUIET_HOURS_END_MINUTE] ?: (7 * 60)
            )
        }

    suspend fun updateHomeWifiSsid(ssid: String) {
        context.dataStore.edit { it[Keys.HOME_WIFI_SSID] = ssid }
    }

    suspend fun addHomeNetworkId(networkId: Int) {
        if (networkId == -1) return
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_NETWORK_IDS]
                ?.split("|")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet()
                ?: mutableSetOf()
            current.add(networkId)
            prefs[Keys.HOME_NETWORK_IDS] = current.joinToString("|")
        }
    }

    suspend fun removeHomeNetworkId(networkId: Int) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HOME_NETWORK_IDS]
                ?.split("|")?.mapNotNull { it.trim().toIntOrNull() }?.toMutableSet()
                ?: mutableSetOf()
            current.remove(networkId)
            prefs[Keys.HOME_NETWORK_IDS] = current.joinToString("|")
        }
    }

    suspend fun restoreHomeNetworkIds(ids: Set<Int>) {
        context.dataStore.edit { prefs ->
            prefs[Keys.HOME_NETWORK_IDS] = if (ids.isEmpty()) "" else ids.joinToString("|")
        }
    }

    suspend fun updateHomeFloor(floor: Int) {
        context.dataStore.edit { it[Keys.HOME_FLOOR] = floor }
    }

    suspend fun setSetupComplete(complete: Boolean) {
        context.dataStore.edit { it[Keys.IS_SETUP_COMPLETE] = complete }
    }

    suspend fun setMonitoringEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_MONITORING_ENABLED] = enabled }
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

    suspend fun updateWeatherEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.WEATHER_ENABLED] = enabled }
    }

    suspend fun updateCalendarEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.CALENDAR_ENABLED] = enabled }
    }

    suspend fun updateQuietHoursEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.QUIET_HOURS_ENABLED] = enabled }
    }

    suspend fun updateQuietHoursStartMinute(minute: Int) {
        context.dataStore.edit { it[Keys.QUIET_HOURS_START_MINUTE] = minute }
    }

    suspend fun updateQuietHoursEndMinute(minute: Int) {
        context.dataStore.edit { it[Keys.QUIET_HOURS_END_MINUTE] = minute }
    }
}
