package com.moodcalendar.app.ui.mood

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.model.BuiltInMoods
import com.moodcalendar.app.data.model.MoodPreset
import com.moodcalendar.app.data.preferences.SettingsRepository
import com.moodcalendar.app.data.repository.MoodEntry
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.util.DateFormats
import com.moodcalendar.app.util.toLocalDateOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class MoodJournalItem(
    val id: Long,
    val date: String,
    val dateLabel: String,
    val timeLabel: String,
    val emoji: String,
    val emojiLabel: String,
    val text: String,
    val isPeriod: Boolean,
    val coverImageUri: String? = null
)

data class MoodJournalUiState(
    val items: List<MoodJournalItem> = emptyList(),
    val filterOptions: List<MoodPreset> = BuiltInMoods.presets,
    val selectedEmojis: Set<String> = emptySet(),
    val filterAll: Boolean = true
)

class MoodJournalViewModel(
    moodRepository: MoodRepository,
    settingsRepository: SettingsRepository
) : ViewModel() {
    private val selectedEmojis = MutableStateFlow<Set<String>>(emptySet())
    private val filterAll = MutableStateFlow(true)

    val uiState: StateFlow<MoodJournalUiState> = combine(
        moodRepository.observeAllWithImages(),
        settingsRepository.customMoods,
        selectedEmojis,
        filterAll
    ) { entries, customs, selected, all ->
        val usedCustom = entries
            .map { MoodPreset(it.emoji, it.emojiLabel.ifBlank { BuiltInMoods.labelFor(it.emoji) }, true) }
            .filter { preset ->
                BuiltInMoods.presets.none { it.emoji == preset.emoji } && preset.emoji.isNotBlank()
            }
            .distinctBy { it.emoji }
        val options = BuiltInMoods.mergeWithCustoms(
            (customs + usedCustom).distinctBy { it.emoji }
        )
        val items = entries
            .filter { all || it.emoji in selected }
            .map { it.toItem(customs) }
        MoodJournalUiState(
            items = items,
            filterOptions = options,
            selectedEmojis = selected,
            filterAll = all
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoodJournalUiState())

    fun selectAll() {
        filterAll.value = true
        selectedEmojis.value = emptySet()
    }

    fun toggleEmoji(emoji: String) {
        filterAll.value = false
        selectedEmojis.update { current ->
            val next = current.toMutableSet()
            if (emoji in next) next.remove(emoji) else next.add(emoji)
            if (next.isEmpty()) {
                filterAll.value = true
            }
            next
        }
    }

    companion object {
        fun factory(
            repo: MoodRepository,
            settingsRepository: SettingsRepository
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MoodJournalViewModel(repo, settingsRepository) as T
            }
        }

        private fun MoodEntry.toItem(customs: List<MoodPreset>): MoodJournalItem {
            val label = date.toLocalDateOrNull()?.format(DateFormats.DISPLAY) ?: date
            val time = Instant.ofEpochMilli(createdAt)
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime()
                .format(DateTimeFormatter.ofPattern("HH:mm"))
            val meaning = emojiLabel.ifBlank { BuiltInMoods.labelFor(emoji, customs) }
            return MoodJournalItem(
                id = id,
                date = date,
                dateLabel = label,
                timeLabel = time,
                emoji = emoji,
                emojiLabel = meaning,
                text = text,
                isPeriod = isPeriod,
                coverImageUri = images.minByOrNull { it.sortOrder }?.localUri
            )
        }
    }
}
