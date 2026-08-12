package com.moodcalendar.app.util

import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.local.IntListJson
import com.moodcalendar.app.data.local.StringListJson
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode
import com.moodcalendar.app.data.repository.CalendarEvent
import java.time.LocalDate
import java.time.LocalDateTime

object EventOccurrence {

    fun occursOn(event: CalendarEventEntity, date: LocalDate): Boolean {
        val target = event.targetDate.toLocalDateOrNull() ?: return false
        return occursOn(
            type = event.type,
            target = target,
            recurrence = event.recurrence,
            yearlyMode = event.yearlyMode,
            yearlyDates = StringListJson.decode(event.yearlyDatesJson),
            weeklyDays = IntListJson.decode(event.weeklyDaysJson),
            monthlyDays = IntListJson.decode(event.monthlyDaysJson),
            calendarSystem = event.calendarSystem,
            lunarMonth = event.lunarMonth,
            lunarDay = event.lunarDay,
            date = date
        )
    }

    fun occursOn(event: CalendarEvent, date: LocalDate): Boolean {
        val target = event.targetDate.toLocalDateOrNull() ?: return false
        return occursOn(
            type = event.type,
            target = target,
            recurrence = event.recurrence,
            yearlyMode = event.yearlyMode,
            yearlyDates = event.yearlyDates,
            weeklyDays = event.weeklyDays,
            monthlyDays = event.monthlyDays,
            calendarSystem = event.calendarSystem,
            lunarMonth = event.lunarMonth,
            lunarDay = event.lunarDay,
            date = date
        )
    }

    private fun occursOn(
        type: EventType,
        target: LocalDate,
        recurrence: Recurrence,
        yearlyMode: YearlyMode,
        yearlyDates: List<String>,
        weeklyDays: List<Int>,
        monthlyDays: List<Int>,
        calendarSystem: CalendarSystem,
        lunarMonth: Int,
        lunarDay: Int,
        date: LocalDate
    ): Boolean {
        if (type == EventType.COUNTDOWN) {
            return date == target
        }

        val lunar = calendarSystem == CalendarSystem.LUNAR

        return when (recurrence) {
            Recurrence.NONE -> date == target
            Recurrence.DAILY -> !date.isBefore(target)
            Recurrence.WEEKLY -> {
                val days = weeklyDays.ifEmpty { listOf(target.dayOfWeek.value) }
                date.dayOfWeek.value in days && !date.isBefore(target)
            }
            Recurrence.MONTHLY -> {
                if (lunar) {
                    val days = monthlyDays.ifEmpty {
                        listOf(lunarDay.takeIf { it > 0 } ?: LunarDateUtils.lunarDayOf(target))
                    }
                    days.any { LunarDateUtils.matchesLunarDay(date, it) } && !date.isBefore(target)
                } else {
                    val days = monthlyDays.ifEmpty { listOf(target.dayOfMonth) }
                    date.dayOfMonth in days && !date.isBefore(target)
                }
            }
            Recurrence.YEARLY -> {
                if (lunar) {
                    when (yearlyMode) {
                        YearlyMode.SAME_DAY -> {
                            val m = if (lunarMonth != 0) lunarMonth else LunarDateUtils.lunarMonthOf(target)
                            val d = if (lunarDay > 0) lunarDay else LunarDateUtils.lunarDayOf(target)
                            LunarDateUtils.matchesLunarMonthDay(date, m, d) && !date.isBefore(target)
                        }
                        YearlyMode.CUSTOM_DATES -> {
                            val list = yearlyDates.ifEmpty {
                                val m = kotlin.math.abs(if (lunarMonth != 0) lunarMonth else LunarDateUtils.lunarMonthOf(target))
                                val d = if (lunarDay > 0) lunarDay else LunarDateUtils.lunarDayOf(target)
                                listOf("%02d-%02d".format(m, d))
                            }
                            list.any { md ->
                                val parts = md.split("-")
                                val m = parts.getOrNull(0)?.toIntOrNull() ?: return@any false
                                val d = parts.getOrNull(1)?.toIntOrNull() ?: return@any false
                                // Prefer non-leap month match for custom MM-dd
                                LunarDateUtils.matchesLunarMonthDay(date, m, d) ||
                                    LunarDateUtils.matchesLunarMonthDay(date, -m, d)
                            } && !date.isBefore(target)
                        }
                    }
                } else {
                    when (yearlyMode) {
                        YearlyMode.SAME_DAY -> {
                            val md = target.toMonthDay()
                            monthDayMatches(date, md) && !date.isBefore(target)
                        }
                        YearlyMode.CUSTOM_DATES -> {
                            val list = yearlyDates.ifEmpty { listOf(target.toMonthDay()) }
                            list.any { monthDayMatches(date, it) } && !date.isBefore(target)
                        }
                    }
                }
            }
        }
    }

