package com.moodcalendar.app.data.repository

import com.moodcalendar.app.data.local.AdvanceReminderJson
import com.moodcalendar.app.data.local.CalendarEventDao
import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.local.IntListJson
import com.moodcalendar.app.data.local.StringListJson
import com.moodcalendar.app.data.model.AdvanceReminder
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode
import com.moodcalendar.app.reminder.ReminderScheduler
import kotlinx.coroutines.flow.Flow

data class CalendarEvent(
    val id: Long = 0,
    val type: EventType,
    val title: String,
    val note: String = "",
    val targetDate: String,
    val allDay: Boolean = true,
    val recurrence: Recurrence = Recurrence.YEARLY,
    val remindOnDay: Boolean = true,
    val remindTime: String = "09:00",
    val advanceReminders: List<AdvanceReminder> = emptyList(),
    val displayMode: DisplayMode = DisplayMode.DAYS_ONLY,
    val yearlyMode: YearlyMode = YearlyMode.SAME_DAY,
    val yearlyDates: List<String> = emptyList(),
    val weeklyDays: List<Int> = emptyList(),
    val monthlyDays: List<Int> = emptyList(),
    val backgroundImageUri: String? = null,
    val calendarSystem: CalendarSystem = CalendarSystem.SOLAR,
    val lunarYear: Int = 0,
    val lunarMonth: Int = 0,
    val lunarDay: Int = 0,
    val sortOrder: Int = 0,
    val archived: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

fun CalendarEventEntity.toDomain() = CalendarEvent(
    id = id,
    type = type,
    title = title,
    note = note,
    targetDate = targetDate,
    allDay = allDay,
    recurrence = recurrence,
    remindOnDay = remindOnDay,
    remindTime = remindTime,
    advanceReminders = AdvanceReminderJson.decode(advanceRemindersJson),
    displayMode = displayMode,
    yearlyMode = yearlyMode,
    yearlyDates = StringListJson.decode(yearlyDatesJson),
    weeklyDays = IntListJson.decode(weeklyDaysJson),
    monthlyDays = IntListJson.decode(monthlyDaysJson),
    backgroundImageUri = backgroundImageUri,
    calendarSystem = calendarSystem,
    lunarYear = lunarYear,
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

fun CalendarEvent.toEntity() = CalendarEventEntity(
    id = id,
    type = type,
    title = title,
    note = note,
    targetDate = targetDate,
    allDay = allDay,
    recurrence = recurrence,
    remindOnDay = remindOnDay,
    remindTime = remindTime,
    advanceRemindersJson = AdvanceReminderJson.encode(advanceReminders),
    displayMode = displayMode,
    yearlyMode = yearlyMode,
    yearlyDatesJson = StringListJson.encode(yearlyDates),
    weeklyDaysJson = IntListJson.encode(weeklyDays),
    monthlyDaysJson = IntListJson.encode(monthlyDays),
    backgroundImageUri = backgroundImageUri,
    calendarSystem = calendarSystem,
    lunarYear = lunarYear,
    lunarMonth = lunarMonth,
    lunarDay = lunarDay,
    sortOrder = sortOrder,
    archived = archived,
    createdAt = createdAt,
    updatedAt = updatedAt
)

class EventRepository(
    private val dao: CalendarEventDao,
    private val scheduler: ReminderScheduler
) {
    fun observeEvents(): Flow<List<CalendarEventEntity>> = dao.observeActive()

    suspend fun getById(id: Long): CalendarEvent? = dao.getById(id)?.toDomain()

    fun observeById(id: Long): Flow<CalendarEventEntity?> = dao.observeById(id)

    suspend fun save(event: CalendarEvent): Long {
        val now = System.currentTimeMillis()
        val entity = event.toEntity().copy(
            updatedAt = now,
            createdAt = if (event.id == 0L) now else event.createdAt,
            sortOrder = if (event.id == 0L && event.sortOrder == 0) {
                // Keep 0 for new items so auto-sort still applies until user reorders
                0
            } else {
                event.sortOrder
            }
        )
        val id = if (entity.id == 0L) {
            dao.insert(entity)
        } else {
            dao.update(entity)
            entity.id
        }
        val saved = dao.getById(id)?.toDomain() ?: return id
        runCatching { scheduler.schedule(saved) }
        return id
    }

    suspend fun updateSortOrders(orderedIds: List<Long>) {
        val now = System.currentTimeMillis()
        val entities = orderedIds.mapIndexedNotNull { index, id ->
            dao.getById(id)?.copy(sortOrder = index + 1, updatedAt = now)
        }
        if (entities.isNotEmpty()) dao.updateAll(entities)
    }

    suspend fun delete(id: Long) {
        scheduler.cancel(id)
        dao.deleteById(id)
    }

    suspend fun getAllActive(): List<CalendarEvent> = dao.getActive().map { it.toDomain() }

    suspend fun rescheduleAll() {
        getAllActive().forEach { runCatching { scheduler.schedule(it) } }
    }
}
