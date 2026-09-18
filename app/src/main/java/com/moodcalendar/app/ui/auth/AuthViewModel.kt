package com.moodcalendar.app.ui.auth

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.auth.AuthErrorMessages
import com.moodcalendar.app.data.auth.AuthRepository
import com.moodcalendar.app.data.auth.SignupCodeResult
import com.moodcalendar.app.data.auth.UserSession
import com.moodcalendar.app.data.sync.SyncEngine
import com.moodcalendar.app.data.sync.SyncState
import com.moodcalendar.app.util.ImageStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AuthPage {
    Login,
    Register,
    ForgotPassword
}

data class AuthUiState(
    val page: AuthPage = AuthPage.Login,
    val email: String = "",
    val password: String = "",
    val displayName: String = "",
    val otpCode: String = "",
    val newPassword: String = "",
    val confirmPassword: String = "",
    val loading: Boolean = false,
    val sendingCode: Boolean = false,
    val codeCooldownSec: Int = 0,
    val codeSent: Boolean = false,
    val error: String? = null,
    val info: String? = null
)

class AuthViewModel(
    application: Application,
    private val authRepository: AuthRepository,
    private val syncEngine: SyncEngine
) : AndroidViewModel(application) {
    val session: StateFlow<UserSession?> = authRepository.session
    val syncState: StateFlow<SyncState> = syncEngine.state

    private val _ui = MutableStateFlow(AuthUiState())
    val ui: StateFlow<AuthUiState> = _ui.asStateFlow()

    val isConfigured: Boolean = authRepository.isConfigured

    fun update(transform: (AuthUiState) -> AuthUiState) {
        _ui.update(transform)
    }

    fun switchPage(page: AuthPage) {
        _ui.update {
            it.copy(
                page = page,
                error = null,
                info = null,
                otpCode = "",
                newPassword = "",
                confirmPassword = "",
                codeSent = false
            )
        }
    }

    fun sendRegisterCode() {
        val state = _ui.value
        val email = state.email.trim()
        if (!isValidEmail(email)) {
            _ui.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        if (state.password.length < 6) {
            _ui.update { it.copy(error = "密码至少 6 位") }
            return
        }
        if (state.displayName.isBlank()) {
            _ui.update { it.copy(error = "请填写昵称") }
            return
        }
        if (state.codeCooldownSec > 0 || state.sendingCode) return

        viewModelScope.launch {
            _ui.update { it.copy(sendingCode = true, error = null, info = null) }
            try {
                when (
                    authRepository.sendSignupVerification(email, state.password, state.displayName)
                ) {
                    SignupCodeResult.CodeSent -> {
                        _ui.update {
                            it.copy(
                                sendingCode = false,
                                codeSent = true,
                                info = "若该邮箱是新账号，验证码会发到邮箱（请查垃圾箱）。" +
                                    "若长期收不到，可能是该邮箱已注册——请返回登录，或点「忘记密码」。"
                            )
                        }
                        startCooldown()
                    }
                    SignupCodeResult.AutoSignedIn -> {
                        syncEngine.syncInBackground(silent = true)
                        _ui.update {
                            it.copy(
                                sendingCode = false,
                                codeSent = false,
                                info = "注册成功（当前未强制邮箱验证），正在后台同步",
                                password = ""
                            )
                        }
                    }
                    SignupCodeResult.AlreadyRegistered -> {
                        _ui.update {
                            it.copy(
                                sendingCode = false,
                                codeSent = false,
                                page = AuthPage.Login,
                                error = null,
                                info = "该邮箱已注册，不会再发验证码。请直接登录；忘记密码请点「忘记密码」。"
                            )
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(sendingCode = false, error = AuthErrorMessages.from(e))
                }
            }
        }
    }

    fun submitRegister() {
        val state = _ui.value
        val email = state.email.trim()
        if (!isValidEmail(email)) {
            _ui.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        if (state.password.length < 6) {
            _ui.update { it.copy(error = "密码至少 6 位") }
            return
        }
        if (state.displayName.isBlank()) {
            _ui.update { it.copy(error = "请填写昵称") }
            return
        }
        if (state.otpCode.trim().length < 6) {
            _ui.update { it.copy(error = "请输入邮箱收到的验证码") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            try {
                authRepository.completeSignupWithCode(
                    email = email,
                    code = state.otpCode,
                    displayNameHint = state.displayName
                )
                syncEngine.syncInBackground(silent = true)
                _ui.update {
                    it.copy(
                        loading = false,
                        info = "注册成功，正在后台同步数据",
                        password = "",
                        otpCode = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun submitLogin() {
        val state = _ui.value
        val email = state.email.trim()
        if (!isValidEmail(email)) {
            _ui.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        if (state.password.length < 6) {
            _ui.update { it.copy(error = "密码至少 6 位") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            try {
                authRepository.signIn(email, state.password)
                syncEngine.syncInBackground(silent = true)
                _ui.update {
                    it.copy(
                        loading = false,
                        info = "登录成功，正在后台同步数据",
                        password = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun sendForgotCode() {
        val email = _ui.value.email.trim()
        if (!isValidEmail(email)) {
            _ui.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        if (_ui.value.codeCooldownSec > 0 || _ui.value.sendingCode) return
        viewModelScope.launch {
            _ui.update { it.copy(sendingCode = true, error = null, info = null) }
            try {
                authRepository.requestPasswordReset(email)
                _ui.update {
                    it.copy(
                        sendingCode = false,
                        codeSent = true,
                        info = "重置邮件已发送。若模板含验证码，请填写下方验证码与新密码；也可点击邮件链接重置。"
                    )
                }
                startCooldown()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(sendingCode = false, error = AuthErrorMessages.from(e))
                }
            }
        }
    }

    fun submitForgotReset() {
        val state = _ui.value
        val email = state.email.trim()
        if (!isValidEmail(email)) {
            _ui.update { it.copy(error = "请输入正确的邮箱地址") }
            return
        }
        if (state.otpCode.trim().length < 6) {
            _ui.update { it.copy(error = "请输入邮件中的验证码") }
            return
        }
        if (state.newPassword.length < 6) {
            _ui.update { it.copy(error = "新密码至少 6 位") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _ui.update { it.copy(error = "两次输入的新密码不一致") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            try {
                authRepository.resetPasswordWithCode(email, state.otpCode, state.newPassword)
                _ui.update {
                    it.copy(
                        loading = false,
                        page = AuthPage.Login,
                        info = "密码已重置，请使用新密码登录",
                        otpCode = "",
                        newPassword = "",
                        confirmPassword = "",
                        password = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun changePassword() {
        val state = _ui.value
        if (state.newPassword.length < 6) {
            _ui.update { it.copy(error = "新密码至少 6 位") }
            return
        }
        if (state.newPassword != state.confirmPassword) {
            _ui.update { it.copy(error = "两次输入的新密码不一致") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            try {
                authRepository.changePassword(state.newPassword)
                _ui.update {
                    it.copy(
                        loading = false,
                        info = "密码已修改",
                        newPassword = "",
                        confirmPassword = ""
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun updateAvatar(uri: Uri) {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null) }
            try {
                val path = withContext(Dispatchers.IO) {
                    ImageStore.persistAvatar(getApplication(), uri.toString())
                } ?: error("无法读取图片")
                authRepository.updateAvatar(File(path))
                _ui.update { it.copy(loading = false, info = "头像已更新") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(loading = false, error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                authRepository.signOut()
                _ui.update {
                    it.copy(
                        page = AuthPage.Login,
                        info = "已退出登录，本地数据仍保留",
                        error = null
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update { it.copy(error = AuthErrorMessages.from(e)) }
            }
        }
    }

    fun syncNow() {
        viewModelScope.launch {
            _ui.update { it.copy(error = null, info = "正在同步…") }
            try {
                syncEngine.syncAll(silent = false)
                _ui.update { it.copy(info = "同步完成") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(
                        error = syncEngine.state.value.message.ifBlank {
                            AuthErrorMessages.from(e)
                        },
                        info = null
                    )
                }
            }
        }
    }

    fun updateDisplayName(name: String) {
        if (name.isBlank()) {
            _ui.update { it.copy(error = "昵称不能为空") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            try {
                authRepository.updateProfile(name)
                _ui.update { it.copy(loading = false, info = "昵称已更新") }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _ui.update {
                    it.copy(loading = false, error = AuthErrorMessages.from(e))
                }
            }
        }
    }

    private fun startCooldown(seconds: Int = 60) {
        viewModelScope.launch {
            for (left in seconds downTo 1) {
                _ui.update { it.copy(codeCooldownSec = left) }
                delay(1000)
            }
            _ui.update { it.copy(codeCooldownSec = 0) }
        }
    }

    private fun isValidEmail(email: String): Boolean =
        email.isNotBlank() && '@' in email && '.' in email.substringAfter('@')

    companion object {
        fun factory(
            application: Application,
            authRepository: AuthRepository,
            syncEngine: SyncEngine
        ) = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AuthViewModel(application, authRepository, syncEngine) as T
            }
        }
    }
}
