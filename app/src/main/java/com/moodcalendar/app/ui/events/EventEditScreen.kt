package com.moodcalendar.app.ui.events

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodcalendar.app.data.model.AdvanceUnit
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.YearlyMode
import com.moodcalendar.app.ui.components.DatePickerField
import com.moodcalendar.app.ui.components.DatePickerWithLunarDialog
import com.moodcalendar.app.ui.components.WheelNumberPicker
import com.moodcalendar.app.util.LunarDateUtils
import com.moodcalendar.app.util.toIso
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun EventEditScreen(
    viewModel: EventEditViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showYearlyDatePicker by remember { mutableStateOf(false) }
    var advanceUnitExpanded by remember { mutableStateOf(false) }
    val bgPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        viewModel.setBackgroundImage(uri?.toString())
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "新建事件" else "编辑事件") },
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
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("类型", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                EventType.entries.forEach { type ->
                    FilterChip(
                        selected = state.type == type,
                        onClick = { viewModel.onTypeChange(type) },
                        label = {
                            Text(
                                when (type) {
                                    EventType.ANNIVERSARY -> "纪念日"
                                    EventType.COUNTDOWN -> "倒数日"
                                    EventType.BIRTHDAY -> "生日"
                                    EventType.CUSTOM -> "自定义"
                                }
                            )
                        }
                    )
                }
            }

            OutlinedTextField(
                value = state.title,
                onValueChange = { v -> viewModel.update { it.copy(title = v, error = null) } },
                label = { Text("标题") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = { v -> viewModel.update { it.copy(note = v) } },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2
            )

            Text("背景图片（最多 1 张）", style = MaterialTheme.typography.titleMedium)
            if (state.backgroundImageUri.isNullOrBlank()) {
                OutlinedButton(
                    onClick = {
                        bgPicker.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    }
                ) {
                    Icon(Icons.Outlined.Image, contentDescription = null)
                    Text(" 选择背景图")
                }
            } else {
                Box {
                    AsyncImage(
                        model = state.backgroundImageUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    IconButton(
                        onClick = { viewModel.setBackgroundImage(null) },
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Outlined.Close, contentDescription = "移除")
                    }
                }
            }

            Text("日期类型", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = state.calendarSystem == CalendarSystem.SOLAR,
                    onClick = { viewModel.onCalendarSystemChange(CalendarSystem.SOLAR) },
                    label = { Text("公历（阳历）") }
                )
                FilterChip(
                    selected = state.calendarSystem == CalendarSystem.LUNAR,
                    onClick = { viewModel.onCalendarSystemChange(CalendarSystem.LUNAR) },
                    label = { Text("农历（阴历）") }
                )
            }

            DatePickerField(
                valueIso = state.targetDate,
                onDateSelected = viewModel::onTargetDateChange,
                label = if (state.calendarSystem == CalendarSystem.LUNAR) "农历日期" else "日期",
                supportingText = when {
                    state.calendarSystem == CalendarSystem.LUNAR -> "对应公历：${state.targetDate}"
                    state.type == EventType.COUNTDOWN -> "目标日期"
                    state.type == EventType.BIRTHDAY -> "生日日期"
                    else -> "起始日期"
                },
                calendarSystem = state.calendarSystem
            )

            if (state.type == EventType.COUNTDOWN || state.type == EventType.ANNIVERSARY) {
                Text("展示方式", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.displayMode == DisplayMode.DAYS_ONLY,
                        onClick = { viewModel.update { it.copy(displayMode = DisplayMode.DAYS_ONLY) } },
                        label = { Text("按天数") }
                    )
                    FilterChip(
                        selected = state.displayMode == DisplayMode.YEARS_AND_DAYS,
                        onClick = {
                            viewModel.update { it.copy(displayMode = DisplayMode.YEARS_AND_DAYS) }
                        },
                        label = { Text("年 + 余天") }
                    )
                }
            }

            if (state.type != EventType.COUNTDOWN) {
                Text("提醒周期", style = MaterialTheme.typography.titleMedium)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Recurrence.entries.forEach { rec ->
                        FilterChip(
                            selected = state.recurrence == rec,
                            onClick = { viewModel.update { it.copy(recurrence = rec) } },
                            label = {
                                Text(
                                    when (rec) {
                                        Recurrence.NONE -> "仅一次"
                                        Recurrence.DAILY -> "每天"
                                        Recurrence.WEEKLY -> "每周"
                                        Recurrence.MONTHLY -> "每月"
                                        Recurrence.YEARLY -> "每年"
                                    }
                                )
                            }
                        )
                    }
                }

                when (state.recurrence) {
                    Recurrence.YEARLY -> {
                        Text("每年提醒方式", style = MaterialTheme.typography.titleSmall)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = state.yearlyMode == YearlyMode.SAME_DAY,
                                onClick = {
                                    viewModel.update { it.copy(yearlyMode = YearlyMode.SAME_DAY) }
                                },
                                label = { Text("每年同一天") }
                            )
                            FilterChip(
                                selected = state.yearlyMode == YearlyMode.CUSTOM_DATES,
                                onClick = {
                                    viewModel.update { it.copy(yearlyMode = YearlyMode.CUSTOM_DATES) }
                                },
                                label = { Text("每年指定日期") }
                            )
                        }
                        if (state.yearlyMode == YearlyMode.CUSTOM_DATES) {
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                state.yearlyDates.forEach { md ->
                                    FilterChip(
                                        selected = true,
                                        onClick = { viewModel.removeYearlyDate(md) },
                                        label = {
                                            Text(
                                                formatYearlyDateChip(md, state.calendarSystem) + " ×"
                                            )
                                        }
                                    )
                                }
                            }
                            OutlinedButton(onClick = { showYearlyDatePicker = true }) {
                                Text("添加每年日期")
                            }
                            Text(
                                if (state.calendarSystem == CalendarSystem.LUNAR) {
                                    "按农历月日每年提醒；弹窗底部会显示对应农历"
                                } else {
                                    "按公历月日每年提醒；弹窗底部会显示对应农历"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Recurrence.WEEKLY -> {
                        Text("重复星期（可多选）", style = MaterialTheme.typography.titleSmall)
                        val labels = listOf(
                            1 to "一", 2 to "二", 3 to "三", 4 to "四",
                            5 to "五", 6 to "六", 7 to "日"
                        )
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            labels.forEach { (day, label) ->
                                FilterChip(
                                    selected = day in state.weeklyDays,
                                    onClick = { viewModel.toggleWeeklyDay(day) },
                                    label = { Text(label) }
                                )
                            }
                        }
                    }
                    Recurrence.MONTHLY -> {
                        if (state.calendarSystem == CalendarSystem.LUNAR) {
                            Text("每月农历日（可多选）", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "例如选「初二」则每个农历月的初二提醒；若该月无此日则跳过。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..30).forEach { day ->
                                    FilterChip(
                                        selected = day in state.monthlyDays,
                                        onClick = { viewModel.toggleMonthlyDay(day) },
                                        label = { Text(LunarDateUtils.dayInChinese(day)) }
                                    )
                                }
                            }
                        } else {
                            Text("每月日期（可多选）", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "若某月没有该日（例如 2 月没有 30 日），该月将跳过、不提醒。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                (1..31).forEach { day ->
                                    FilterChip(
                                        selected = day in state.monthlyDays,
                                        onClick = { viewModel.toggleMonthlyDay(day) },
                                        label = { Text(day.toString()) }
                                    )
                                }
                            }
                        }
                    }
                    else -> Unit
                }
            }

            Text("提醒时间", style = MaterialTheme.typography.titleMedium)
            Text(
                "%02d:%02d".format(state.remindHour, state.remindMinute),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("时", style = MaterialTheme.typography.labelMedium)
                    WheelNumberPicker(
                        value = state.remindHour,
                        range = 0..23,
                        onValueChange = { h -> viewModel.update { it.copy(remindHour = h) } },
                        format = { "%02d".format(it) }
                    )
                }
                Text(
                    ":",
                    fontSize = 28.sp,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("分", style = MaterialTheme.typography.labelMedium)
                    WheelNumberPicker(
                        value = state.remindMinute,
                        range = 0..59,
                        onValueChange = { m -> viewModel.update { it.copy(remindMinute = m) } },
                        format = { "%02d".format(it) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("当天提醒")
                Switch(
                    checked = state.remindOnDay,
                    onCheckedChange = { c -> viewModel.update { it.copy(remindOnDay = c) } }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("提前提醒")
                Switch(
                    checked = state.enableAdvance,
                    onCheckedChange = { c -> viewModel.update { it.copy(enableAdvance = c) } }
                )
            }

            if (state.enableAdvance) {
                val amountRange = advanceAmountRange(state.advanceUnit)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("提前", style = MaterialTheme.typography.labelMedium)
                        WheelNumberPicker(
                            value = state.advanceAmount.coerceIn(amountRange),
                            range = amountRange,
                            onValueChange = { n ->
                                viewModel.update { it.copy(advanceAmount = n) }
                            }
                        )
                    }
                    ExposedDropdownMenuBox(
                        expanded = advanceUnitExpanded,
                        onExpandedChange = { advanceUnitExpanded = it },
                        modifier = Modifier.weight(1f)
                    ) {
                        OutlinedTextField(
                            value = advanceUnitLabel(state.advanceUnit),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("单位") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = advanceUnitExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = advanceUnitExpanded,
                            onDismissRequest = { advanceUnitExpanded = false }
                        ) {
                            AdvanceUnit.entries.forEach { unit ->
                                DropdownMenuItem(
                                    text = { Text(advanceUnitLabel(unit)) },
                                    onClick = {
                                        viewModel.onAdvanceUnitChange(unit)
                                        advanceUnitExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(8.dp))
            Button(
                onClick = viewModel::save,
                enabled = !state.isSaving,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (state.isSaving) "保存中…" else "保存")
            }
        }
    }

    if (showYearlyDatePicker) {
        DatePickerWithLunarDialog(
            initialDate = LocalDate.now(),
            onDismiss = { showYearlyDatePicker = false },
            onConfirm = { selected ->
                viewModel.addYearlyDate(selected.toIso())
                showYearlyDatePicker = false
            }
        )
    }
}

private fun advanceUnitLabel(unit: AdvanceUnit): String = when (unit) {
    AdvanceUnit.DAYS -> "天"
    AdvanceUnit.HOURS -> "小时"
    AdvanceUnit.MINUTES -> "分钟"
}

private fun advanceAmountRange(unit: AdvanceUnit): IntRange = when (unit) {
    AdvanceUnit.DAYS -> 1..30
    AdvanceUnit.HOURS -> 1..12
    AdvanceUnit.MINUTES -> 1..60
}

private fun formatYearlyDateChip(md: String, calendarSystem: CalendarSystem): String {
    val parts = md.split("-")
    val m = parts.getOrNull(0)?.toIntOrNull() ?: return md
    val d = parts.getOrNull(1)?.toIntOrNull() ?: return md
    return if (calendarSystem == CalendarSystem.LUNAR) {
        LunarDateUtils.monthInChinese(m) + LunarDateUtils.dayInChinese(d)
    } else {
        val year = LocalDate.now().year
        val solar = runCatching { LocalDate.of(year, m, d) }.getOrNull()
            ?: runCatching { LocalDate.of(year, m, d.coerceAtMost(28)) }.getOrNull()
        val lunarSuffix = solar?.let {
            "（${LunarDateUtils.format(LunarDateUtils.fromSolar(it)).substringAfter("年")}）"
        }.orEmpty()
        "%02d-%02d%s".format(m, d, lunarSuffix)
    }
}
