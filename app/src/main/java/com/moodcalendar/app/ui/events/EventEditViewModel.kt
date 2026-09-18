package com.moodcalendar.app.ui.events

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.model.AdvanceReminder
import com.moodcalendar.app.data.model.AdvanceUnit
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode
import com.moodcalendar.app.data.repository.CalendarEvent
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.util.ImageStore
import com.moodcalendar.app.util.LunarDateUtils
import com.moodcalendar.app.util.LunarYmd
import com.moodcalendar.app.util.toIso
import com.moodcalendar.app.util.toLocalDateOrNull
import com.moodcalendar.app.util.toMonthDay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class EventEditUiState(
    val id: Long = 0,
    val type: EventType = EventType.ANNIVERSARY,
    val title: String = "",
    val note: String = "",
    val targetDate: String = LocalDate.now().toIso(),
    val calendarSystem: CalendarSystem = CalendarSystem.SOLAR,
    val lunarYear: Int = LunarDateUtils.fromSolar(LocalDate.now()).year,
    val lunarMonth: Int = LunarDateUtils.fromSolar(LocalDate.now()).month,
    val lunarDay: Int = LunarDateUtils.fromSolar(LocalDate.now()).day,
    val recurrence: Recurrence = Recurrence.YEARLY,
    val remindOnDay: Boolean = true,
    val remindHour: Int = 9,
    val remindMinute: Int = 0,
    val enableAdvance: Boolean = false,
    val advanceAmount: Int = 1,
    val advanceUnit: AdvanceUnit = AdvanceUnit.DAYS,
    val displayMode: DisplayMode = DisplayMode.DAYS_ONLY,
    val yearlyMode: YearlyMode = YearlyMode.SAME_DAY,
    val yearlyDates: List<String> = emptyList(),
    val weeklyDays: List<Int> = listOf(LocalDate.now().dayOfWeek.value),
    val monthlyDays: List<Int> = listOf(LocalDate.now().dayOfMonth),
    val backgroundImageUri: String? = null,
    val sortOrder: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val ownerId: String? = null,
    val syncId: String? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: String? = null
)

