package com.moodcalendar.app.ui.mood

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.People
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcalendar.app.ui.components.SoftCoverBackground

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodJournalScreen(
    viewModel: MoodJournalViewModel,
    onOpenMood: (moodId: Long, date: String) -> Unit,
    onOpenSocial: () -> Unit = {}
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("感情记录") },
                actions = {
                    IconButton(onClick = onOpenSocial) {
                        Icon(Icons.Outlined.People, contentDescription = "好友动态")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Text(
                "按心情筛选（可多选）",
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FilterChip(
                    selected = state.filterAll,
                    onClick = viewModel::selectAll,
                    label = { Text("全部心情") }
                )
                state.filterOptions.forEach { preset ->
                    FilterChip(
                        selected = !state.filterAll && preset.emoji in state.selectedEmojis,
                        onClick = { viewModel.toggleEmoji(preset.emoji) },
                        label = {
                            Text("${preset.emoji} ${preset.label}")
                        }
                    )
                }
            }

            if (state.items.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("还没有感情记录", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        "在日历里点开某天，写下心情吧",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(state.items, key = { it.id }) { item ->
                        val hasBg = !item.coverImageUri.isNullOrBlank()
                        val contentColor =
                            if (hasBg) Color.White else MaterialTheme.colorScheme.onSurface
                        val subColor =
                            if (hasBg) Color.White.copy(0.9f)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 88.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .clickable { onOpenMood(item.id, item.date) }
                        ) {
                            if (hasBg) {
                                SoftCoverBackground(
                                    item.coverImageUri.orEmpty(),
                                    Modifier.fillMaxSize(),
                                    0.3f
                                )
                            } else {
                                Box(
                                    Modifier
                                        .fillMaxSize()
                                        .background(
                                            MaterialTheme.colorScheme.surfaceVariant.copy(0.35f)
                                        )
                                )
                            }
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier.width(56.dp)
                                ) {
                                    Text(item.emoji, fontSize = 28.sp, textAlign = TextAlign.Center)
                                    if (item.emojiLabel.isNotBlank()) {
                                        Text(
                                            item.emojiLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            textAlign = TextAlign.Center,
                                            color = contentColor
                                        )
                                    }
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "${item.dateLabel}  ${item.timeLabel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = if (hasBg) Color.White
                                        else MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        item.text.ifBlank { "（无文字）" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = subColor,
                                        maxLines = 2
                                    )
                                    if (item.isPeriod) {
                                        Text(
                                            "月经期间",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (hasBg) Color(0xFFFF8A80)
                                            else MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
