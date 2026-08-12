package com.moodcalendar.app.data.local

import org.json.JSONArray

object IntListJson {
    fun encode(list: List<Int>): String {
        val arr = JSONArray()
        list.distinct().sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(json: String): List<Int> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getInt(i))
                }
            }.distinct().sorted()
        }.getOrDefault(emptyList())
    }
}

object StringListJson {
    fun encode(list: List<String>): String {
        val arr = JSONArray()
        list.distinct().sorted().forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(json: String): List<String> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    add(arr.getString(i))
                }
            }.distinct().sorted()
        }.getOrDefault(emptyList())
    }
}
