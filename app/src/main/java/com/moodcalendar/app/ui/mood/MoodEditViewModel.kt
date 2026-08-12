package com.moodcalendar.app.ui.mood

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.model.BuiltInMoods
import com.moodcalendar.app.data.model.MoodPreset
import com.moodcalendar.app.data.model.MoodVisibility
import com.moodcalendar.app.data.preferences.SettingsRepository
import com.moodcalendar.app.data.repository.MoodEntry
import com.moodcalendar.app.data.repository.MoodImage
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.util.ImageStore
import com.moodcalendar.app.util.toIso
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

data class MoodEditUiState(
    val id: Long = 0,
    val date: String = LocalDate.now().toIso(),
    val text: String = "",
    val emoji: String = BuiltInMoods.presets.first().emoji,
    val emojiLabel: String = BuiltInMoods.presets.first().label,
    val visibility: MoodVisibility = MoodVisibility.FRIENDS_ALL,
    val isPeriod: Boolean = false,
    val imageUris: List<String> = emptyList(),
    val moodOptions: List<MoodPreset> = BuiltInMoods.presets,
    val createdAt: Long = System.currentTimeMillis(),
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val deleted: Boolean = false,
    val error: String? = null
)

class MoodEditViewModel(
    application: Application,
    private val moodRepository: MoodRepository,
    private val settingsRepository: SettingsRepository,
    private val moodId: Long?,
    date: String
) : AndroidViewModel(application) {
    private val base = MutableStateFlow(
        MoodEditUiState(date = date.ifBlank { LocalDate.now().toIso() })
    )

    val uiState: StateFlow<MoodEditUiState> = combine(
        base,
        settingsRepository.customMoods
    ) { state, customs ->
        state.copy(moodOptions = BuiltInMoods.mergeWithCustoms(customs))
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        MoodEditUiState(date = date.ifBlank { LocalDate.now().toIso() })
    )

    init {
        if (moodId != null) {
            viewModelScope.launch {
                val existing = moodRepository.getById(moodId) ?: return@launch
                base.value = MoodEditUiState(
                    id = existing.id,
                    date = existing.date,
                    text = existing.text,
                    emoji = existing.emoji,
                    emojiLabel = existing.emojiLabel.ifBlank {
                        BuiltInMoods.labelFor(existing.emoji)
                    },
                    visibility = existing.visibility,
                    isPeriod = existing.isPeriod,
                    imageUris = existing.images.map { it.localUri },
                    createdAt = existing.createdAt
                )
            }
        }
    }

    fun update(transform: (MoodEditUiState) -> MoodEditUiState) {
        base.update(transform)
    }

    fun selectMood(preset: MoodPreset) {
        base.update { it.copy(emoji = preset.emoji, emojiLabel = preset.label, error = null) }
    }

    fun addCustomMood(emoji: String, label: String) {
        val e = emoji.trim()
        val l = label.trim()
        if (e.isBlank() || l.isBlank()) {
            base.update { it.copy(error = "请填写自定义表情和含义") }
            return
        }
        viewModelScope.launch {
            settingsRepository.upsertCustomMood(MoodPreset(e, l, isCustom = true))
            base.update { it.copy(emoji = e, emojiLabel = l, error = null) }
        }
    }

    fun addImages(uris: List<String>) {
        base.update {
            val merged = (it.imageUris + uris).distinct().take(9)
            it.copy(imageUris = merged)
        }
    }

    fun removeImage(uri: String) {
        base.update { it.copy(imageUris = it.imageUris - uri) }
    }

    fun save() {
        val state = base.value
        if (state.emoji.isBlank()) {
            base.update { it.copy(error = "请选择心情") }
            return
        }
        viewModelScope.launch {
            base.update { it.copy(isSaving = true) }
            val persisted = withContext(Dispatchers.IO) {
                ImageStore.persistUris(getApplication(), state.imageUris)
            }
            moodRepository.save(
                MoodEntry(
                    id = state.id,
                    date = state.date,
                    text = state.text.trim(),
                    emoji = state.emoji,
                    emojiLabel = state.emojiLabel.trim().ifBlank {
                        BuiltInMoods.labelFor(state.emoji)
                    },
                    visibility = state.visibility,
                    isPeriod = state.isPeriod,
                    images = persisted.mapIndexed { index, uri ->
                        MoodImage(localUri = uri, sortOrder = index)
                    },
                    createdAt = state.createdAt
                )
            )
            base.update { it.copy(isSaving = false, saved = true) }
        }
    }

    fun delete() {
        val id = base.value.id
        if (id == 0L) {
            base.update { it.copy(deleted = true) }
            return
        }
        viewModelScope.launch {
            moodRepository.delete(id)
            base.update { it.copy(deleted = true) }
        }
    }

    companion object {
        fun factory(
            application: Application,
            repo: MoodRepository,
            settingsRepository: SettingsRepository,
            moodId: Long?,
            date: String
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return MoodEditViewModel(application, repo, settingsRepository, moodId, date) as T
            }
        }
    }
}
