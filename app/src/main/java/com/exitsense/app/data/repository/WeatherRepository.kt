package com.exitsense.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private class CachedForecast(val alert: String?, val fetchedAt: Long)

    @Volatile
    private var cached: CachedForecast? = null

    /** True when a forecast fetched within [maxAgeMs] is available. */
    fun hasRecentForecast(maxAgeMs: Long): Boolean =
        cached?.let { System.currentTimeMillis() - it.fetchedAt < maxAgeMs } == true

    /** The alert from the last successful fetch, if it is still recent enough to use. */
    fun cachedRainAlert(): String? =
        cached?.takeIf { System.currentTimeMillis() - it.fetchedAt < CACHE_MAX_AGE_MS }?.alert

    /**
     * Returns a rain alert string if precipitation probability >= 50% in the current or next
     * two hours, or null if no rain is expected. If the fetch fails (often right after leaving
     * home Wi-Fi), falls back to a forecast fetched within the last hour.
     */
    suspend fun getRainForecast(lat: Double, lon: Double): String? {
        val result = fetchRainAlert(lat, lon)
        val now = System.currentTimeMillis()
        if (result != null) {
            cached = CachedForecast(result.getOrNull(), now)
            return result.getOrNull()
        }
        return cachedRainAlert()
    }

    /** Null when the forecast couldn't be fetched; otherwise the alert (which may be null). */
    private suspend fun fetchRainAlert(lat: Double, lon: Double): Result<String?>? = withContext(Dispatchers.IO) {
        // forecast_hours makes the API return hours starting from "now" at that location,
        // so no time-zone arithmetic is needed here
        val url = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon" +
            "&hourly=precipitation_probability" +
            "&forecast_hours=$FORECAST_HOURS"
        runCatching {
            // Explicit timeouts: coroutine timeouts can't interrupt a blocking socket read
            val connection = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
            }
            try {
                connection.inputStream.bufferedReader().use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }.getOrNull()?.let { body ->
            if (hasHourlyForecast(body)) Result.success(parseRainAlert(body)) else null
        }
    }

    companion object {
        private const val FORECAST_HOURS = 3
        private const val TIMEOUT_MS = 5_000
        private const val RAIN_THRESHOLD = 50
        private const val CACHE_MAX_AGE_MS = 60 * 60 * 1000L
        private val json = Json { ignoreUnknownKeys = true }

        private fun hasHourlyForecast(response: String): Boolean = runCatching {
            json.parseToJsonElement(response).jsonObject["hourly"] != null
        }.getOrDefault(false)

        internal fun parseRainAlert(response: String): String? {
            val probs = json.parseToJsonElement(response).jsonObject["hourly"]?.jsonObject
                ?.get("precipitation_probability")?.jsonArray
                ?: return null
            val maxProb = probs.mapNotNull { it.jsonPrimitive.intOrNull }.maxOrNull() ?: return null
            return if (maxProb >= RAIN_THRESHOLD) "Rain likely ($maxProb%) — bring an umbrella" else null
        }
    }
}
