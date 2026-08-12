package com.moodcalendar.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moodcalendar.app.data.model.MoodPreset
import com.moodcalendar.app.data.model.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_style")
    private val customMoodsKey = stringPreferencesKey("custom_moods_json")

    val themeStyle: Flow<ThemeStyle> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeStyle.valueOf(it) }.getOrNull() }
            ?: ThemeStyle.MALE_BLUE
    }

    val customMoods: Flow<List<MoodPreset>> = context.dataStore.data.map { prefs ->
        decodeCustomMoods(prefs[customMoodsKey].orEmpty())
    }

    suspend fun setThemeStyle(style: ThemeStyle) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = style.name
        }
    }

    suspend fun upsertCustomMood(preset: MoodPreset) {
        context.dataStore.edit { prefs ->
            val current = decodeCustomMoods(prefs[customMoodsKey].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.emoji == preset.emoji }
            val item = preset.copy(isCustom = true)
            if (idx >= 0) current[idx] = item else current += item
            prefs[customMoodsKey] = encodeCustomMoods(current)
        }
    }

    suspend fun removeCustomMood(emoji: String) {
        context.dataStore.edit { prefs ->
            val current = decodeCustomMoods(prefs[customMoodsKey].orEmpty())
                .filterNot { it.emoji == emoji }
            prefs[customMoodsKey] = encodeCustomMoods(current)
        }
    }

    private fun encodeCustomMoods(list: List<MoodPreset>): String {
        val arr = JSONArray()
        list.forEach {
            arr.put(
                JSONObject()
                    .put("emoji", it.emoji)
                    .put("label", it.label)
            )
        }
        return arr.toString()
    }

    private fun decodeCustomMoods(json: String): List<MoodPreset> {
        if (json.isBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(json)
            buildList {
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    val emoji = obj.optString("emoji")
                    val label = obj.optString("label")
                    if (emoji.isNotBlank() && label.isNotBlank()) {
                        add(MoodPreset(emoji = emoji, label = label, isCustom = true))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }
}
