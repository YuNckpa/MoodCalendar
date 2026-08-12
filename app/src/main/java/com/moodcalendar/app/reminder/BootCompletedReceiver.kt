package com.moodcalendar.app.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.moodcalendar.app.MoodCalendarApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIMEZONE_CHANGED &&
            action != Intent.ACTION_TIME_CHANGED
        ) return

        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val app = context.applicationContext as MoodCalendarApp
                app.container.eventRepository.rescheduleAll()
            } finally {
                pending.finish()
            }
        }
    }
}
