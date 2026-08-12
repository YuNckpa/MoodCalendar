package com.moodcalendar.app.util

import com.nlf.calendar.Solar
import java.time.LocalDate

data class DayExtras(
    val lunarText: String,
    /** Short badge shown in calendar cell, e.g. 休 / 节 / 气 */
    val badge: String? = null,
    /** Full labels for day detail */
    val labels: List<String> = emptyList()
)

/**
 * Lunar day text, solar terms, Chinese legal holidays and common international festivals.
 */
object LunarHolidayProvider {

    private val internationalFestivals = mapOf(
        "01-01" to "元旦",
        "02-14" to "情人节",
        "03-08" to "妇女节",
        "03-12" to "植树节",
        "04-01" to "愚人节",
        "05-01" to "劳动节",
        "05-04" to "青年节",
        "06-01" to "儿童节",
        "10-01" to "国庆节",
        "10-31" to "万圣节",
        "11-11" to "光棍节",
        "12-24" to "平安夜",
        "12-25" to "圣诞节"
    )

    /** Approximate statutory holiday dates (ISO) for recent years; rest days marked as 休 */
    private val statutoryHolidays: Set<String> = buildSet {
        // 2024
        addAll(range("2024-01-01", "2024-01-01"))
        addAll(range("2024-02-10", "2024-02-17"))
        addAll(range("2024-04-04", "2024-04-06"))
        addAll(range("2024-05-01", "2024-05-05"))
        addAll(range("2024-06-08", "2024-06-10"))
        addAll(range("2024-09-15", "2024-09-17"))
        addAll(range("2024-10-01", "2024-10-07"))
        // 2025
        addAll(range("2025-01-01", "2025-01-01"))
        addAll(range("2025-01-28", "2025-02-04"))
        addAll(range("2025-04-04", "2025-04-06"))
        addAll(range("2025-05-01", "2025-05-05"))
        addAll(range("2025-05-31", "2025-06-02"))
        addAll(range("2025-10-01", "2025-10-08"))
        addAll(range("2025-10-06", "2025-10-08")) // 中秋并入国庆附近
        // 2026
        addAll(range("2026-01-01", "2026-01-03"))
        addAll(range("2026-02-15", "2026-02-23"))
        addAll(range("2026-04-04", "2026-04-06"))
        addAll(range("2026-05-01", "2026-05-05"))
        addAll(range("2026-06-19", "2026-06-21"))
        addAll(range("2026-09-25", "2026-09-27"))
        addAll(range("2026-10-01", "2026-10-07"))
        // 2027
        addAll(range("2027-01-01", "2027-01-03"))
        addAll(range("2027-02-06", "2027-02-13"))
        addAll(range("2027-04-03", "2027-04-05"))
        addAll(range("2027-05-01", "2027-05-05"))
        addAll(range("2027-06-09", "2027-06-11"))
        addAll(range("2027-09-15", "2027-09-17"))
        addAll(range("2027-10-01", "2027-10-07"))
    }

    private fun range(start: String, end: String): List<String> {
        val s = LocalDate.parse(start)
        val e = LocalDate.parse(end)
        val out = mutableListOf<String>()
        var c = s
        while (!c.isAfter(e)) {
            out += c.toIso()
            c = c.plusDays(1)
        }
        return out
    }

    fun extras(date: LocalDate): DayExtras {
        val solar = Solar.fromYmd(date.year, date.monthValue, date.dayOfMonth)
        val lunar = solar.lunar
        val lunarText = if (lunar.day == 1) {
            lunar.monthInChinese + "月"
        } else {
            lunar.dayInChinese
        }

        val labels = mutableListOf<String>()
        var badge: String? = null

        val jieQi = lunar.jieQi
        if (!jieQi.isNullOrBlank()) {
            labels += jieQi
            badge = badge ?: "气"
        }

        val festivals = lunar.festivals.orEmpty() + solar.festivals.orEmpty()
        festivals.forEach { name ->
            if (name.isNotBlank() && name !in labels) labels += name
        }

        val md = date.toMonthDay()
        internationalFestivals[md]?.let { name ->
            if (name !in labels) labels += name
            if (badge == null) badge = "节"
        }

        val iso = date.toIso()
        if (iso in statutoryHolidays) {
            if ("法定节假日" !in labels) labels += "法定节假日"
            badge = "休"
        }

        // Prefer showing festival/jieqi name in lunar slot when short
        val displayLunar = when {
            labels.isNotEmpty() && (labels.first().length <= 3) -> labels.first()
            else -> lunarText
        }

        return DayExtras(
            lunarText = displayLunar,
            badge = badge,
            labels = labels
        )
    }
}
