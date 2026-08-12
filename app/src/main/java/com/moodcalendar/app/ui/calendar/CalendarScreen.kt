package com.moodcalendar.app.ui.calendar

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodcalendar.app.R
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.repository.MoodEntry
import com.moodcalendar.app.ui.components.SoftCoverBackground
import com.moodcalendar.app.util.calendarDays
import com.moodcalendar.app.util.toIso
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: CalendarViewModel,
    onAddEvent: (dateIso: String) -> Unit,
    onEditEvent: (Long) -> Unit,
    onAddMood: (dateIso: String) -> Unit,
    onEditMood: (moodId: Long, dateIso: String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    var yearPickerOpen by remember { mutableStateOf(false) }
    var monthMenu by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name)) },
                actions = {
                    TextButton(onClick = viewModel::goToToday) {
                        Icon(
                            Icons.Outlined.Today,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("今日")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddEvent(state.selectedDate.toIso()) }) {
                Icon(Icons.Filled.Add, contentDescription = "添加事件")
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = viewModel::previousMonth) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, contentDescription = "上个月")
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = { yearPickerOpen = true }) {
                            Text(
                                text = state.month.year.toString() + "年",
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Box {
                            TextButton(onClick = { monthMenu = true }) {
                                Text(
                                    text = state.month.monthValue.toString() + "月",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                            DropdownMenu(expanded = monthMenu, onDismissRequest = { monthMenu = false }) {
                                (1..12).forEach { m ->
                                    val selected = m == state.month.monthValue
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = m.toString() + "月",
                                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                                                color = if (selected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        },
                                        onClick = {
                                            viewModel.setMonthNumber(m)
                                            monthMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    IconButton(onClick = viewModel::nextMonth) {
                        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = "下个月")
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    weekLabels.forEachIndexed { index, label ->
                        val weekend = index >= 5
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (weekend) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))

                val days = state.month.calendarDays()
                days.chunked(7).forEach { week ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        week.forEach { date ->
                            DayCell(
                                date = date,
                                selected = date == state.selectedDate,
                                isToday = date == LocalDate.now(),
                                markers = date?.let { state.markers[it] },
                                onClick = {
                                    if (date != null) viewModel.selectDate(date)
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                if (!state.isCurrentMonth) {
                    TextButton(
                        onClick = viewModel::goToToday,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp)
                    ) {
                        Icon(Icons.Outlined.Today, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("回到今日")
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    LegendDot(color = MaterialTheme.colorScheme.primary, label = "事件")
                    LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "心情")
                    LegendDot(color = MaterialTheme.colorScheme.error, label = "经期")
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                Text(
                    state.selectedDateLabel,
                    style = MaterialTheme.typography.titleLarge
                )
                if (state.holidayLabels.isNotEmpty()) {
                    Text(
                        state.holidayLabels.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                SectionHeader(
                    title = "当天事件",
                    actionLabel = "添加",
                    onAction = { onAddEvent(state.selectedDate.toIso()) }
                )
            }
            if (state.selectedEvents.isEmpty()) {
                item {
                    Text(
                        "暂无事件",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                items(state.selectedEvents, key = { "e-${it.id}" }) { event ->
                    EventInlineCard(
                        title = event.title,
                        typeLabel = typeLabel(event.type),
                        note = event.note,
                        backgroundUri = event.backgroundImageUri,
                        onClick = { onEditEvent(event.id) }
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                SectionHeader(
                    title = "情感记录",
                    actionLabel = "写心情",
                    onAction = { onAddMood(state.selectedDate.toIso()) }
                )
            }
            if (state.selectedMoods.isEmpty()) {
                item {
                    Text(
                        "还没有记录，写下今天的心情吧",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }
            } else {
                items(state.selectedMoods, key = { "m-${it.id}" }) { mood ->
                    MoodInlineCard(
                        mood = mood,
                        onClick = { onEditMood(mood.id, mood.date) }
                    )
                }
            }
            item { Spacer(Modifier.height(88.dp)) }
        }
    }

    if (yearPickerOpen) {
        YearPickerDialog(
            years = state.yearOptions,
            selectedYear = state.month.year,
            onSelect = {
                viewModel.setYear(it)
                yearPickerOpen = false
            },
            onDismiss = { yearPickerOpen = false }
        )
    }
}

@Composable
private fun SectionHeader(title: String, actionLabel: String, onAction: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        TextButton(onClick = onAction) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(actionLabel)
        }
    }
}

@Composable
private fun EventInlineCard(
    title: String,
    typeLabel: String,
    note: String,
    backgroundUri: String?,
    onClick: () -> Unit
) {
    val hasBg = !backgroundUri.isNullOrBlank()
    val contentColor = if (hasBg) Color.White else MaterialTheme.colorScheme.onSurface
    val secondaryColor =
        if (hasBg) Color.White.copy(alpha = 0.9f) else MaterialTheme.colorScheme.primary
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (hasBg) {
            SoftCoverBackground(backgroundUri.orEmpty(), Modifier.fillMaxSize(), 0.3f)
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            )
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, color = contentColor)
            Text(text = typeLabel, style = MaterialTheme.typography.bodySmall, color = secondaryColor)
            if (note.isNotBlank()) {
                Text(text = note, style = MaterialTheme.typography.bodyMedium, color = contentColor)
            }
        }
    }
}

@Composable
private fun MoodInlineCard(mood: MoodEntry, onClick: () -> Unit) {
    val timeText = remember(mood.createdAt) {
        Instant.ofEpochMilli(mood.createdAt)
            .atZone(ZoneId.systemDefault())
            .toLocalDateTime()
            .format(DateTimeFormatter.ofPattern("HH:mm"))
    }
    val coverUri = mood.images.minByOrNull { it.sortOrder }?.localUri
    val hasBg = !coverUri.isNullOrBlank()
    val contentColor = if (hasBg) Color.White else MaterialTheme.colorScheme.onSurface
    val primaryColor =
        if (hasBg) Color.White else MaterialTheme.colorScheme.primary

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .heightIn(min = 88.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
    ) {
        if (hasBg) {
            SoftCoverBackground(coverUri.orEmpty(), Modifier.fillMaxSize(), 0.3f)
        }
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                timeText,
                style = MaterialTheme.typography.labelMedium,
                color = primaryColor
            )
            Spacer(Modifier.height(4.dp))
            Text(mood.emoji, fontSize = 28.sp)
            if (mood.emojiLabel.isNotBlank()) {
                Text(
                    mood.emojiLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                mood.text.ifBlank { "（无文字）" },
                style = MaterialTheme.typography.bodyLarge,
                color = contentColor
            )
            if (mood.isPeriod) {
                Text(
                    "月经期间",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (hasBg) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error
                )
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
    }
}

@Composable
private fun YearPickerDialog(
    years: List<Int>,
    selectedYear: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val selectedIndex = years.indexOf(selectedYear).coerceAtLeast(0)
    val listState = rememberLazyListState()
    LaunchedEffect(selectedYear, years) {
        val target = (selectedIndex - 2).coerceAtLeast(0)
        listState.scrollToItem(target)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择年份") },
        text = {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp)
            ) {
                items(years, key = { it }) { year ->
                    val selected = year == selectedYear
                    Text(
                        text = year.toString() + "年",
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(
                                if (selected) MaterialTheme.colorScheme.primaryContainer
                                else Color.Transparent
                            )
                            .clickable { onSelect(year) }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                        color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun DayCell(
    date: LocalDate?,
    selected: Boolean,
    isToday: Boolean,
    markers: DayMarkers?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val weekend = date?.dayOfWeek == DayOfWeek.SATURDAY || date?.dayOfWeek == DayOfWeek.SUNDAY
    val hasCover = !markers?.coverImageUri.isNullOrBlank()
    val dayColor = when {
        hasCover -> Color.White
        selected -> MaterialTheme.colorScheme.onPrimaryContainer
        weekend -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val lunarColor = when {
        hasCover -> Color.White.copy(alpha = 0.9f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box(
        modifier = modifier
            .aspectRatio(0.85f)
            .padding(1.dp)
            .clip(RoundedCornerShape(10.dp))
            .then(if (date != null) Modifier.clickable(onClick = onClick) else Modifier)
            .background(
                when {
                    hasCover -> Color.Transparent
                    selected -> MaterialTheme.colorScheme.primaryContainer
                    isToday -> MaterialTheme.colorScheme.surfaceVariant
                    else -> Color.Transparent
                }
            ),
        contentAlignment = Alignment.TopCenter
    ) {
        if (date != null) {
            val coverUri = markers?.coverImageUri
            if (!coverUri.isNullOrBlank()) {
                SoftCoverBackground(uri = coverUri, scrimAlpha = if (selected) 0.35f else 0.22f)
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
            ) {
                Text(
                    text = date.dayOfMonth.toString(),
                    fontWeight = if (selected || isToday) FontWeight.Bold else FontWeight.Normal,
                    color = dayColor,
                    fontSize = 14.sp
                )
                Text(
                    text = markers?.lunarText.orEmpty(),
                    color = lunarColor,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    if (markers?.hasEvent == true) {
                        Dot(if (hasCover) Color.White else MaterialTheme.colorScheme.primary)
                    }
                    if (markers?.hasMood == true && !hasCover) {
                        Dot(MaterialTheme.colorScheme.tertiary)
                    }
                    if (markers?.hasPeriod == true) {
                        Dot(if (hasCover) Color(0xFFFF8A80) else MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@Composable
private fun Dot(color: Color) {
    Box(
        Modifier
            .size(4.dp)
            .clip(CircleShape)
            .background(color)
    )
}

private fun typeLabel(type: EventType): String = when (type) {
    EventType.ANNIVERSARY -> "纪念日"
    EventType.COUNTDOWN -> "倒数日"
    EventType.BIRTHDAY -> "生日"
    EventType.CUSTOM -> "自定义"
}
