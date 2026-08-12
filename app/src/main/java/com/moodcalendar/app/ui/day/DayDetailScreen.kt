package com.moodcalendar.app.ui.day

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.util.DateFormats
import com.moodcalendar.app.util.toLocalDateOrNull

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayDetailScreen(
    viewModel: DayDetailViewModel,
    onBack: () -> Unit,
    onAddMood: () -> Unit,
    onEditMood: (Long) -> Unit,
    onAddEvent: () -> Unit,
    onEditEvent: (Long) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val title = state.date.toLocalDateOrNull()
        ?.format(DateFormats.DISPLAY)
        ?: state.date

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                if (state.holidayLabels.isNotEmpty()) {
                    Text(
                        state.holidayLabels.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(8.dp))
                }
                SectionHeader(
                    title = "当天事件",
                    actionLabel = "添加",
                    onAction = onAddEvent
                )
            }
            if (state.events.isEmpty()) {
                item {
                    Text(
                        "暂无事件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(state.events, key = { it.id }) { event ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditEvent(event.id) }
                            .padding(12.dp)
                    ) {
                        Text(event.title, style = MaterialTheme.typography.titleMedium)
                        Text(
                            typeLabel(event.type),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (event.note.isNotBlank()) {
                            Text(event.note, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                    HorizontalDivider()
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "情感记录",
                    actionLabel = "写心情",
                    onAction = onAddMood
                )
            }
            if (state.moods.isEmpty()) {
                item {
                    Text(
                        "还没有记录，写下今天的心情吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                items(state.moods, key = { it.id }) { mood ->
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onEditMood(mood.id) }
                            .padding(12.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(mood.emoji, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.size(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    mood.text.ifBlank { "（无文字）" },
                                    style = MaterialTheme.typography.bodyLarge
                                )
                                if (mood.isPeriod) {
                                    Text(
                                        "月经期间",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.error
                                    )
                                }
                            }
                        }
                        if (mood.images.isNotEmpty()) {
                            Spacer(Modifier.height(8.dp))
                            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(mood.images) { img ->
                                    AsyncImage(
                                        model = img.localUri,
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(72.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                }
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge)
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(actionLabel)
        }
    }
}

private fun typeLabel(type: EventType): String = when (type) {
    EventType.ANNIVERSARY -> "纪念日"
    EventType.COUNTDOWN -> "倒数日"
    EventType.BIRTHDAY -> "生日"
    EventType.CUSTOM -> "自定义"
}
