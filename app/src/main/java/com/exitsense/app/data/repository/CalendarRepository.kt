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
     * Returns titles of timed calendar events starting within the next [withinHours] hours
     * (events already under way and all-day events are left out).
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
                    arrayOf(
                        CalendarContract.Instances.TITLE,
                        CalendarContract.Instances.BEGIN,
                        CalendarContract.Instances.ALL_DAY
                    ),
                    now,
                    until
                ) ?: return@withContext emptyList()

                val events = mutableListOf<String>()
                cursor.use { c ->
                    val titleIdx = c.getColumnIndex(CalendarContract.Instances.TITLE)
                    val beginIdx = c.getColumnIndex(CalendarContract.Instances.BEGIN)
                    val allDayIdx = c.getColumnIndex(CalendarContract.Instances.ALL_DAY)
                    // The query returns everything overlapping the range; keep only timed
                    // events that are still ahead, soonest first
                    val upcoming = mutableListOf<Pair<Long, String>>()
                    while (c.moveToNext()) {
                        val title = c.getString(titleIdx) ?: continue
                        val begin = c.getLong(beginIdx)
                        if (c.getInt(allDayIdx) == 1 || begin < now) continue
                        upcoming += begin to title
                    }
                    upcoming.sortedBy { it.first }.take(3).forEach { (begin, title) ->
                        events.add("$title (${timeFmt.format(Date(begin))})")
                    }
                }
                events
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
