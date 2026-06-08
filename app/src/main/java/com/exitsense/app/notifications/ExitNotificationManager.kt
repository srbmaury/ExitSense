package com.exitsense.app.notifications

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.exitsense.app.R
import com.exitsense.app.domain.model.ReminderItem
import com.exitsense.app.presentation.MainActivity
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExitNotificationManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    companion object {
        const val CHANNEL_EXIT_REMINDERS = "exit_reminders"
        const val CHANNEL_MONITORING = "monitoring_service"
        const val NOTIFICATION_ID_EXIT = 1001
        const val NOTIFICATION_ID_SERVICE = 1002

        const val EXTRA_EXIT_EVENT_ID = "exit_event_id"
        const val EXTRA_PROFILE_ID = "profile_id"
        const val EXTRA_SNOOZE_MINUTES = "snooze_minutes"
        const val ACTION_CONFIRM = "com.exitsense.app.ACTION_CONFIRM"
        const val ACTION_SNOOZE = "com.exitsense.app.ACTION_SNOOZE"
    }

    fun createChannels() {
        val exitChannel = NotificationChannel(
            CHANNEL_EXIT_REMINDERS,
            context.getString(R.string.channel_exit_reminder_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_exit_reminder_description)
            enableVibration(true)
            enableLights(true)
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_MONITORING,
            context.getString(R.string.channel_monitoring_name),
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = context.getString(R.string.channel_monitoring_description)
        }

        notificationManager.createNotificationChannels(listOf(exitChannel, serviceChannel))
    }

    fun showExitReminder(
        exitEventId: Long,
        profileId: Long,
        profileName: String,
        items: List<ReminderItem>,
        snoozeMinutes: Int
    ) {
        val openIntent = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(EXTRA_EXIT_EVENT_ID, exitEventId)
                putExtra(EXTRA_PROFILE_ID, profileId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val confirmIntent = PendingIntent.getBroadcast(
            context, 1,
            Intent(ACTION_CONFIRM).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_EXIT_EVENT_ID, exitEventId)
                putExtra(EXTRA_PROFILE_ID, profileId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = PendingIntent.getBroadcast(
            context, 2,
            Intent(ACTION_SNOOZE).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_EXIT_EVENT_ID, exitEventId)
                putExtra(EXTRA_PROFILE_ID, profileId)
                putExtra(EXTRA_SNOOZE_MINUTES, snoozeMinutes)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val itemLines = items.take(5).joinToString("\n") { "  ☐  ${it.name}" }
        val bigText = "Did you take:\n$itemLines"

        val notification = android.app.Notification.Builder(context, CHANNEL_EXIT_REMINDERS)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.notification_exit_title))
            .setContentText(profileName)
            .setStyle(Notification.BigTextStyle().bigText(bigText))
            .setContentIntent(openIntent)
            .setAutoCancel(false)
            .setPriority(Notification.PRIORITY_HIGH)
            .setCategory(Notification.CATEGORY_REMINDER)
            .addAction(
                android.R.drawable.checkbox_on_background,
                context.getString(R.string.notification_exit_action_confirm),
                confirmIntent
            )
            .addAction(
                android.R.drawable.ic_menu_recent_history,
                context.getString(R.string.notification_exit_action_snooze)
                    .replace("again", "in $snoozeMinutes min"),
                snoozeIntent
            )
            .build()

        notificationManager.notify(NOTIFICATION_ID_EXIT, notification)
    }

    fun buildServiceNotification(): Notification =
        Notification.Builder(context, CHANNEL_MONITORING)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.service_notification_title))
            .setContentText(context.getString(R.string.service_notification_text))
            .setOngoing(true)
            .build()

    fun dismissExitReminder() {
        notificationManager.cancel(NOTIFICATION_ID_EXIT)
    }
}