    fun nextOccurrenceOnOrAfter(event: CalendarEventEntity, from: LocalDate = LocalDate.now()): LocalDate? {
        var cursor = from
        repeat(400) {
            if (occursOn(event, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    fun nextOccurrenceOnOrAfter(event: CalendarEvent, from: LocalDate = LocalDate.now()): LocalDate? {
        var cursor = from
        repeat(400) {
            if (occursOn(event, cursor)) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    fun nextBaseDateTime(event: CalendarEvent, now: LocalDateTime = LocalDateTime.now()): LocalDateTime? {
        val time = event.remindTime.toLocalTimeOrDefault()
        val target = event.targetDate.toLocalDateOrNull() ?: return null
        val fromDate = now.toLocalDate()
        val lunar = event.calendarSystem == CalendarSystem.LUNAR

        if (event.type == EventType.COUNTDOWN || event.recurrence == Recurrence.NONE) {
            val dt = LocalDateTime.of(target, time)
            return dt.takeIf { it.isAfter(now) }
        }

        val nextDate: LocalDate? = when (event.recurrence) {
            Recurrence.NONE -> target.takeIf { !it.isBefore(fromDate) }
            Recurrence.DAILY -> {
                val start = maxOf(target, fromDate)
                val todayDt = LocalDateTime.of(start, time)
                if (start == fromDate && !todayDt.isAfter(now)) start.plusDays(1) else start
            }
            Recurrence.WEEKLY -> {
                val days = event.weeklyDays.ifEmpty { listOf(target.dayOfWeek.value) }
                var candidate = nextWeeklyDate(days, maxOf(target, fromDate))
                if (candidate != null && candidate == fromDate && !LocalDateTime.of(candidate, time).isAfter(now)) {
                    candidate = nextWeeklyDate(days, fromDate.plusDays(1))
                }
                candidate
            }
            Recurrence.MONTHLY -> {
                if (lunar) {
                    val days = event.monthlyDays.ifEmpty {
                        listOf(event.lunarDay.takeIf { it > 0 } ?: LunarDateUtils.lunarDayOf(target))
                    }
                    var candidate = LunarDateUtils.nextLunarMonthly(days, maxOf(target, fromDate))
                    if (candidate != null && candidate == fromDate &&
                        !LocalDateTime.of(candidate, time).isAfter(now)
                    ) {
                        candidate = LunarDateUtils.nextLunarMonthly(days, fromDate.plusDays(1))
                    }
                    candidate
                } else {
                    val days = event.monthlyDays.ifEmpty { listOf(target.dayOfMonth) }
                    var candidate = nextMonthlyDate(days, maxOf(target, fromDate))
                    if (candidate != null && candidate == fromDate && !LocalDateTime.of(candidate, time).isAfter(now)) {
                        candidate = nextMonthlyDate(days, fromDate.plusDays(1))
                    }
                    candidate
                }
            }
            Recurrence.YEARLY -> {
                if (lunar) {
                    when (event.yearlyMode) {
                        YearlyMode.SAME_DAY -> {
                            val m = if (event.lunarMonth != 0) event.lunarMonth else LunarDateUtils.lunarMonthOf(target)
                            val d = if (event.lunarDay > 0) event.lunarDay else LunarDateUtils.lunarDayOf(target)
                            var candidate = LunarDateUtils.nextLunarAnnual(m, d, maxOf(target, fromDate))
                            if (candidate != null && candidate == fromDate &&
                                !LocalDateTime.of(candidate, time).isAfter(now)
                            ) {
                                candidate = LunarDateUtils.nextLunarAnnual(m, d, fromDate.plusDays(1))
                            }
                            candidate
                        }
                        YearlyMode.CUSTOM_DATES -> {
                            val list = event.yearlyDates.ifEmpty {
                                val m = kotlin.math.abs(
                                    if (event.lunarMonth != 0) event.lunarMonth else LunarDateUtils.lunarMonthOf(target)
                                )
                                val d = if (event.lunarDay > 0) event.lunarDay else LunarDateUtils.lunarDayOf(target)
                                listOf("%02d-%02d".format(m, d))
                            }
                            nextLunarCustomAnnual(list, maxOf(target, fromDate), time, now)
                        }
                    }
                } else {
                    when (event.yearlyMode) {
                        YearlyMode.SAME_DAY -> {
                            var candidate = nextAnnualDate(target, maxOf(target, fromDate))
                            if (candidate == fromDate && !LocalDateTime.of(candidate, time).isAfter(now)) {
                                candidate = nextAnnualDate(target, fromDate.plusDays(1))
                            }
                            candidate
                        }
                        YearlyMode.CUSTOM_DATES -> {
                            val list = event.yearlyDates.ifEmpty { listOf(target.toMonthDay()) }
                            var candidate = nextCustomAnnualDate(list, maxOf(target, fromDate))
                            if (candidate != null && candidate == fromDate &&
                                !LocalDateTime.of(candidate, time).isAfter(now)
                            ) {
                                candidate = nextCustomAnnualDate(list, fromDate.plusDays(1))
                            }
                            candidate
                        }
                    }
                }
            }
        }

        return nextDate?.let { LocalDateTime.of(it, time) }?.takeIf { it.isAfter(now) }
    }

    private fun nextLunarCustomAnnual(
        monthDays: List<String>,
        from: LocalDate,
        time: java.time.LocalTime,
        now: LocalDateTime
    ): LocalDate? {
        var year = LunarDateUtils.fromSolar(from).year
        repeat(16) {
            val candidates = monthDays.mapNotNull { md ->
                val parts = md.split("-")
                val m = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val d = parts.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                LunarDateUtils.toSolar(LunarYmd(year, m, d))
                    ?: LunarDateUtils.toSolar(LunarYmd(year, -m, d))
            }.filter { !it.isBefore(from) }.sorted()
            val hit = candidates.firstOrNull { LocalDateTime.of(it, time).isAfter(now) }
                ?: candidates.firstOrNull()
            if (hit != null && LocalDateTime.of(hit, time).isAfter(now)) return hit
            if (hit != null && hit == from && !LocalDateTime.of(hit, time).isAfter(now)) {
                year++
                return@repeat
            }
            if (candidates.isNotEmpty() && hit != null) return hit
            year++
        }
        return null
    }
}
