package com.exitsense.app.data.repository

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class CalendarRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /**
     * Returns titles of calendar event instances starting within the next [withinHours] hours.
     * Uses CalendarContract.Instances so recurring events are correctly expanded.
     */
    suspend fun getUpcomingEvents(withinHours: Int = 3): List<String> {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                != PackageManager.PERMISSION_GRANTED) return emptyList()
        return withContext(Dispatchers.IO) {
            try {
                val now = System.currentTimeMillis()
                val until = now + withinHours * 60 * 60 * 1000L
                val timeFmt = SimpleDateFormat("HH:mm", Locale.getDefault())

                val cursor = CalendarContract.Instances.query(
                    context.contentResolver,
                    arrayOf(CalendarContract.Instances.TITLE, CalendarContract.Instances.BEGIN),
                    now,
                    until
                ) ?: return@withContext emptyList()

                val events = mutableListOf<String>()
                cursor.use { c ->
                    val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                    while (c.moveToNext() && events.size < 3) {
                        val title = c.getString(titleIdx) ?: continue
                        val time = timeFmt.format(Date(c.getLong(beginIdx)))
                        events.add("$title ($time)")
                    }
                }
                events
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
