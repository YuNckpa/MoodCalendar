package com.moodcalendar.app.di

import android.content.Context
import androidx.room.Room
import com.moodcalendar.app.data.auth.AuthRepository
import com.moodcalendar.app.data.auth.SessionStore
import com.moodcalendar.app.data.local.AppDatabase
import com.moodcalendar.app.data.preferences.SettingsRepository
import com.moodcalendar.app.data.remote.SupabaseClient
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.data.social.SocialRepository
import com.moodcalendar.app.data.sync.SyncEngine
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
    val supabaseClient = SupabaseClient()
    val sessionStore = SessionStore(appContext)
    val authRepository = AuthRepository(supabaseClient, sessionStore)

    val moodRepository = MoodRepository(
        entryDao = database.moodEntryDao(),
        imageDao = database.moodImageDao(),
        onLocalChanged = null
    )

    val eventRepository = EventRepository(
        dao = database.calendarEventDao(),
        scheduler = reminderScheduler,
        onLocalChanged = null
    )

    val syncEngine = SyncEngine(
        client = supabaseClient,
        authRepository = authRepository,
        moodRepository = moodRepository,
        eventRepository = eventRepository,
        settingsRepository = settingsRepository
    )

    val socialRepository = SocialRepository(supabaseClient, authRepository)

    init {
        moodRepository.setOnLocalChanged { syncEngine.requestIncrementalSync() }
        eventRepository.setOnLocalChanged { syncEngine.requestIncrementalSync() }
        settingsRepository.setOnLocalChanged { syncEngine.requestIncrementalSync() }
    }
}
