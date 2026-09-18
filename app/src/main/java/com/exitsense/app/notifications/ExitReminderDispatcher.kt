package com.exitsense.app.notifications

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.location.LocationManager
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import androidx.core.os.CancellationSignal
import com.exitsense.app.data.preferences.UserPreferencesDataStore
import com.exitsense.app.data.repository.CalendarRepository
import com.exitsense.app.data.repository.WeatherRepository
import com.exitsense.app.domain.model.ExitEvent
import com.exitsense.app.domain.model.UserPreferences
import com.exitsense.app.domain.repository.ExitEventRepository
import com.exitsense.app.domain.repository.ReminderRepository
import com.exitsense.app.rules.DepartureTracker
import com.exitsense.app.rules.ExitDetectionResult
import com.exitsense.app.rules.ExitDetector
import com.exitsense.app.rules.TimeRuleEvaluator
import com.exitsense.app.util.DebugLog
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Calendar
import kotlin.coroutines.resume
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The one place that turns a detection into a reminder, shared by the monitoring service and
 * the periodic worker. Serialised so two callers can't both remind for the same departure.
 */
@Singleton
class ExitReminderDispatcher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val exitDetector: ExitDetector,
    private val reminderRepository: ReminderRepository,
    private val exitEventRepository: ExitEventRepository,
    private val preferencesDataStore: UserPreferencesDataStore,
    private val notificationManager: ExitNotificationManager,
    private val weatherRepository: WeatherRepository,
    private val calendarRepository: CalendarRepository,
    private val departureTracker: DepartureTracker
) {
    private val mutex = Mutex()

    /**
     * Evaluates the current sensor state and shows a reminder when the user is leaving.
     * Returns the detection result, or null when detection was skipped (setup incomplete,
     * notifications off, quiet hours or no active profiles).
     */
    suspend fun evaluateAndNotify(): ExitDetectionResult? = mutex.withLock {
        val prefs = preferencesDataStore.userPreferences.first()
        if (!prefs.isSetupComplete || !prefs.notificationsEnabled) return null
        if (isInQuietHours(prefs)) return null

        val activeProfiles = reminderRepository.getActiveProfiles().first()
        if (activeProfiles.isEmpty()) return null

        val result = exitDetector.evaluate(
            activeProfiles = activeProfiles,
            homeWifiSsid = prefs.homeWifiSsid,
            homeNetworkIds = prefs.homeNetworkIds,
            threshold = prefs.exitConfidenceThreshold
        )
        if (!result.isExitDetected) return result

        val profile = activeProfiles.firstOrNull {
            TimeRuleEvaluator.isWithinSchedule(it) && !TimeRuleEvaluator.wasNotifiedInCurrentWindow(it)
        }
        if (profile == null) {
            DebugLog.d("reminder") { "exit detected but no profile due a reminder" }
            return result
        }

        val now = System.currentTimeMillis()
        val eventId = exitEventRepository.saveExitEvent(
            ExitEvent(
                confidenceScore = result.confidenceScore,
                triggeredSignals = result.signals.map { it.type },
                notificationShown = true,
                profileId = profile.id
            )
        )
        reminderRepository.updateProfileLastNotifiedAt(profile.id, now)
        departureTracker.consume()

        val weatherAlert = if (prefs.weatherEnabled) fetchWeatherAlert() else null
        val upcomingEvents = if (prefs.calendarEnabled) {
            runCatching { calendarRepository.getUpcomingEvents() }.getOrDefault(emptyList())
        } else emptyList()

        DebugLog.d("reminder") { "showing reminder for '${profile.name}' (score ${result.confidenceScore})" }
        notificationManager.showExitReminder(
            exitEventId = eventId,
            profileId = profile.id,
            profileName = profile.name,
            items = profile.notifiableItems(),
            snoozeMinutes = prefs.reminderSnoozeMinutes,
            weatherAlert = weatherAlert,
            upcomingEvents = upcomingEvents
        )
        result
    }

    /**
     * Fetches the forecast ahead of a departure while the phone still has home Wi-Fi, so the
     * reminder can use it even if there's no connection right after leaving.
     */
    suspend fun prefetchWeather() {
        val prefs = preferencesDataStore.userPreferences.first()
        if (!prefs.weatherEnabled || weatherRepository.hasRecentForecast(PREFETCH_INTERVAL_MS)) return
        val alert = fetchWeatherAlert()
        DebugLog.d("reminder") { "weather prefetched: ${alert ?: "no rain alert"}" }
    }

    private fun isInQuietHours(prefs: UserPreferences): Boolean {
        if (!prefs.quietHoursEnabled) return false
        val cal = Calendar.getInstance()
        val nowMinute = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val start = prefs.quietHoursStartMinute
        val end = prefs.quietHoursEndMinute
        return if (start > end) nowMinute >= start || nowMinute < end   // overnight window
               else nowMinute >= start && nowMinute < end
    }

    /** Best effort — a slow or failing forecast must not hold back the reminder. */
    private suspend fun fetchWeatherAlert(): String? {
        // Right after leaving home Wi-Fi there's often no working connection. Trying anyway
        // would stall the reminder, because a blocked DNS lookup ignores coroutine timeouts.
        if (!hasInternet()) {
            return weatherRepository.cachedRainAlert()?.also {
                DebugLog.d("reminder") { "offline, using cached forecast: $it" }
            }
        }
        val location = currentLocation() ?: run {
            DebugLog.d("reminder") { "weather skipped: no location" }
            return null
        }
        // Falling back outside the timeout too: with no connection the fetch can outlast it
        // (DNS lookups aren't covered by the socket timeouts)
        val fetched = withTimeoutOrNull(WEATHER_TIMEOUT_MS) {
            runCatching { weatherRepository.getRainForecast(location.latitude, location.longitude) }.getOrNull()
        }
        return fetched ?: weatherRepository.cachedRainAlert()?.also {
            DebugLog.d("reminder") { "using cached forecast: $it" }
        }
    }

    /**
     * A recent cached fix if there is one, otherwise a fresh one. Last-known location is often
     * empty when no other app has asked for location lately.
     */
    private fun hasInternet(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val capabilities = connectivityManager.activeNetwork?.let {
            runCatching { connectivityManager.getNetworkCapabilities(it) }.getOrNull()
        } ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }

    @SuppressLint("MissingPermission")
    private suspend fun currentLocation(): Location? {
        val hasLocation = listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
            .any { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
        if (!hasLocation) return null
        val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val providers = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER)
            .filter { runCatching { locationManager.isProviderEnabled(it) }.getOrDefault(false) }

        val cached = providers
            .mapNotNull { runCatching { locationManager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
        if (cached != null && System.currentTimeMillis() - cached.time < MAX_LOCATION_AGE_MS) return cached

        // Ask every enabled provider at once and take the first answer: a provider can be
        // enabled yet never produce a fix (e.g. network location without Play services)
        val fresh = withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
            channelFlow {
                providers.forEach { provider ->
                    launch { requestLocation(locationManager, provider)?.let { send(it) } }
                }
            }.firstOrNull()
        }
        return fresh ?: cached
    }

    @SuppressLint("MissingPermission")
    private suspend fun requestLocation(locationManager: LocationManager, provider: String): Location? =
        suspendCancellableCoroutine { cont ->
            val cancellation = CancellationSignal()
            cont.invokeOnCancellation { cancellation.cancel() }
            runCatching {
                LocationManagerCompat.getCurrentLocation(
                    locationManager, provider, cancellation, ContextCompat.getMainExecutor(context)
                ) { location -> if (cont.isActive) cont.resume(location) }
            }.onFailure { if (cont.isActive) cont.resume(null) }
        }

    private companion object {
        const val WEATHER_TIMEOUT_MS = 8_000L
        const val PREFETCH_INTERVAL_MS = 30 * 60 * 1000L
        const val LOCATION_TIMEOUT_MS = 10_000L
        const val MAX_LOCATION_AGE_MS = 60 * 60 * 1000L  // weather doesn't need a precise, fresh fix
    }
}
