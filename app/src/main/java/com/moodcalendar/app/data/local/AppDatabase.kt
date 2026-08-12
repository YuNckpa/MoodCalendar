package com.moodcalendar.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.MoodVisibility
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode

class Converters {
    @TypeConverter
    fun toEventType(value: String): EventType = EventType.valueOf(value)

    @TypeConverter
    fun fromEventType(value: EventType): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toVisibility(value: String): MoodVisibility = MoodVisibility.valueOf(value)

    @TypeConverter
    fun fromVisibility(value: MoodVisibility): String = value.name

    @TypeConverter
    fun toDisplayMode(value: String): DisplayMode = DisplayMode.valueOf(value)

    @TypeConverter
    fun fromDisplayMode(value: DisplayMode): String = value.name

    @TypeConverter
    fun toYearlyMode(value: String): YearlyMode = YearlyMode.valueOf(value)

    @TypeConverter
    fun fromYearlyMode(value: YearlyMode): String = value.name

    @TypeConverter
    fun toCalendarSystem(value: String): CalendarSystem = CalendarSystem.valueOf(value)

    @TypeConverter
    fun fromCalendarSystem(value: CalendarSystem): String = value.name
}

@Database(
    entities = [
        CalendarEventEntity::class,
        MoodEntryEntity::class,
        MoodImageEntity::class
    ],
    version = 5,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun calendarEventDao(): CalendarEventDao
    abstract fun moodEntryDao(): MoodEntryDao
    abstract fun moodImageDao(): MoodImageDao
}
