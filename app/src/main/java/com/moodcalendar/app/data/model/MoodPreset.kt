package com.moodcalendar.app.data.model

data class MoodPreset(
    val emoji: String,
    val label: String,
    val isCustom: Boolean = false
)

object BuiltInMoods {
    val presets: List<MoodPreset> = listOf(
        MoodPreset("😊", "开心"),
        MoodPreset("🥺", "想念"),
        MoodPreset("💕", "爱情"),
        MoodPreset("😢", "难过"),
        MoodPreset("😡", "愤怒"),
        MoodPreset("😐", "平淡"),
        MoodPreset("😤", "吵架")
    )

    fun labelFor(emoji: String, customs: List<MoodPreset> = emptyList()): String {
        customs.firstOrNull { it.emoji == emoji }?.label?.let { return it }
        presets.firstOrNull { it.emoji == emoji }?.label?.let { return it }
        return ""
    }

    fun mergeWithCustoms(customs: List<MoodPreset>): List<MoodPreset> {
        val customEmojis = customs.map { it.emoji }.toSet()
        return presets.filter { it.emoji !in customEmojis } + customs
    }
}
