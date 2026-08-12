package com.moodcalendar.app.ui.calendar

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.data.repository.MoodEntry
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.di.AppContainer
import com.moodcalendar.app.util.DateFormats
import com.moodcalendar.app.util.EventOccurrence
import com.moodcalendar.app.util.LunarHolidayProvider
import com.moodcalendar.app.util.toIso
import com.moodcalendar.app.util.toLocalDateOrNull
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import java.time.YearMonth

data class DayMarkers(
    val hasEvent: Boolean = false,
    val hasMood: Boolean = false,
    val hasPeriod: Boolean = false,
    val lunarText: String = "",
    val badge: String? = null,
    val coverImageUri: String? = null
)

data class CalendarUiState(
    val month: YearMonth = YearMonth.now(),
    val selectedDate: LocalDate = LocalDate.now(),
    val selectedDateLabel: String = "",
    val markers: Map<LocalDate, DayMarkers> = emptyMap(),
    val yearOptions: List<Int> = emptyList(),
    val isCurrentMonth: Boolean = true,
    val holidayLabels: List<String> = emptyList(),
    val selectedEvents: List<CalendarEventEntity> = emptyList(),
    val selectedMoods: List<MoodEntry> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class CalendarViewModel(
    eventRepository: EventRepository,
    moodRepository: MoodRepository
) : ViewModel() {
    private val month = MutableStateFlow(YearMonth.now())
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val currentYear = LocalDate.now().year
    private val yearOptions = ((currentYear - 90)..(currentYear + 90)).toList()

    private data class MoodFlags(
        val moodDates: Set<String>,
        val periodDates: Set<String>,
        val covers: Map<String, String>
    )

    private val moodFlags = combine(
        moodRepository.observeDatesWithMood(),
        moodRepository.observeDatesWithPeriod(),
        moodRepository.observeMoodCoverByDate()
    ) { moodDates, periodDates, covers ->
        MoodFlags(moodDates, periodDates, covers)
    }

    private val selectedMoodsFlow = selectedDate.flatMapLatest { date ->
        moodRepository.observeByDate(date.toIso())
    }

    val uiState: StateFlow<CalendarUiState> = combine(
        month,
        selectedDate,
        eventRepository.observeEvents(),
        moodFlags,
        selectedMoodsFlow
    ) { ym, selected, events, flags, moods ->
        val dayEvents = events.filter { EventOccurrence.occursOn(it, selected) }
        CalendarUiState(
            month = ym,
            selectedDate = selected,
            selectedDateLabel = selected.format(DateFormats.DISPLAY),
            markers = buildMarkers(ym, events, flags.moodDates, flags.periodDates, flags.covers),
            yearOptions = yearOptions,
            isCurrentMonth = ym == YearMonth.now(),
            holidayLabels = LunarHolidayProvider.extras(selected).labels,
            selectedEvents = dayEvents,
            selectedMoods = moods
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        CalendarUiState(yearOptions = yearOptions)
    )

    fun previousMonth() {
        val next = month.value.minusMonths(1)
        if (next.year >= currentYear - 90) month.value = next
    }

    fun nextMonth() {
        val next = month.value.plusMonths(1)
        if (next.year <= currentYear + 90) month.value = next
    }

    fun setYear(year: Int) {
        val y = year.coerceIn(currentYear - 90, currentYear + 90)
        month.value = YearMonth.of(y, month.value.month)
    }

    fun setMonthNumber(monthNumber: Int) {
        month.value = YearMonth.of(month.value.year, monthNumber.coerceIn(1, 12))
    }

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        month.value = YearMonth.from(date)
    }

    fun goToToday() {
        val today = LocalDate.now()
        selectedDate.value = today
        month.value = YearMonth.from(today)
    }

    companion object {
        fun factory(container: AppContainer) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CalendarViewModel(container.eventRepository, container.moodRepository) as T
            }
        }

        private fun buildMarkers(
            month: YearMonth,
            events: List<CalendarEventEntity>,
            moodDates: Set<String>,
            periodDates: Set<String>,
            covers: Map<String, String>
        ): Map<LocalDate, DayMarkers> {
            val map = mutableMapOf<LocalDate, DayMarkers>()
            val start = month.atDay(1)
            val end = month.atEndOfMonth()

            moodDates.forEach { iso ->
                val d = iso.toLocalDateOrNull() ?: return@forEach
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    map[d] = (map[d] ?: DayMarkers()).copy(
                        hasMood = true,
                        coverImageUri = covers[iso]
                    )
                }
            }
            periodDates.forEach { iso ->
                val d = iso.toLocalDateOrNull() ?: return@forEach
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    map[d] = (map[d] ?: DayMarkers()).copy(hasPeriod = true)
                }
            }
            covers.forEach { (iso, uri) ->
                val d = iso.toLocalDateOrNull() ?: return@forEach
                if (!d.isBefore(start) && !d.isAfter(end)) {
                    val prev = map[d] ?: DayMarkers()
                    map[d] = prev.copy(hasMood = true, coverImageUri = uri)
                }
            }

            var cursor = start
            while (!cursor.isAfter(end)) {
                val extras = LunarHolidayProvider.extras(cursor)
                val hit = events.any { EventOccurrence.occursOn(it, cursor) }
                val prev = map[cursor] ?: DayMarkers()
                map[cursor] = prev.copy(
                    hasEvent = hit,
                    lunarText = extras.lunarText,
                    badge = extras.badge
                )
                cursor = cursor.plusDays(1)
            }
            return map
        }
    }
}
