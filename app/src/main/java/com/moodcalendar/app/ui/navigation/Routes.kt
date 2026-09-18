package com.moodcalendar.app.ui.navigation

object Routes {
    const val CALENDAR = "calendar"
    const val EVENTS = "events"
    const val MOODS = "moods"
    const val SETTINGS = "settings"
    const val SETTINGS_THEME = "settings/theme"
    const val SETTINGS_REMINDER = "settings/reminder"
    const val SETTINGS_AUTH = "settings/auth"
    const val SOCIAL = "social"
    const val EVENT_EDIT = "event_edit?eventId={eventId}&date={date}"
    const val MOOD_EDIT = "mood_edit?moodId={moodId}&date={date}"

    fun eventEdit(eventId: Long? = null, date: String? = null): String {
        val id = eventId ?: -1L
        val d = date.orEmpty()
        return "event_edit?eventId=$id&date=$d"
    }

    fun moodEdit(moodId: Long? = null, date: String = ""): String {
        val id = moodId ?: -1L
        return "mood_edit?moodId=$id&date=$date"
    }
}
