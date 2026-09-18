package com.moodcalendar.app.data.auth

import com.moodcalendar.app.data.remote.AuthTokenResponse
import com.moodcalendar.app.data.remote.ProfileDto
import com.moodcalendar.app.data.remote.SupabaseClient
import com.moodcalendar.app.data.remote.SupabaseConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException

enum class SignupCodeResult {
    /** Verification email / OTP sent; user must enter code. */
    CodeSent,
    /** Project has email confirm disabled; signup already created a session. */
    AutoSignedIn,
    /** Email already belongs to an account — prompt user to sign in. */
    AlreadyRegistered
}

class AuthRepository(
    private val client: SupabaseClient,
    private val sessionStore: SessionStore
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val refreshMutex = Mutex()

    val session: StateFlow<UserSession?> = sessionStore.sessionFlow
        .stateIn(scope, SharingStarted.Eagerly, null)

    val isConfigured: Boolean get() = SupabaseConfig.isConfigured

    fun isLoggedIn(): Boolean = session.value != null

    /**
     * Starts registration: creates auth user and triggers verification email (OTP / link).
     * Prefer enabling "Confirm email" and putting `{{ .Token }}` in the Confirm signup template.
     */
    suspend fun sendSignupVerification(
        email: String,
        password: String,
        displayName: String
    ): SignupCodeResult {
        val normalizedEmail = email.trim()
        val name = displayName.trim().ifBlank { normalizedEmail.substringBefore('@') }
        return try {
            val token = client.signUp(normalizedEmail, password, name)
            val user = token.user
            val identities = user?.identities
            val hasSession =
                !token.accessToken.isNullOrBlank() && !token.refreshToken.isNullOrBlank()

            when {
                // Duplicate account: GoTrue returns 200 with empty identities and no session.
                !hasSession && identities != null && identities.isEmpty() ->
                    SignupCodeResult.AlreadyRegistered

                // Confirmed existing user sometimes still has confirmation markers without identities.
                !hasSession &&
                    user != null &&
                    !user.emailConfirmedAt.isNullOrBlank() &&
                    identities.isNullOrEmpty() ->
                    SignupCodeResult.AlreadyRegistered

                hasSession -> {
                    persistFromAuth(token, normalizedEmail, name)
                    ensureDefaultAvatar()
                    SignupCodeResult.AutoSignedIn
                }

                // New signup awaiting email OTP (identities usually non-empty).
                else -> SignupCodeResult.CodeSent
            }
        } catch (e: IOException) {
            val msg = e.message.orEmpty().lowercase()
            if (
                "already registered" in msg ||
                "already been registered" in msg ||
                "email_exists" in msg ||
                "user_already_exists" in msg ||
                "user already exists" in msg
            ) {
                SignupCodeResult.AlreadyRegistered
            } else {
                throw e
            }
        }
    }

    suspend fun completeSignupWithCode(email: String, code: String, displayNameHint: String?) {
        val token = client.verifyOtp(email.trim(), code.trim(), type = "signup")
        persistFromAuth(token, email.trim(), displayNameHint)
        ensureDefaultAvatar()
    }

    suspend fun signIn(email: String, password: String) {
        val token = client.signIn(email.trim(), password)
        persistFromAuth(token, email.trim(), null)
        ensureDefaultAvatar()
    }

    suspend fun requestPasswordReset(email: String) {
        client.recoverPassword(email.trim())
    }

    /**
     * Reset via email OTP (requires Reset Password template to include `{{ .Token }}`).
     */
    suspend fun resetPasswordWithCode(email: String, code: String, newPassword: String) {
        val token = client.verifyOtp(email.trim(), code.trim(), type = "recovery")
        val access = token.accessToken ?: error("重置验证失败，请检查验证码")
        client.updatePassword(access, newPassword)
        // Do not keep recovery session as logged-in unless we want to — clear and ask login
        sessionStore.clear()
    }

    suspend fun changePassword(newPassword: String) {
        val access = validAccessToken()
        client.updatePassword(access, newPassword)
    }

    suspend fun signOut() {
        val current = session.value
        if (current != null) {
            runCatching { client.signOut(current.accessToken) }
        }
        sessionStore.clear()
    }

    suspend fun updateProfile(displayName: String, avatarUrl: String? = null) {
        val access = validAccessToken()
        val current = requireSession()
        val body = buildJsonObject {
            put("display_name", displayName.trim())
            if (avatarUrl != null) put("avatar_url", avatarUrl)
            put("updated_at", java.time.Instant.now().toString())
        }
        client.patch(
            table = "profiles",
            accessToken = access,
            query = "id=eq.${current.userId}",
            bodyJson = client.jsonParser.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                body
            )
        )
        sessionStore.save(
            current.copy(
                displayName = displayName.trim(),
                avatarUrl = avatarUrl ?: current.avatarUrl
            )
        )
    }

    suspend fun updateAvatar(localImageFile: File) {
        val access = validAccessToken()
        val current = requireSession()
        val url = client.uploadAvatar(access, current.userId, localImageFile)
        updateProfile(current.displayName, avatarUrl = url)
    }

    suspend fun validAccessToken(): String {
        val current = requireSession()
        val soon = System.currentTimeMillis() + 60_000
        if (current.expiresAtEpochMs > soon) return current.accessToken
        return refreshMutex.withLock {
            val latest = sessionStore.sessionFlow.first() ?: error("未登录")
            if (latest.expiresAtEpochMs > soon) return@withLock latest.accessToken
            val refreshed = client.refresh(latest.refreshToken)
            val access = refreshed.accessToken ?: error("登录已过期，请重新登录")
            val refresh = refreshed.refreshToken ?: latest.refreshToken
            val userId = refreshed.user?.id ?: latest.userId
            val email = refreshed.user?.email ?: latest.email
            val next = latest.copy(
                userId = userId,
                email = email,
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochMs = System.currentTimeMillis() + refreshed.expiresIn * 1000
            )
            sessionStore.save(next)
            next.accessToken
        }
    }

    suspend fun requireSession(): UserSession =
        session.value ?: sessionStore.sessionFlow.first() ?: error("请先登录")

    private suspend fun ensureDefaultAvatar() {
        val current = session.value ?: return
        if (!current.avatarUrl.isNullOrBlank()) return
        val defaultUrl = DefaultAvatars.urlFor(current.userId)
        runCatching { updateProfile(current.displayName, avatarUrl = defaultUrl) }
    }

    private suspend fun persistFromAuth(
        token: AuthTokenResponse,
        emailFallback: String,
        displayNameHint: String?
    ) {
        val access = token.accessToken ?: error("登录响应缺少凭证，请检查邮箱验证是否完成")
        val refresh = token.refreshToken ?: error("登录响应缺少刷新凭证")
        val userId = token.user?.id ?: error("登录响应缺少用户信息")
        val email = token.user?.email ?: emailFallback
        var displayName = displayNameHint.orEmpty()
        var avatarUrl: String? = null
        var friendCode = ""
        runCatching {
            val profile: ProfileDto = client.select(
                table = "profiles",
                accessToken = access,
                query = "id=eq.$userId&select=*",
                single = true
            )
            displayName = profile.displayName.ifBlank {
                displayName.ifBlank { email.substringBefore('@') }
            }
            avatarUrl = profile.avatarUrl
            friendCode = profile.friendCode
            if (profile.email.isNullOrBlank() && email.isNotBlank()) {
                runCatching {
                    client.patch(
                        table = "profiles",
                        accessToken = access,
                        query = "id=eq.$userId",
                        bodyJson = client.jsonParser.encodeToString(
                            kotlinx.serialization.json.JsonObject.serializer(),
                            buildJsonObject { put("email", email) }
                        )
                    )
                }
            }
        }
        sessionStore.save(
            UserSession(
                userId = userId,
                email = email,
                accessToken = access,
                refreshToken = refresh,
                expiresAtEpochMs = System.currentTimeMillis() + token.expiresIn * 1000,
                displayName = displayName.ifBlank { email.substringBefore('@') },
                avatarUrl = avatarUrl,
                friendCode = friendCode
            )
        )
    }
}