class EventEditViewModel(
    application: Application,
    private val eventRepository: EventRepository,
    private val eventId: Long?,
    initialDate: String?
) : AndroidViewModel(application) {
    private val seedDate = initialDate?.toLocalDateOrNull() ?: LocalDate.now()
    private val seedLunar = LunarDateUtils.fromSolar(seedDate)
    private val _uiState = MutableStateFlow(
        EventEditUiState(
            targetDate = seedDate.toIso(),
            lunarYear = seedLunar.year,
            lunarMonth = seedLunar.month,
            lunarDay = seedLunar.day,
            weeklyDays = listOf(seedDate.dayOfWeek.value),
            monthlyDays = listOf(seedDate.dayOfMonth),
            recurrence = Recurrence.YEARLY
        )
    )
    val uiState: StateFlow<EventEditUiState> = _uiState.asStateFlow()

    init {
        if (eventId != null) {
            viewModelScope.launch {
                val existing = eventRepository.getById(eventId) ?: return@launch
                val parts = existing.remindTime.split(":")
                val hour = parts.getOrNull(0)?.toIntOrNull() ?: 9
                val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
                val advance = existing.advanceReminders.firstOrNull()
                val target = existing.targetDate.toLocalDateOrNull() ?: LocalDate.now()
                val lunar = if (existing.calendarSystem == CalendarSystem.LUNAR && existing.lunarDay > 0) {
                    LunarYmd(existing.lunarYear, existing.lunarMonth, existing.lunarDay)
                } else {
                    LunarDateUtils.fromSolar(target)
                }
                _uiState.value = EventEditUiState(
                    id = existing.id,
                    type = existing.type,
                    title = existing.title,
                    note = existing.note,
                    targetDate = existing.targetDate,
                    calendarSystem = existing.calendarSystem,
                    lunarYear = lunar.year,
                    lunarMonth = lunar.month,
                    lunarDay = lunar.day,
                    recurrence = existing.recurrence,
                    remindOnDay = existing.remindOnDay,
                    remindHour = hour,
                    remindMinute = minute,
                    enableAdvance = advance != null,
                    advanceAmount = advance?.amount ?: 1,
                    advanceUnit = advance?.unit ?: AdvanceUnit.DAYS,
                    displayMode = existing.displayMode,
                    yearlyMode = existing.yearlyMode,
                    yearlyDates = existing.yearlyDates,
                    weeklyDays = existing.weeklyDays.ifEmpty { listOf(target.dayOfWeek.value) },
                    monthlyDays = existing.monthlyDays.ifEmpty {
                        if (existing.calendarSystem == CalendarSystem.LUNAR) listOf(lunar.day)
                        else listOf(target.dayOfMonth)
                    },
                    backgroundImageUri = existing.backgroundImageUri,
                    sortOrder = existing.sortOrder,
                    createdAt = existing.createdAt,
                    ownerId = existing.ownerId,
                    syncId = existing.syncId
                )
            }
        } else {
            _uiState.update {
                it.copy(
                    recurrence = when (it.type) {
                        EventType.COUNTDOWN -> Recurrence.NONE
                        else -> Recurrence.YEARLY
                    }
                )
            }
        }
    }

    fun update(transform: (EventEditUiState) -> EventEditUiState) {
        _uiState.update(transform)
    }

    fun onTypeChange(type: EventType) {
        _uiState.update {
            it.copy(
                type = type,
                recurrence = when (type) {
                    EventType.COUNTDOWN -> Recurrence.NONE
                    EventType.ANNIVERSARY, EventType.BIRTHDAY, EventType.CUSTOM ->
                        if (it.recurrence == Recurrence.NONE) Recurrence.YEARLY else it.recurrence
                }
            )
        }
    }

    fun onCalendarSystemChange(system: CalendarSystem) {
        _uiState.update { state ->
            if (system == state.calendarSystem) return@update state
            if (system == CalendarSystem.LUNAR) {
                val solar = state.targetDate.toLocalDateOrNull() ?: LocalDate.now()
                val lunar = LunarDateUtils.fromSolar(solar)
                state.copy(
                    calendarSystem = system,
                    lunarYear = lunar.year,
                    lunarMonth = lunar.month,
                    lunarDay = lunar.day,
                    monthlyDays = listOf(lunar.day)
                )
            } else {
                val solar = LunarDateUtils.toSolar(
                    LunarYmd(state.lunarYear, state.lunarMonth, state.lunarDay)
                ) ?: state.targetDate.toLocalDateOrNull() ?: LocalDate.now()
                state.copy(
                    calendarSystem = system,
                    targetDate = solar.toIso(),
                    monthlyDays = listOf(solar.dayOfMonth)
                )
            }
        }
    }

    fun onTargetDateChange(iso: String) {
        val date = iso.toLocalDateOrNull() ?: return
        val lunar = LunarDateUtils.fromSolar(date)
        _uiState.update {
            it.copy(
                targetDate = iso,
                lunarYear = lunar.year,
                lunarMonth = lunar.month,
                lunarDay = lunar.day,
                weeklyDays = if (it.weeklyDays.size <= 1) listOf(date.dayOfWeek.value) else it.weeklyDays,
                monthlyDays = when {
                    it.calendarSystem == CalendarSystem.LUNAR && it.monthlyDays.size <= 1 ->
                        listOf(lunar.day)
                    it.calendarSystem == CalendarSystem.SOLAR && it.monthlyDays.size <= 1 ->
                        listOf(date.dayOfMonth)
                    else -> it.monthlyDays
                },
                error = null
            )
        }
    }

    fun onLunarDateChange(year: Int, month: Int, day: Int) {
        val solar = LunarDateUtils.toSolar(LunarYmd(year, month, day))
        if (solar == null) {
            _uiState.update { it.copy(error = "该农历日期无效") }
            return
        }
        _uiState.update {
            it.copy(
                lunarYear = year,
                lunarMonth = month,
                lunarDay = day,
                targetDate = solar.toIso(),
                weeklyDays = if (it.weeklyDays.size <= 1) listOf(solar.dayOfWeek.value) else it.weeklyDays,
                monthlyDays = if (it.monthlyDays.size <= 1) listOf(day) else it.monthlyDays,
                error = null
            )
        }
    }

    fun toggleWeeklyDay(day: Int) {
        _uiState.update { state ->
            val next = state.weeklyDays.toMutableSet()
            if (day in next) {
                if (next.size > 1) next.remove(day)
            } else {
                next.add(day)
            }
            state.copy(weeklyDays = next.sorted())
        }
    }

    fun toggleMonthlyDay(day: Int) {
        _uiState.update { state ->
            val next = state.monthlyDays.toMutableSet()
            if (day in next) {
                if (next.size > 1) next.remove(day)
            } else {
                next.add(day)
            }
            state.copy(monthlyDays = next.sorted())
        }
    }

    fun addYearlyDate(iso: String) {
        val date = iso.toLocalDateOrNull() ?: return
        val md = if (_uiState.value.calendarSystem == CalendarSystem.LUNAR) {
            val l = LunarDateUtils.fromSolar(date)
            "%02d-%02d".format(kotlin.math.abs(l.month), l.day)
        } else {
            date.toMonthDay()
        }
        _uiState.update {
            it.copy(yearlyDates = (it.yearlyDates + md).distinct().sorted())
        }
    }

    fun removeYearlyDate(md: String) {
        _uiState.update { it.copy(yearlyDates = it.yearlyDates - md) }
    }

    fun setBackgroundImage(uri: String?) {
        _uiState.update { it.copy(backgroundImageUri = uri) }
    }

    fun onAdvanceUnitChange(unit: AdvanceUnit) {
        _uiState.update { state ->
            val range = when (unit) {
                AdvanceUnit.DAYS -> 1..30
                AdvanceUnit.HOURS -> 1..12
                AdvanceUnit.MINUTES -> 1..60
            }
            state.copy(
                advanceUnit = unit,
                advanceAmount = state.advanceAmount.coerceIn(range)
            )
        }
    }

    fun save() {
        val state = _uiState.value
        if (state.title.isBlank()) {
            _uiState.update { it.copy(error = "请填写标题") }
            return
        }
        val target = if (state.calendarSystem == CalendarSystem.LUNAR) {
            LunarDateUtils.toSolar(LunarYmd(state.lunarYear, state.lunarMonth, state.lunarDay))
        } else {
            state.targetDate.toLocalDateOrNull()
        }
        if (target == null) {
            _uiState.update { it.copy(error = "请选择有效日期") }
            return
        }
        if (state.recurrence == Recurrence.YEARLY &&
            state.yearlyMode == YearlyMode.CUSTOM_DATES &&
            state.yearlyDates.isEmpty()
        ) {
            _uiState.update { it.copy(error = "请至少添加一个每年提醒日期") }
            return
        }
        if (state.recurrence == Recurrence.WEEKLY && state.weeklyDays.isEmpty()) {
            _uiState.update { it.copy(error = "请至少选择一个星期") }
            return
        }
        if (state.recurrence == Recurrence.MONTHLY && state.monthlyDays.isEmpty()) {
            _uiState.update { it.copy(error = "请至少选择一个日期") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, error = null) }
            val remindTime = "%02d:%02d".format(state.remindHour, state.remindMinute)
            val advances = if (state.enableAdvance) {
                listOf(AdvanceReminder(state.advanceAmount.coerceAtLeast(1), state.advanceUnit))
            } else {
                emptyList()
            }
            val lunar = LunarDateUtils.fromSolar(target)
            runCatching {
                val bg = withContext(Dispatchers.IO) {
                    ImageStore.persistSingle(
                        getApplication(),
                        state.backgroundImageUri,
                        "event_images"
                    )
                }
                eventRepository.save(
                    CalendarEvent(
                        id = state.id,
                        type = state.type,
                        title = state.title.trim(),
                        note = state.note.trim(),
                        targetDate = target.toIso(),
                        recurrence = state.recurrence,
                        remindOnDay = state.remindOnDay,
                        remindTime = remindTime,
                        advanceReminders = advances,
                        displayMode = state.displayMode,
                        yearlyMode = state.yearlyMode,
                        yearlyDates = state.yearlyDates,
                        weeklyDays = state.weeklyDays,
                        monthlyDays = state.monthlyDays,
                        backgroundImageUri = bg,
                        calendarSystem = state.calendarSystem,
                        lunarYear = if (state.calendarSystem == CalendarSystem.LUNAR) state.lunarYear else lunar.year,
                        lunarMonth = if (state.calendarSystem == CalendarSystem.LUNAR) state.lunarMonth else lunar.month,
                        lunarDay = if (state.calendarSystem == CalendarSystem.LUNAR) state.lunarDay else lunar.day,
                        sortOrder = state.sortOrder,
                        createdAt = state.createdAt,
                        ownerId = state.ownerId,
                        syncId = state.syncId
                    )
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, saved = true) }
            }.onFailure { e ->
                _uiState.update {
                    it.copy(isSaving = false, error = e.message ?: "保存失败")
                }
            }
        }
    }

    companion object {
        fun factory(
            application: Application,
            repo: EventRepository,
            eventId: Long?,
            initialDate: String?
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EventEditViewModel(application, repo, eventId, initialDate) as T
            }
        }
    }
}
