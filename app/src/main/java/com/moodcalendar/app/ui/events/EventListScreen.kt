package com.moodcalendar.app.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.ui.components.SoftCoverBackground

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventListScreen(
    viewModel: EventListViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit
) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { TopAppBar(title = { Text("事件") }) },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Filled.Add, contentDescription = "添加")
            }
        }
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("还没有事件", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("点右下角添加一条", style = MaterialTheme.typography.bodyMedium)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                itemsIndexed(items, key = { _, item -> item.entity.id }) { index, item ->
                    val showTypeHeader = shouldShowTypeHeader(items, index)
                    if (showTypeHeader) {
                        Text(
                            typeLabel(item.entity.type),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = if (index == 0) 0.dp else 8.dp, bottom = 2.dp)
                        )
                    }
                    EventListCard(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == items.lastIndex,
                        onOpen = { onEdit(item.entity.id) },
                        onDelete = { viewModel.delete(item.entity.id) },
                        onMoveUp = { viewModel.moveUp(item.entity.id) },
                        onMoveDown = { viewModel.moveDown(item.entity.id) }
                    )
                }
            }
        }
    }
}

private fun shouldShowTypeHeader(items: List<EventListItem>, index: Int): Boolean {
    val item = items[index]
    if (item.isToday || item.section != EventListSection.BY_TYPE) return false
    if (index == 0) return true
    val prev = items[index - 1]
    return prev.section != EventListSection.BY_TYPE || prev.entity.type != item.entity.type
}

@Composable
private fun EventListCard(
    item: EventListItem,
    isFirst: Boolean,
    isLast: Boolean,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val hasBg = !item.entity.backgroundImageUri.isNullOrBlank()
    val highlight = item.isToday
    val contentColor = when {
        hasBg -> Color.White
        highlight -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val subColor = when {
        hasBg -> Color.White.copy(0.9f)
        highlight -> MaterialTheme.colorScheme.onPrimaryContainer.copy(0.85f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val shape = RoundedCornerShape(14.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 96.dp)
            .clip(shape)
            .then(
                if (highlight && !hasBg) {
                    Modifier
                        .background(MaterialTheme.colorScheme.primaryContainer)
                        .border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else if (highlight) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onOpen)
    ) {
        if (hasBg) {
            SoftCoverBackground(
                uri = item.entity.backgroundImageUri!!,
                modifier = Modifier.matchParentSize(),
                scrimAlpha = if (highlight) 0.22f else 0.3f
            )
        } else if (!highlight) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(0.35f))
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                if (item.isToday) {
                    Text(
                        "今日事件",
                        style = MaterialTheme.typography.labelLarge,
                        color = if (hasBg) Color.White else MaterialTheme.colorScheme.primary
                    )
                }
                Text(
                    item.entity.title,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
                Text(
                    "${typeLabel(item.entity.type)} · ${item.subtitle}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = subColor
                )
                Text(
                    "提醒 ${item.entity.remindTime}" +
                        if (item.entity.remindOnDay) "（当天）" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (hasBg) Color.White.copy(0.85f)
                    else MaterialTheme.colorScheme.primary
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                IconButton(onClick = onMoveUp, enabled = !isFirst) {
                    Icon(
                        Icons.Outlined.KeyboardArrowUp,
                        contentDescription = "上移",
                        tint = contentColor.copy(alpha = if (isFirst) 0.35f else 1f)
                    )
                }
                IconButton(onClick = onMoveDown, enabled = !isLast) {
                    Icon(
                        Icons.Outlined.KeyboardArrowDown,
                        contentDescription = "下移",
                        tint = contentColor.copy(alpha = if (isLast) 0.35f else 1f)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "删除",
                    tint = contentColor
                )
            }
        }
    }
}

private fun typeLabel(type: EventType): String = when (type) {
    EventType.ANNIVERSARY -> "纪念日"
    EventType.COUNTDOWN -> "倒数日"
    EventType.BIRTHDAY -> "生日"
    EventType.CUSTOM -> "自定义"
}
