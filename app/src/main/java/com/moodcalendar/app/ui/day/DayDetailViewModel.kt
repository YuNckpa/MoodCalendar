package com.moodcalendar.app.ui.day

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.data.repository.MoodEntry
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.di.AppContainer
import com.moodcalendar.app.util.EventOccurrence
import com.moodcalendar.app.util.LunarHolidayProvider
import com.moodcalendar.app.util.toLocalDateOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class DayDetailUiState(
    val date: String,
    val holidayLabels: List<String> = emptyList(),
    val events: List<CalendarEventEntity> = emptyList(),
    val moods: List<MoodEntry> = emptyList()
)

class DayDetailViewModel(
    private val dateIso: String,
    eventRepository: EventRepository,
    moodRepository: MoodRepository
) : ViewModel() {
    val uiState: StateFlow<DayDetailUiState> = combine(
        eventRepository.observeEvents(),
        moodRepository.observeByDate(dateIso)
    ) { events, moods ->
        val date = dateIso.toLocalDateOrNull()
        val labels = date?.let { LunarHolidayProvider.extras(it).labels }.orEmpty()
        DayDetailUiState(
            date = dateIso,
            holidayLabels = labels,
            events = if (date == null) emptyList() else events.filter {
                EventOccurrence.occursOn(it, date)
            },
            moods = moods
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        DayDetailUiState(date = dateIso)
    )

    companion object {
        fun factory(container: AppContainer, date: String) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return DayDetailViewModel(
                    date,
                    container.eventRepository,
                    container.moodRepository
                ) as T
            }
        }
    }
}
