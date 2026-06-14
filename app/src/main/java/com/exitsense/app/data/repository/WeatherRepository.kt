package com.exitsense.app.data.repository

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URL
import java.util.Calendar
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WeatherRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Returns a rain alert string if precipitation probability >= 50% in the next 2 hours,
     * or null if no rain is expected or the fetch fails.
     */
    suspend fun getRainForecast(lat: Double, lon: Double): String? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.open-meteo.com/v1/forecast" +
                "?latitude=$lat&longitude=$lon" +
                "&hourly=precipitation_probability" +
                "&forecast_days=1&timezone=auto"

            val response = withTimeout(5_000L) { URL(url).readText() }
            val root = json.parseToJsonElement(response).jsonObject
            val probs = root["hourly"]?.jsonObject
                ?.get("precipitation_probability")?.jsonArray
                ?: return@withContext null

            val currentHour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
            val maxProb = (currentHour until minOf(currentHour + 3, probs.size))
                .maxOfOrNull { probs[it].jsonPrimitive.int } ?: 0

            if (maxProb >= 50) "Rain likely ($maxProb%) — bring an umbrella" else null
        } catch (e: Exception) {
            null
        }
    }
}
