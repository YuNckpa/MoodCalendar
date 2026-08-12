package com.moodcalendar.app.util

import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.Recurrence
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.Period
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

object DateFormats {
    val ISO_DATE: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    val DISPLAY: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月d日")
    val MONTH_TITLE: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy年M月")
    val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    val MONTH_DAY: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd")
}

fun LocalDate.toIso(): String = format(DateFormats.ISO_DATE)

fun LocalDate.toMonthDay(): String = format(DateFormats.MONTH_DAY)

fun String.toLocalDate(): LocalDate = LocalDate.parse(this.trim(), DateFormats.ISO_DATE)

fun String.toLocalDateOrNull(): LocalDate? =
    runCatching { toLocalDate() }.getOrNull()

fun String.toLocalTimeOrDefault(default: LocalTime = LocalTime.of(9, 0)): LocalTime =
    runCatching { LocalTime.parse(this, DateFormats.TIME) }.getOrDefault(default)

fun YearMonth.calendarDays(firstDayOfWeek: DayOfWeek = DayOfWeek.MONDAY): List<LocalDate?> {
    val first = atDay(1)
    val start = first.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
    val last = atEndOfMonth()
    val end = last.with(TemporalAdjusters.nextOrSame(firstDayOfWeek.plus(6)))
    val days = mutableListOf<LocalDate?>()
    var cursor = start
    while (!cursor.isAfter(end)) {
        days += if (YearMonth.from(cursor) == this) cursor else null
        cursor = cursor.plusDays(1)
    }
    return days
}

fun daysBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.DAYS.between(from, to)

fun yearsBetween(from: LocalDate, to: LocalDate): Long = ChronoUnit.YEARS.between(from, to)

/**
 * Format elapsed or remaining span.
 * [from] is the earlier date for elapsed, or today for remaining when [to] is future.
 */
fun formatDateSpan(
    from: LocalDate,
    to: LocalDate,
    mode: DisplayMode,
    pastPrefix: String,
    futurePrefix: String,
    todayText: String
): String {
    val days = daysBetween(from, to)
    when {
        days == 0L -> return todayText
        days > 0 -> {
            return when (mode) {
                DisplayMode.DAYS_ONLY -> "$futurePrefix $days 天"
                DisplayMode.YEARS_AND_DAYS -> {
                    val period = Period.between(from, to)
                    val years = period.years
                    val remDays = ChronoUnit.DAYS.between(from.plusYears(years.toLong()), to)
                    if (years > 0) "$futurePrefix $years 年 $remDays 天" else "$futurePrefix $remDays 天"
                }
            }
        }
        else -> {
            val absDays = -days
            return when (mode) {
                DisplayMode.DAYS_ONLY -> "$pastPrefix $absDays 天"
                DisplayMode.YEARS_AND_DAYS -> {
                    val period = Period.between(to, from)
                    val years = period.years
                    val remDays = ChronoUnit.DAYS.between(to.plusYears(years.toLong()), from)
                    if (years > 0) "$pastPrefix $years 年 $remDays 天" else "$pastPrefix $remDays 天"
                }
            }
        }
    }
}

fun LocalDateTime.atNextOccurrence(
    recurrence: Recurrence,
    after: LocalDateTime = LocalDateTime.now()
): LocalDateTime? {
    var candidate = this
    var guard = 0
    while (!candidate.isAfter(after)) {
        candidate = when (recurrence) {
            Recurrence.NONE -> return if (this.isAfter(after)) this else null
            Recurrence.DAILY -> candidate.plusDays(1)
            Recurrence.WEEKLY -> candidate.plusWeeks(1)
            Recurrence.MONTHLY -> candidate.plusMonthsSafe(1)
            Recurrence.YEARLY -> candidate.plusYearsSafe(1)
        }
        if (++guard > 5000) return null
    }
    return candidate
}

private fun LocalDateTime.plusMonthsSafe(months: Long): LocalDateTime {
    val nextMonth = toLocalDate().plusMonths(months)
    val day = dayOfMonth.coerceAtMost(nextMonth.lengthOfMonth())
    return LocalDateTime.of(nextMonth.withDayOfMonth(day), toLocalTime())
}

private fun LocalDateTime.plusYearsSafe(years: Long): LocalDateTime {
    val nextYear = toLocalDate().plusYears(years)
    val day = dayOfMonth.coerceAtMost(nextYear.lengthOfMonth())
    return LocalDateTime.of(nextYear.withDayOfMonth(day), toLocalTime())
}

/** Next occurrence on/after [from] for an annual date (handles Feb 29). */
fun nextAnnualDate(base: LocalDate, from: LocalDate = LocalDate.now()): LocalDate {
    fun resolve(year: Int): LocalDate {
        val maxDay = base.month.length(LocalDate.of(year, 1, 1).isLeapYear)
        val day = base.dayOfMonth.coerceAtMost(maxDay)
        return LocalDate.of(year, base.month, day)
    }

    var candidate = resolve(from.year)
    if (candidate.isBefore(from)) {
        candidate = resolve(from.year + 1)
    }
    return candidate
}

/**
 * Next date on/after [from] matching any MM-dd in [monthDays].
 * Invalid dates for a year (e.g. 02-30) are skipped.
 */
fun nextCustomAnnualDate(monthDays: List<String>, from: LocalDate = LocalDate.now()): LocalDate? {
    if (monthDays.isEmpty()) return null
    var year = from.year
    repeat(8) {
        val candidates = monthDays.mapNotNull { md ->
            parseMonthDayInYear(md, year)
        }.filter { !it.isBefore(from) }.sorted()
        if (candidates.isNotEmpty()) return candidates.first()
        year++
    }
    return null
}

fun parseMonthDayInYear(monthDay: String, year: Int): LocalDate? {
    val parts = monthDay.trim().split("-")
    if (parts.size != 2) return null
    val month = parts[0].toIntOrNull() ?: return null
    val day = parts[1].toIntOrNull() ?: return null
    if (month !in 1..12 || day !in 1..31) return null
    val max = YearMonth.of(year, month).lengthOfMonth()
    if (day > max) return null // skip invalid e.g. Feb 30
    return LocalDate.of(year, month, day)
}

/**
 * Next weekly occurrence on selected ISO weekdays (1=Mon…7=Sun) on/after [from].
 */
fun nextWeeklyDate(weekdays: List<Int>, from: LocalDate = LocalDate.now()): LocalDate? {
    val set = weekdays.filter { it in 1..7 }.toSet()
    if (set.isEmpty()) return null
    var cursor = from
    repeat(14) {
        if (cursor.dayOfWeek.value in set) return cursor
        cursor = cursor.plusDays(1)
    }
    return null
}

/**
 * Next monthly occurrence on selected days-of-month.
 * If a month lacks that day (e.g. 31 in Feb), that day is skipped for that month.
 */
fun nextMonthlyDate(daysOfMonth: List<Int>, from: LocalDate = LocalDate.now()): LocalDate? {
    val set = daysOfMonth.filter { it in 1..31 }.toSet()
    if (set.isEmpty()) return null
    var ym = YearMonth.from(from)
    repeat(48) {
        val candidates = set.mapNotNull { day ->
            if (day <= ym.lengthOfMonth()) ym.atDay(day) else null
        }.filter { !it.isBefore(from) }.sorted()
        if (candidates.isNotEmpty()) return candidates.first()
        ym = ym.plusMonths(1)
    }
    return null
}

fun monthDayMatches(date: LocalDate, monthDay: String): Boolean {
    return date.toMonthDay() == monthDay.trim()
}
