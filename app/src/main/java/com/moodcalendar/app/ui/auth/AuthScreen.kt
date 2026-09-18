package com.moodcalendar.app.ui.auth

import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.canhub.cropper.CropImageContract
import com.canhub.cropper.CropImageContractOptions
import com.canhub.cropper.CropImageOptions
import com.canhub.cropper.CropImageView
import com.moodcalendar.app.data.sync.SyncStatus
import com.moodcalendar.app.ui.components.UserAvatar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit
) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val sync by viewModel.syncState.collectAsStateWithLifecycle()

    val cropAvatar = rememberLauncherForActivityResult(CropImageContract()) { result ->
        when {
            result.isSuccessful -> result.uriContent?.let(viewModel::updateAvatar)
            result.error != null -> viewModel.update {
                it.copy(error = result.error?.localizedMessage ?: "裁剪头像失败")
            }
        }
    }
    val clipboard = LocalClipboardManager.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when {
                            session != null -> "账号与同步"
                            ui.page == AuthPage.Register -> "注册"
                            ui.page == AuthPage.ForgotPassword -> "忘记密码"
                            else -> "登录"
                        }
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
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
            if (!viewModel.isConfigured) {
                Text(
                    text = "尚未配置 Supabase。请在 local.properties 填写 supabase.url / supabase.anonKey，" +
                        "并执行 supabase/migrations 下的 SQL。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
            }

            val current = session
            if (current == null) {
                when (ui.page) {
                    AuthPage.Login -> LoginForm(ui, viewModel)
                    AuthPage.Register -> RegisterForm(ui, viewModel)
                    AuthPage.ForgotPassword -> ForgotForm(ui, viewModel)
                }
            } else {
                LoggedInSection(
                    email = current.email,
                    displayName = current.displayName,
                    friendCode = current.friendCode,
                    avatarUrl = current.avatarUrl,
                    ui = ui,
                    syncStatus = sync.status,
                    syncMessage = sync.message,
                    loading = ui.loading,
                    onPickAvatar = {
                        cropAvatar.launch(
                            CropImageContractOptions(
                                uri = null,
                                cropImageOptions = CropImageOptions(
                                    imageSourceIncludeCamera = false,
                                    imageSourceIncludeGallery = true,
                                    guidelines = CropImageView.Guidelines.ON,
                                    aspectRatioX = 1,
                                    aspectRatioY = 1,
                                    fixAspectRatio = true,
                                    cropShape = CropImageView.CropShape.OVAL,
                                    outputCompressFormat = Bitmap.CompressFormat.JPEG,
                                    outputRequestWidth = 512,
                                    outputRequestHeight = 512
                                )
                            )
                        )
                    },
                    onCopyFriendCode = {
                        if (current.friendCode.isNotBlank()) {
                            clipboard.setText(AnnotatedString(current.friendCode))
                            viewModel.update { it.copy(info = "好友码已复制", error = null) }
                        }
                    },
                    onSaveName = {
                        viewModel.updateDisplayName(
                            ui.displayName.ifBlank { current.displayName }
                        )
                    },
                    onChangePassword = viewModel::changePassword,
                    onSync = viewModel::syncNow,
                    onSignOut = viewModel::signOut,
                    onUpdate = viewModel::update
                )
            }

            ui.error?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
            ui.info?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun LoginForm(ui: AuthUiState, viewModel: AuthViewModel) {
    Text(
        text = "登录后可同步云端并使用社交功能",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = ui.email,
        onValueChange = { v -> viewModel.update { it.copy(email = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    OutlinedTextField(
        value = ui.password,
        onValueChange = { v -> viewModel.update { it.copy(password = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    Button(
        onClick = viewModel::submitLogin,
        enabled = !ui.loading && viewModel.isConfigured,
        modifier = Modifier.fillMaxWidth()
    ) { Text("登录") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        TextButton(onClick = { viewModel.switchPage(AuthPage.ForgotPassword) }) {
            Text("忘记密码")
        }
        TextButton(onClick = { viewModel.switchPage(AuthPage.Register) }) {
            Text("没有账号？去注册")
        }
    }
}

@Composable
private fun RegisterForm(ui: AuthUiState, viewModel: AuthViewModel) {
    Text(
        text = "注册需邮箱验证码。请在 Supabase 开启 Confirm email，并在确认邮件模板中加入 {{ .Token }}。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = ui.displayName,
        onValueChange = { v -> viewModel.update { it.copy(displayName = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("昵称") },
        singleLine = true
    )
    OutlinedTextField(
        value = ui.email,
        onValueChange = { v -> viewModel.update { it.copy(email = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    OutlinedTextField(
        value = ui.password,
        onValueChange = { v -> viewModel.update { it.copy(password = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("密码（至少 6 位）") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = ui.otpCode,
            onValueChange = { v -> viewModel.update { it.copy(otpCode = v.filter { ch -> ch.isDigit() }.take(8)) } },
            modifier = Modifier.weight(1f),
            label = { Text("验证码") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
        )
        OutlinedButton(
            onClick = viewModel::sendRegisterCode,
            enabled = !ui.sendingCode && ui.codeCooldownSec == 0 && viewModel.isConfigured
        ) {
            Text(
                when {
                    ui.sendingCode -> "发送中"
                    ui.codeCooldownSec > 0 -> "${ui.codeCooldownSec}s"
                    else -> "发送验证码"
                }
            )
        }
    }
    Button(
        onClick = viewModel::submitRegister,
        enabled = !ui.loading && viewModel.isConfigured,
        modifier = Modifier.fillMaxWidth()
    ) { Text("注册") }
    TextButton(onClick = { viewModel.switchPage(AuthPage.Login) }) {
        Text("已有账号？去登录")
    }
}

@Composable
private fun ForgotForm(ui: AuthUiState, viewModel: AuthViewModel) {
    Text(
        text = "输入注册邮箱获取重置邮件。若邮件模板包含验证码 {{ .Token }}，可在此直接重置；否则请点击邮件链接。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedTextField(
        value = ui.email,
        onValueChange = { v -> viewModel.update { it.copy(email = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮箱") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
    )
    OutlinedButton(
        onClick = viewModel::sendForgotCode,
        enabled = !ui.sendingCode && ui.codeCooldownSec == 0 && viewModel.isConfigured,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                ui.sendingCode -> "发送中…"
                ui.codeCooldownSec > 0 -> "重新发送（${ui.codeCooldownSec}s）"
                else -> "发送重置邮件"
            }
        )
    }
    OutlinedTextField(
        value = ui.otpCode,
        onValueChange = { v -> viewModel.update { it.copy(otpCode = v.filter { ch -> ch.isDigit() }.take(8)) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("邮件验证码（可选，视模板而定）") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
    )
    OutlinedTextField(
        value = ui.newPassword,
        onValueChange = { v -> viewModel.update { it.copy(newPassword = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    OutlinedTextField(
        value = ui.confirmPassword,
        onValueChange = { v -> viewModel.update { it.copy(confirmPassword = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("确认新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    Button(
        onClick = viewModel::submitForgotReset,
        enabled = !ui.loading && viewModel.isConfigured,
        modifier = Modifier.fillMaxWidth()
    ) { Text("用验证码重置密码") }
    TextButton(onClick = { viewModel.switchPage(AuthPage.Login) }) {
        Text("返回登录")
    }
}

@Composable
private fun LoggedInSection(
    email: String,
    displayName: String,
    friendCode: String,
    avatarUrl: String?,
    ui: AuthUiState,
    syncStatus: SyncStatus,
    syncMessage: String,
    loading: Boolean,
    onPickAvatar: () -> Unit,
    onCopyFriendCode: () -> Unit,
    onSaveName: () -> Unit,
    onChangePassword: () -> Unit,
    onSync: () -> Unit,
    onSignOut: () -> Unit,
    onUpdate: ((AuthUiState) -> AuthUiState) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        UserAvatar(
            avatarUrl = avatarUrl,
            size = 88.dp,
            modifier = Modifier.clickable(onClick = onPickAvatar)
        )
        TextButton(onClick = onPickAvatar, enabled = !loading) {
            Text("更换头像")
        }
        Text(
            text = "选择图片后可拖动裁剪圆形区域",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Text("邮箱：$email")
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "好友码：${friendCode.ifBlank { "加载中…" }}",
            modifier = Modifier.weight(1f)
        )
        if (friendCode.isNotBlank()) {
            IconButton(onClick = onCopyFriendCode) {
                Icon(Icons.Outlined.ContentCopy, contentDescription = "复制好友码")
            }
            TextButton(onClick = onCopyFriendCode) { Text("复制") }
        }
    }
    OutlinedTextField(
        value = ui.displayName.ifBlank { displayName },
        onValueChange = { v -> onUpdate { it.copy(displayName = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("昵称") },
        singleLine = true
    )
    OutlinedButton(
        onClick = onSaveName,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) { Text("保存昵称") }

    Text("修改密码", style = MaterialTheme.typography.titleMedium)
    OutlinedTextField(
        value = ui.newPassword,
        onValueChange = { v -> onUpdate { it.copy(newPassword = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    OutlinedTextField(
        value = ui.confirmPassword,
        onValueChange = { v -> onUpdate { it.copy(confirmPassword = v) } },
        modifier = Modifier.fillMaxWidth(),
        label = { Text("确认新密码") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
    )
    OutlinedButton(
        onClick = onChangePassword,
        enabled = !loading,
        modifier = Modifier.fillMaxWidth()
    ) { Text("保存新密码") }

    val syncLabel = when (syncStatus) {
        SyncStatus.Idle -> syncMessage.ifBlank { "登录后将自动后台同步" }
        SyncStatus.Syncing -> syncMessage.ifBlank { "后台同步中…" }
        SyncStatus.Success -> syncMessage.ifBlank { "同步成功" }
        SyncStatus.Error -> "同步失败：$syncMessage"
    }
    Text(
        text = syncLabel,
        color = if (syncStatus == SyncStatus.Error) {
            MaterialTheme.colorScheme.error
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        }
    )
    Button(
        onClick = onSync,
        enabled = !loading && syncStatus != SyncStatus.Syncing,
        modifier = Modifier.fillMaxWidth()
    ) { Text(if (syncStatus == SyncStatus.Syncing) "同步中…" else "立即同步") }

    Text(
        text = "退出登录后本地日历、事件与心情仍保留，可继续离线使用。",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = onSignOut, modifier = Modifier.fillMaxWidth()) {
        Text("退出登录")
    }
}
