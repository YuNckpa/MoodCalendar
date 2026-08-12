package com.moodcalendar.app.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.moodcalendar.app.data.model.AdvanceUnit
import com.moodcalendar.app.data.repository.CalendarEvent
import com.moodcalendar.app.util.EventOccurrence
import java.time.LocalDateTime
import java.time.ZoneId

class ReminderScheduler(private val context: Context) {
    private val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    fun schedule(event: CalendarEvent) {
        cancel(event.id)
        if (!event.remindOnDay && event.advanceReminders.isEmpty()) return

        val fireTimes = computeFireTimes(event)
        fireTimes.forEachIndexed { index, epochMillis ->
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
                putExtra(EXTRA_EVENT_ID, event.id)
                putExtra(EXTRA_TITLE, event.title)
                putExtra(EXTRA_TYPE, event.type.name)
                putExtra(EXTRA_SLOT, index)
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(event.id, index),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            setExact(epochMillis, pi)
        }
    }

    fun cancel(eventId: Long) {
        for (slot in 0 until 8) {
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                action = ACTION_REMIND
            }
            val pi = PendingIntent.getBroadcast(
                context,
                requestCode(eventId, slot),
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (pi != null) {
                alarmManager.cancel(pi)
                pi.cancel()
            }
        }
    }

    private fun setExact(triggerAtMillis: Long, pi: PendingIntent) {
        if (triggerAtMillis <= System.currentTimeMillis()) return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            } else {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
            }
        } catch (_: SecurityException) {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    private fun computeFireTimes(event: CalendarEvent): List<Long> {
        val baseDateTime = EventOccurrence.nextBaseDateTime(event) ?: return emptyList()
        val zone = ZoneId.systemDefault()
        val times = mutableListOf<LocalDateTime>()

        if (event.remindOnDay) {
            times += baseDateTime
        }
        event.advanceReminders.take(2).forEach { adv ->
            val delta = when (adv.unit) {
                AdvanceUnit.MINUTES -> java.time.Duration.ofMinutes(adv.amount.toLong())
                AdvanceUnit.HOURS -> java.time.Duration.ofHours(adv.amount.toLong())
                AdvanceUnit.DAYS -> java.time.Duration.ofDays(adv.amount.toLong())
            }
            times += baseDateTime.minus(delta)
        }

        return times
            .filter { it.isAfter(LocalDateTime.now()) }
            .distinct()
            .map { it.atZone(zone).toInstant().toEpochMilli() }
            .sorted()
    }

    private fun requestCode(eventId: Long, slot: Int): Int =
        ((eventId % Int.MAX_VALUE).toInt() * 10 + slot)

    companion object {
        const val ACTION_REMIND = "com.moodcalendar.app.ACTION_REMIND"
        const val EXTRA_EVENT_ID = "event_id"
        const val EXTRA_TITLE = "title"
        const val EXTRA_TYPE = "type"
        const val EXTRA_SLOT = "slot"
    }
}
