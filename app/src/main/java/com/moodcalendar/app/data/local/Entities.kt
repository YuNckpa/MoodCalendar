package com.moodcalendar.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.MoodVisibility
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode

@Entity(tableName = "calendar_events")
data class CalendarEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: EventType,
    val title: String,
    val note: String = "",
    /** ISO date yyyy-MM-dd: solar anchor (also converted from lunar pick) */
    val targetDate: String,
    val allDay: Boolean = true,
    val recurrence: Recurrence = Recurrence.YEARLY,
    val remindOnDay: Boolean = true,
    /** HH:mm */
    val remindTime: String = "09:00",
    /** JSON array of AdvanceReminder */
    val advanceRemindersJson: String = "[]",
    val displayMode: DisplayMode = DisplayMode.DAYS_ONLY,
    val yearlyMode: YearlyMode = YearlyMode.SAME_DAY,
    /** JSON array of "MM-dd" for YEARLY + CUSTOM_DATES (solar or lunar month-day) */
    val yearlyDatesJson: String = "[]",
    /** JSON array of ISO weekdays 1=Mon … 7=Sun */
    val weeklyDaysJson: String = "[]",
    /** JSON array of day-of-month 1..31 (solar) or lunar day 1..30 (lunar) */
    val monthlyDaysJson: String = "[]",
    /** Optional single background image absolute path / uri */
    val backgroundImageUri: String? = null,
    val calendarSystem: CalendarSystem = CalendarSystem.SOLAR,
    /** Lunar fields used when calendarSystem == LUNAR (month may be negative for leap) */
    val lunarYear: Int = 0,
    val lunarMonth: Int = 0,
    val lunarDay: Int = 0,
    /** Manual list order; lower comes first. Auto-sort used when all equal. */
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Phase 2 reserved
    val ownerId: String? = null,
    val syncId: String? = null
)

@Entity(tableName = "mood_entries")
data class MoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** ISO date yyyy-MM-dd */
    val date: String,
    val text: String = "",
    val emoji: String = "😊",
    /** Display meaning, e.g. 开心 */
    val emojiLabel: String = "",
    val visibility: MoodVisibility = MoodVisibility.FRIENDS_ALL,
    val isPeriod: Boolean = false,
    val groupId: Long? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Phase 2 reserved
    val ownerId: String? = null,
    val syncId: String? = null
)

@Entity(
    tableName = "mood_images",
    foreignKeys = [
        ForeignKey(
            entity = MoodEntryEntity::class,
            parentColumns = ["id"],
            childColumns = ["moodEntryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("moodEntryId")]
)
data class MoodImageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val moodEntryId: Long,
    val localUri: String,
    val sortOrder: Int = 0,
    // Phase 2 reserved
    val remoteUrl: String? = null
)
