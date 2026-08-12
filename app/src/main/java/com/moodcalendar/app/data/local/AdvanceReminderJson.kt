package com.moodcalendar.app.data.local

import com.moodcalendar.app.data.model.AdvanceReminder
import com.moodcalendar.app.data.model.AdvanceUnit
import org.json.JSONArray
import org.json.JSONObject

object AdvanceReminderJson {
    fun encode(list: List<AdvanceReminder>): String {
        val arr = JSONArray()
        list.forEach { item ->
            arr.put(
                JSONObject()
                    .put("amount", item.amount)
                    .put("unit", item.unit.name)
            )
        }
        return arr.toString()
    }

    fun decode(json: String): List<AdvanceReminder> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    add(
                        AdvanceReminder(
                            amount = obj.getInt("amount"),
                            unit = AdvanceUnit.valueOf(obj.getString("unit"))
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}
