package com.moodcalendar.app.di

import android.content.Context
import androidx.room.Room
import com.moodcalendar.app.data.local.AppDatabase
import com.moodcalendar.app.data.preferences.SettingsRepository
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.reminder.ReminderScheduler

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        "mood_calendar.db"
    ).fallbackToDestructiveMigration().build()

    val settingsRepository = SettingsRepository(appContext)
    val reminderScheduler = ReminderScheduler(appContext)
    val eventRepository = EventRepository(database.calendarEventDao(), reminderScheduler)
    val moodRepository = MoodRepository(database.moodEntryDao(), database.moodImageDao())
}
