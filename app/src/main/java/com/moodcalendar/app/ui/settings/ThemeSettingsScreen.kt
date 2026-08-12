package com.moodcalendar.app.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcalendar.app.data.model.ThemeStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val theme by viewModel.themeStyle.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("主题风格") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("选择主题", style = MaterialTheme.typography.titleMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = theme == ThemeStyle.MALE_BLUE,
                    onClick = { viewModel.setTheme(ThemeStyle.MALE_BLUE) },
                    label = { Text("男生 · 蓝色") }
                )
                FilterChip(
                    selected = theme == ThemeStyle.FEMALE_PINK,
                    onClick = { viewModel.setTheme(ThemeStyle.FEMALE_PINK) },
                    label = { Text("女生 · 粉色") }
                )
            }
            Text(
                "切换后立即生效，并会保存到本地。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
