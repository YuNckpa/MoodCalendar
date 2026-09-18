package com.moodcalendar.app.data.preferences

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.moodcalendar.app.data.model.MoodPreset
import com.moodcalendar.app.data.model.ThemeStyle
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "app_settings")

data class SettingsSnapshot(
    val themeStyle: ThemeStyle,
    val customMoodsJson: String,
    val updatedAt: Long
)

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme_style")
    private val customMoodsKey = stringPreferencesKey("custom_moods_json")
    private val updatedAtKey = longPreferencesKey("settings_updated_at")

    private var onLocalChanged: (suspend () -> Unit)? = null

    fun setOnLocalChanged(listener: (suspend () -> Unit)?) {
        onLocalChanged = listener
    }

    val themeStyle: Flow<ThemeStyle> = context.dataStore.data.map { prefs ->
        prefs[themeKey]?.let { runCatching { ThemeStyle.valueOf(it) }.getOrNull() }
            ?: ThemeStyle.MALE_BLUE
    }

    val customMoods: Flow<List<MoodPreset>> = context.dataStore.data.map { prefs ->
        decodeCustomMoods(prefs[customMoodsKey].orEmpty())
    }

    val updatedAt: Flow<Long> = context.dataStore.data.map { prefs ->
        prefs[updatedAtKey] ?: 0L
    }

    suspend fun snapshot(): SettingsSnapshot {
        val prefs = context.dataStore.data.first()
        return SettingsSnapshot(
            themeStyle = prefs[themeKey]?.let { runCatching { ThemeStyle.valueOf(it) }.getOrNull() }
                ?: ThemeStyle.MALE_BLUE,
            customMoodsJson = prefs[customMoodsKey] ?: "[]",
            updatedAt = prefs[updatedAtKey] ?: 0L
        )
    }

    suspend fun setThemeStyle(style: ThemeStyle, notifySync: Boolean = true) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = style.name
            prefs[updatedAtKey] = System.currentTimeMillis()
        }
        if (notifySync) runCatching { onLocalChanged?.invoke() }
    }

    suspend fun upsertCustomMood(preset: MoodPreset, notifySync: Boolean = true) {
        context.dataStore.edit { prefs ->
            val current = decodeCustomMoods(prefs[customMoodsKey].orEmpty()).toMutableList()
            val idx = current.indexOfFirst { it.emoji == preset.emoji }
            val item = preset.copy(isCustom = true)
            if (idx >= 0) current[idx] = item else current += item
            prefs[customMoodsKey] = encodeCustomMoods(current)
            prefs[updatedAtKey] = System.currentTimeMillis()
        }
        if (notifySync) runCatching { onLocalChanged?.invoke() }
    }

    suspend fun removeCustomMood(emoji: String, notifySync: Boolean = true) {
        context.dataStore.edit { prefs ->
            val current = decodeCustomMoods(prefs[customMoodsKey].orEmpty())
                .filterNot { it.emoji == emoji }
            prefs[customMoodsKey] = encodeCustomMoods(current)
            prefs[updatedAtKey] = System.currentTimeMillis()
        }
        if (notifySync) runCatching { onLocalChanged?.invoke() }
    }

    suspend fun applyFromCloud(themeStyle: ThemeStyle, customMoodsJson: String, updatedAt: Long) {
        context.dataStore.edit { prefs ->
            prefs[themeKey] = themeStyle.name
            prefs[customMoodsKey] = customMoodsJson
            prefs[updatedAtKey] = updatedAt
        }
    }

    fun encodeCustomMoods(list: List<MoodPreset>): String {
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

    fun decodeCustomMoods(json: String): List<MoodPreset> {
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
