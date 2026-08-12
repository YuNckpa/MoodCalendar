package com.moodcalendar.app.ui.mood

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodcalendar.app.data.model.MoodVisibility
import com.moodcalendar.app.ui.components.DatePickerField
import com.moodcalendar.app.util.toLocalDateOrNull
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun MoodEditScreen(
    viewModel: MoodEditViewModel,
    onDone: () -> Unit,
    onBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCustomDialog by remember { mutableStateOf(false) }
    var customEmoji by remember { mutableStateOf("") }
    var customLabel by remember { mutableStateOf("") }

    LaunchedEffect(state.saved, state.deleted) {
        if (state.saved || state.deleted) onDone()
    }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(maxItems = 9)
    ) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.addImages(uris.map { it.toString() })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.id == 0L) "写情感记录" else "编辑情感记录") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    if (state.id != 0L) {
                        IconButton(onClick = viewModel::delete) {
                            Icon(Icons.Outlined.Delete, contentDescription = "删除")
                        }
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
            DatePickerField(
                valueIso = state.date.ifBlank { LocalDate.now().toString() },
                onDateSelected = { iso ->
                    if (iso.toLocalDateOrNull() != null) {
                        viewModel.update { it.copy(date = iso) }
                    }
                },
                label = "日期"
            )

            Text("心情", style = MaterialTheme.typography.titleMedium)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                state.moodOptions.forEach { preset ->
                    val selected = state.emoji == preset.emoji
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .width(64.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .then(
                                if (selected) {
                                    Modifier.border(
                                        2.dp,
                                        MaterialTheme.colorScheme.primary,
                                        RoundedCornerShape(10.dp)
                                    )
                                } else Modifier
                            )
                            .clickable { viewModel.selectMood(preset) }
                            .padding(6.dp)
                    ) {
                        Text(preset.emoji, fontSize = 28.sp, textAlign = TextAlign.Center)
                        Text(
                            preset.label,
                            style = MaterialTheme.typography.labelSmall,
                            textAlign = TextAlign.Center,
                            maxLines = 1
                        )
                    }
                }
                OutlinedButton(onClick = { showCustomDialog = true }) {
                    Text("自定义")
                }
            }

            Text(
                "当前：${state.emoji} ${state.emojiLabel}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = state.text,
                onValueChange = { v -> viewModel.update { it.copy(text = v) } },
                label = { Text("备注") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 4
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("月经期间")
                Switch(
                    checked = state.isPeriod,
                    onCheckedChange = { c -> viewModel.update { it.copy(isPeriod = c) } }
                )
            }

            Text("图片", style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                Icon(Icons.Outlined.Image, contentDescription = null)
                Text(" 从相册选择（最多 9 张）")
            }

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.imageUris.forEach { uri ->
                    Box {
                        AsyncImage(
                            model = uri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(88.dp)
                                .clip(RoundedCornerShape(10.dp))
                        )
                        IconButton(
                            onClick = { viewModel.removeImage(uri) },
                            modifier = Modifier.align(Alignment.TopEnd)
                        ) {
                            Icon(Icons.Outlined.Close, contentDescription = "移除")
                        }
                    }
                }
            }

            Text("可见性（二期社交生效）", style = MaterialTheme.typography.titleMedium)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MoodVisibility.entries.forEach { vis ->
                    FilterChip(
                        selected = state.visibility == vis,
                        onClick = { viewModel.update { it.copy(visibility = vis) } },
                        label = {
                            Text(
                                when (vis) {
                                    MoodVisibility.PRIVATE -> "仅自己"
                                    MoodVisibility.FRIENDS_ALL -> "全部好友"
                                    MoodVisibility.GROUP -> "指定分组"
                                }
                            )
                        }
                    )
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

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义心情") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = customEmoji,
                        onValueChange = { customEmoji = it },
                        label = { Text("表情符号") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = customLabel,
                        onValueChange = { customLabel = it },
                        label = { Text("含义标题") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.addCustomMood(customEmoji, customLabel)
                        showCustomDialog = false
                        customEmoji = ""
                        customLabel = ""
                    }
                ) { Text("保存到列表") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("取消") }
            }
        )
    }
}
