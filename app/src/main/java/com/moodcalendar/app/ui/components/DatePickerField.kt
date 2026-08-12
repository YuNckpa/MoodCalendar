package com.moodcalendar.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.util.DateFormats
import com.moodcalendar.app.util.LunarDateUtils
import com.moodcalendar.app.util.calendarDays
import com.moodcalendar.app.util.toIso
import com.moodcalendar.app.util.toLocalDateOrNull
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun DatePickerField(
    valueIso: String,
    onDateSelected: (String) -> Unit,
    label: String,
    supportingText: String? = null,
    calendarSystem: CalendarSystem = CalendarSystem.SOLAR,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    val date = valueIso.toLocalDateOrNull() ?: LocalDate.now()
    val lunar = remember(date) { LunarDateUtils.fromSolar(date) }
    val display = if (calendarSystem == CalendarSystem.LUNAR) {
        LunarDateUtils.format(lunar)
    } else {
        date.format(DateFormats.DISPLAY)
    }

    Box(modifier = modifier.clickable { show = true }) {
        OutlinedTextField(
            value = display,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(label) },
            supportingText = supportingText?.let { { Text(it) } },
            trailingIcon = {
                Icon(Icons.Outlined.DateRange, contentDescription = "选择日期")
            },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledBorderColor = MaterialTheme.colorScheme.outline,
                disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        )
    }

    if (show) {
        DatePickerWithLunarDialog(
            initialDate = date,
            onDismiss = { show = false },
            onConfirm = { selected ->
                onDateSelected(selected.toIso())
                show = false
            }
        )
    }
}

@Composable
fun DatePickerWithLunarDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    var selected by remember(initialDate) { mutableStateOf(initialDate) }
    var visibleMonth by remember(initialDate) { mutableStateOf(YearMonth.from(initialDate)) }
    val today = remember { LocalDate.now() }
    val selectedLunar = remember(selected) { LunarDateUtils.fromSolar(selected) }
    val weekLabels = listOf("一", "二", "三", "四", "五", "六", "日")
    val days = remember(visibleMonth) { visibleMonth.calendarDays(DayOfWeek.MONDAY) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .padding(8.dp)
        ) {
            Column(modifier = Modifier.padding(bottom = 8.dp)) {
                Text(
                    "选择日期",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)
                )

                // 顶部：公历 + 其下农历年月日
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)) {
                    Text(
                        text = selected.format(DateFormats.DISPLAY),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = LunarDateUtils.format(selectedLunar),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                // 月份切换
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowLeft,
                            contentDescription = "上个月"
                        )
                    }
                    Text(
                        text = visibleMonth.format(DateFormats.MONTH_TITLE),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                        Icon(
                            Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                            contentDescription = "下个月"
                        )
                    }
                }

                // 星期标题：等宽七列，周日不再被挤窄
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    weekLabels.forEachIndexed { index, label ->
                        val weekend = index >= 5
                        Text(
                            text = label,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (weekend) {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.85f)
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }

                // 日期网格
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                    days.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .aspectRatio(0.85f)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (date != null) {
                                        LunarDayCell(
                                            date = date,
                                            selected = date == selected,
                                            isToday = date == today,
                                            onClick = {
                                                selected = date
                                                visibleMonth = YearMonth.from(date)
                                            }
                                        )
                                    }
                                }
                            }
                            // pad incomplete last week if needed (calendarDays usually full weeks)
                            repeat(7 - week.size) {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = {
                            selected = today
                            visibleMonth = YearMonth.from(today)
                        }
                    ) {
                        Text("今日")
                    }
                    Row {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        TextButton(onClick = { onConfirm(selected) }) { Text("确定") }
                    }
                }
            }
        }
    }
}

@Composable
private fun LunarDayCell(
    date: LocalDate,
    selected: Boolean,
    isToday: Boolean,
    onClick: () -> Unit
) {
    val lunarShort = remember(date) { LunarDateUtils.shortLabel(date) }
    val isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY
    val bg = when {
        selected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val dayColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer
        isWeekend -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurface
    }
    val lunarColor = when {
        selected -> MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.9f)
        isToday -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp, horizontal = 2.dp)
            .fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = date.dayOfMonth.toString(),
            color = dayColor,
            fontSize = 16.sp,
            fontWeight = if (selected || isToday) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
        Text(
            text = lunarShort,
            color = lunarColor,
            fontSize = 9.sp,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            lineHeight = 11.sp
        )
        if (isToday && !selected) {
            Box(
                modifier = Modifier
                    .padding(top = 2.dp)
                    .height(3.dp)
                    .fillMaxWidth(0.25f)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )
        } else {
            Spacer(modifier = Modifier.height(3.dp))
        }
    }
}
