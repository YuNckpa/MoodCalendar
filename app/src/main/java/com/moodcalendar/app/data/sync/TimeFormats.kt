package com.moodcalendar.app.data.sync

import java.time.Instant

object TimeFormats {
    fun millisToIso(ms: Long): String = Instant.ofEpochMilli(ms).toString()

    fun isoToMillis(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return runCatching { Instant.parse(iso).toEpochMilli() }.getOrDefault(0L)
    }
}
