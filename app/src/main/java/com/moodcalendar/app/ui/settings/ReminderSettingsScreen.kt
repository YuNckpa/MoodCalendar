package com.moodcalendar.app.ui.settings

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var notifEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提醒权限") },
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
            Text(
                "通知：" + if (notifEnabled) "已开启" else "未开启",
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        openAppNotificationSettings(context)
                    }
                    notifEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("申请 / 打开通知权限")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                val canExact = am.canScheduleExactAlarms()
                Text(
                    "精确闹钟：" + if (canExact) "已允许" else "未允许（提醒可能不准时）",
                    style = MaterialTheme.typography.bodyLarge
                )
                Button(
                    onClick = {
                        val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("打开精确闹钟设置")
                }
            }
        }
    }
}

private fun openAppNotificationSettings(context: Context) {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
        putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
    }
    context.startActivity(intent)
}
