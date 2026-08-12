package com.moodcalendar.app.util

import com.nlf.calendar.Lunar
import com.nlf.calendar.Solar
import java.time.LocalDate

data class LunarYmd(
    val year: Int,
    /** 1..12；闰月用负值（与 6tail 库一致） */
    val month: Int,
    val day: Int
) {
    val isLeap: Boolean get() = month < 0
    val monthAbs: Int get() = kotlin.math.abs(month)
}

object LunarDateUtils {

    fun fromSolar(date: LocalDate): LunarYmd {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return LunarYmd(lunar.year, lunar.month, lunar.day)
    }

    fun toSolar(lunar: LunarYmd): LocalDate? = runCatching {
        val solar = Lunar.fromYmd(lunar.year, lunar.month, lunar.day).solar
        LocalDate.of(solar.year, solar.month, solar.day)
    }.getOrNull()

    fun dayInChinese(day: Int): String = runCatching {
        Lunar.fromYmd(2024, 1, day.coerceIn(1, 30)).dayInChinese
    }.getOrDefault(day.toString())

    fun monthInChinese(month: Int): String = runCatching {
        val m = if (month == 0) 1 else month
        Lunar.fromYmd(2024, m, 1).monthInChinese + "月"
    }.getOrDefault("${kotlin.math.abs(month)}月")

    /** 日历格子短文案：初一显示「正月」，其余显示「初五」等 */
    fun shortLabel(date: LocalDate): String {
        val lunar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth).lunar
        return if (lunar.day == 1) {
            val leap = if (lunar.month < 0) "闰" else ""
            leap + lunar.monthInChinese + "月"
        } else {
            lunar.dayInChinese
        }
    }

    fun format(lunar: LunarYmd): String {
        val leap = if (lunar.isLeap) "闰" else ""
        return "${lunar.year}年$leap${monthInChinese(lunar.monthAbs)}${dayInChinese(lunar.day)}"
    }

    fun lunarDayOf(date: LocalDate): Int = fromSolar(date).day

    fun lunarMonthOf(date: LocalDate): Int = fromSolar(date).month

    /** 是否与农历月日匹配（忽略年；闰月需 month 符号一致） */
    fun matchesLunarMonthDay(date: LocalDate, lunarMonth: Int, lunarDay: Int): Boolean {
        val l = fromSolar(date)
        return l.month == lunarMonth && l.day == lunarDay
    }

    fun matchesLunarDay(date: LocalDate, lunarDay: Int): Boolean =
        fromSolar(date).day == lunarDay

    /**
     * 下一次农历月日（每年）对应的公历日，on/after [from]。
     */
    fun nextLunarAnnual(
        lunarMonth: Int,
        lunarDay: Int,
        from: LocalDate
    ): LocalDate? {
        var year = fromSolar(from).year
        repeat(12) {
            val solar = toSolar(LunarYmd(year, lunarMonth, lunarDay))
            if (solar != null && !solar.isBefore(from)) return solar
            year++
        }
        return null
    }

    /**
     * 下一次「农历日」出现（每月），on/after [from]。
     * 若该农历月无此日则跳过。
     */
    fun nextLunarMonthly(lunarDays: List<Int>, from: LocalDate): LocalDate? {
        val days = lunarDays.filter { it in 1..30 }.toSet()
        if (days.isEmpty()) return null
        var cursor = from
        repeat(400) {
            if (fromSolar(cursor).day in days) return cursor
            cursor = cursor.plusDays(1)
        }
        return null
    }

    fun daysInLunarMonth(year: Int, month: Int): Int {
        for (d in 30 downTo 27) {
            if (toSolar(LunarYmd(year, month, d)) != null) return d
        }
        return 29
    }
}
