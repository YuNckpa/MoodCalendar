package com.moodcalendar.app

import android.app.Application
import com.moodcalendar.app.di.AppContainer
import com.moodcalendar.app.reminder.ReminderReceiver

class MoodCalendarApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderReceiver.ensureChannel(this)
    }
}
