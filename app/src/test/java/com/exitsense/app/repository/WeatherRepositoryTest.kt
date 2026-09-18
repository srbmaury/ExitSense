package com.exitsense.app.repository

import com.exitsense.app.data.repository.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeatherRepositoryTest {

    private fun response(vararg probs: Int?) =
        """{"hourly":{"time":["a","b","c"],"precipitation_probability":[${probs.joinToString(",")}]}}"""

    @Test
    fun `rain alert uses the highest probability`() {
        assertEquals(
            "Rain likely (67%) — bring an umbrella",
            WeatherRepository.parseRainAlert(response(29, 49, 67))
        )
    }

    @Test
    fun `no alert below fifty percent`() {
        assertNull(WeatherRepository.parseRainAlert(response(10, 49, 0)))
    }

    @Test
    fun `missing hourly values are ignored`() {
        assertEquals(
            "Rain likely (50%) — bring an umbrella",
            WeatherRepository.parseRainAlert(response(null, 50, null))
        )
    }

    @Test
    fun `unexpected response gives no alert`() {
        assertNull(WeatherRepository.parseRainAlert("""{"error":true}"""))
    }
}
