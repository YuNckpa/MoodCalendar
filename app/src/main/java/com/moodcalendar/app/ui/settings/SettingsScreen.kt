package com.moodcalendar.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onOpenTheme: () -> Unit,
    onOpenReminder: () -> Unit
) {
    Scaffold(
        topBar = { TopAppBar(title = { Text("我的") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("设置", style = MaterialTheme.typography.titleLarge)
            SettingsNavRow(title = "主题风格", subtitle = "男生蓝 / 女生粉", onClick = onOpenTheme)
            HorizontalDivider()
            SettingsNavRow(title = "提醒权限", subtitle = "通知与精确闹钟", onClick = onOpenReminder)
            HorizontalDivider()

            Text(
                "关于",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 20.dp, bottom = 8.dp)
            )
            Text(
                "MoodCalendar：本地日历、纪念日 / 倒数日 / 生日、情感记录与提醒。\n好友、分组与云端同步将在二期开放。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SettingsNavRow(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
    }
}
