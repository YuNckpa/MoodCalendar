package com.moodcalendar.app.ui.events

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.util.EventOccurrence
import com.moodcalendar.app.util.daysBetween
import com.moodcalendar.app.util.formatDateSpan
import com.moodcalendar.app.util.nextAnnualDate
import com.moodcalendar.app.util.toLocalDateOrNull
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class EventListItem(
    val entity: CalendarEventEntity,
    val subtitle: String,
    val isToday: Boolean,
    val daysUntil: Long?,
    val section: EventListSection
)

enum class EventListSection {
    TODAY,
    UPCOMING,
    BY_TYPE
}

class EventListViewModel(
    private val eventRepository: EventRepository
) : ViewModel() {
    val items: StateFlow<List<EventListItem>> = eventRepository.observeEvents()
        .map { list -> arrange(list) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { eventRepository.delete(id) }
    }

    fun moveUp(id: Long) {
        viewModelScope.launch {
            val current = items.value.toMutableList()
            val index = current.indexOfFirst { it.entity.id == id }
            if (index <= 0) return@launch
            current.add(index - 1, current.removeAt(index))
            eventRepository.updateSortOrders(current.map { it.entity.id })
        }
    }

    fun moveDown(id: Long) {
        viewModelScope.launch {
            val current = items.value.toMutableList()
            val index = current.indexOfFirst { it.entity.id == id }
            if (index < 0 || index >= current.lastIndex) return@launch
            current.add(index + 1, current.removeAt(index))
            eventRepository.updateSortOrders(current.map { it.entity.id })
        }
    }

    companion object {
        fun factory(repo: EventRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return EventListViewModel(repo) as T
            }
        }

        private fun arrange(list: List<CalendarEventEntity>): List<EventListItem> {
            val today = LocalDate.now()
            val enriched = list.map { entity ->
                val next = EventOccurrence.nextOccurrenceOnOrAfter(entity, today)
                val isToday = EventOccurrence.occursOn(entity, today)
                val daysUntil = next?.let { daysBetween(today, it) }
                EventListItem(
                    entity = entity,
                    subtitle = subtitleFor(entity, today, next, isToday),
                    isToday = isToday,
                    daysUntil = daysUntil,
                    section = when {
                        isToday -> EventListSection.TODAY
                        daysUntil != null && daysUntil <= 30 -> EventListSection.UPCOMING
                        else -> EventListSection.BY_TYPE
                    }
                )
            }

            val hasManualOrder = enriched.map { it.entity.sortOrder }.toSet().size > 1 ||
                enriched.any { it.entity.sortOrder > 0 }
            if (hasManualOrder) {
                return enriched.sortedWith(
                    compareBy<EventListItem> { it.entity.sortOrder }
                        .thenBy { it.daysUntil ?: Long.MAX_VALUE }
                        .thenByDescending { it.entity.createdAt }
                )
            }

            val todayItems = enriched.filter { it.isToday }
                .sortedWith(
                    compareBy<EventListItem> { it.entity.remindTime }
                        .thenByDescending { it.entity.createdAt }
                )
            val upcoming = enriched.filter { !it.isToday && it.section == EventListSection.UPCOMING }
                .sortedWith(
                    compareBy<EventListItem> { it.daysUntil ?: Long.MAX_VALUE }
                        .thenBy { typeRank(it.entity.type) }
                        .thenByDescending { it.entity.createdAt }
                )
            val rest = enriched.filter { !it.isToday && it.section == EventListSection.BY_TYPE }
                .sortedWith(
                    compareBy<EventListItem> { typeRank(it.entity.type) }
                        .thenByDescending { it.entity.createdAt }
                )
            return todayItems + upcoming + rest
        }

        private fun typeRank(type: EventType): Int = when (type) {
            EventType.ANNIVERSARY -> 0
            EventType.BIRTHDAY -> 1
            EventType.COUNTDOWN -> 2
            EventType.CUSTOM -> 3
        }

        private fun subtitleFor(
            event: CalendarEventEntity,
            today: LocalDate,
            next: LocalDate?,
            isToday: Boolean
        ): String {
            if (isToday) return "今日事件"
            val target = event.targetDate.toLocalDateOrNull() ?: return "日期无效"
            val mode = event.displayMode
            return when (event.type) {
                EventType.COUNTDOWN -> formatDateSpan(
                    from = today,
                    to = target,
                    mode = mode,
                    pastPrefix = "已过",
                    futurePrefix = "还剩",
                    todayText = "就是今天"
                )
                EventType.ANNIVERSARY -> {
                    if (target.isAfter(today)) {
                        formatDateSpan(
                            from = today,
                            to = target,
                            mode = mode,
                            pastPrefix = "已过",
                            futurePrefix = "还有",
                            todayText = "就是今天"
                        )
                    } else {
                        when (mode) {
                            DisplayMode.DAYS_ONLY -> {
                                val days = daysBetween(target, today)
                                if (days == 0L) "就是今天" else "已走过 $days 天"
                            }
                            DisplayMode.YEARS_AND_DAYS -> {
                                val period = java.time.Period.between(target, today)
                                val years = period.years
                                val remDays = daysBetween(target.plusYears(years.toLong()), today)
                                when {
                                    years == 0 && remDays == 0L -> "就是今天"
                                    years > 0 -> "已走过 $years 年 $remDays 天"
                                    else -> "已走过 $remDays 天"
                                }
                            }
                        }
                    }
                }
                EventType.BIRTHDAY -> {
                    val nextBirth = next ?: nextAnnualDate(target, today)
                    val left = daysBetween(today, nextBirth)
                    if (left == 0L) "今天生日" else "还有 $left 天"
                }
                EventType.CUSTOM -> {
                    val nextDate = next ?: nextAnnualDate(target, today)
                    val left = daysBetween(today, nextDate)
                    when {
                        target == today -> "就是今天"
                        !target.isAfter(today) && event.recurrence.name == "NONE" -> {
                            formatDateSpan(
                                from = target,
                                to = today,
                                mode = mode,
                                pastPrefix = "已过",
                                futurePrefix = "还剩",
                                todayText = "就是今天"
                            )
                        }
                        else -> if (left == 0L) "就是今天" else "还有 $left 天"
                    }
                }
            }
        }
    }
}
