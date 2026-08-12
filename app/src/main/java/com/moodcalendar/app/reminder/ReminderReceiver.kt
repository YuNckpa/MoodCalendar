package com.moodcalendar.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.moodcalendar.app.MainActivity
import com.moodcalendar.app.MoodCalendarApp
import com.moodcalendar.app.R
import com.moodcalendar.app.data.model.EventType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ReminderScheduler.ACTION_REMIND) return

        val title = intent.getStringExtra(ReminderScheduler.EXTRA_TITLE) ?: "事件提醒"
        val typeName = intent.getStringExtra(ReminderScheduler.EXTRA_TYPE)
        val eventId = intent.getLongExtra(ReminderScheduler.EXTRA_EVENT_ID, -1L)
        val typeLabel = when (runCatching { EventType.valueOf(typeName ?: "") }.getOrNull()) {
            EventType.ANNIVERSARY -> "纪念日"
            EventType.COUNTDOWN -> "倒数日"
            EventType.BIRTHDAY -> "生日"
            EventType.CUSTOM -> "自定义"
            null -> "提醒"
        }

        ensureChannel(context)
        val open = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPi = PendingIntent.getActivity(
            context,
            eventId.toInt(),
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("$typeLabel：$title")
            .setContentText("到点提醒，点开查看详情")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentPi)
            .build()

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.notify((eventId % Int.MAX_VALUE).toInt() + 1000, notification)

        // Reschedule next occurrence for recurring events
        if (eventId > 0) {
            val pending = goAsync()
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val app = context.applicationContext as MoodCalendarApp
                    app.container.eventRepository.getById(eventId)?.let {
                        app.container.reminderScheduler.schedule(it)
                    }
                } finally {
                    pending.finish()
                }
            }
        }
    }

    companion object {
        const val CHANNEL_ID = "event_reminders"

        fun ensureChannel(context: Context) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = context.getString(R.string.notification_channel_desc)
            }
            nm.createNotificationChannel(channel)
        }
    }
}
