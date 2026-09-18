package com.moodcalendar.app.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable

@Composable
fun LoginRequiredDialog(
    visible: Boolean,
    onDismiss: () -> Unit,
    onGoLogin: () -> Unit
) {
    if (!visible) return
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("需要登录") },
        text = { Text("使用好友动态、点赞、评论、收藏等社交功能前，请先登录账号。") },
        confirmButton = {
            TextButton(onClick = onGoLogin) { Text("去登录") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
