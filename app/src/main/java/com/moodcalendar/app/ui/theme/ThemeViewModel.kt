package com.moodcalendar.app.ui.theme

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.model.ThemeStyle
import com.moodcalendar.app.data.preferences.SettingsRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class ThemeViewModel(
    settingsRepository: SettingsRepository
) : ViewModel() {
    val themeStyle: StateFlow<ThemeStyle> = settingsRepository.themeStyle
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ThemeStyle.MALE_BLUE)

    companion object {
        fun factory(settingsRepository: SettingsRepository) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ThemeViewModel(settingsRepository) as T
            }
        }
    }
}
