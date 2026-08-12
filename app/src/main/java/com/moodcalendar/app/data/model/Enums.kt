package com.moodcalendar.app.data.model

enum class EventType {
    ANNIVERSARY,
    COUNTDOWN,
    BIRTHDAY,
    CUSTOM
}

enum class Recurrence {
    NONE,
    DAILY,
    WEEKLY,
    MONTHLY,
    YEARLY
}

enum class DisplayMode {
    /** 仅按天数计算 */
    DAYS_ONLY,
    /** 年份 + 余天数 */
    YEARS_AND_DAYS
}

enum class YearlyMode {
    /** 每年同一天（相对起始/目标日期） */
    SAME_DAY,
    /** 每年指定的若干月-日 */
    CUSTOM_DATES
}

enum class AdvanceUnit {
    MINUTES,
    HOURS,
    DAYS
}

data class AdvanceReminder(
    val amount: Int,
    val unit: AdvanceUnit
)

enum class MoodVisibility {
    PRIVATE,
    FRIENDS_ALL,
    GROUP
}

enum class ThemeStyle {
    MALE_BLUE,
    FEMALE_PINK
}

/** 事件日期历法：公历（阳历）/ 农历（阴历） */
enum class CalendarSystem {
    SOLAR,
    LUNAR
}
