package com.moodcalendar.app.data.auth

import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException
import kotlin.coroutines.cancellation.CancellationException

object AuthErrorMessages {
    fun from(throwable: Throwable): String {
        if (throwable is CancellationException) return "操作已取消"
        val original = throwable.message?.trim().orEmpty()
        val raw = buildString {
            append(original)
            throwable.cause?.message?.let { append(' ').append(it) }
        }.lowercase()

        return when {
            raw.isBlank() -> "操作失败，请稍后重试"
            "already registered" in raw || "already been registered" in raw ||
                "user already exists" in raw || "email_exists" in raw ->
                "该邮箱已注册，请直接登录"

            "invalid login credentials" in raw || "invalid_credentials" in raw ->
                "账号不存在或密码错误"

            "email not confirmed" in raw || "email_not_confirmed" in raw ->
                "邮箱尚未验证，请先完成邮箱确认后再登录"

            ("otp" in raw || "token" in raw) && ("invalid" in raw || "expired" in raw) ||
                "invalid_token" in raw || "otp_expired" in raw ->
                "验证码错误或已过期，请重新获取"

            "user not found" in raw || "user_not_found" in raw ->
                "账号不存在，请先注册"

            "invalid email" in raw || "unable to validate email" in raw ||
                "email_address_invalid" in raw ->
                "邮箱格式不正确"

            "password" in raw && ("at least" in raw || "weak" in raw || "short" in raw) ->
                "密码不符合要求，请至少设置 6 位"

            "signup is disabled" in raw || "signups not allowed" in raw ->
                "当前暂未开放注册"

            "too many requests" in raw || "rate limit" in raw || "over_request_rate_limit" in raw ||
                "email rate limit" in raw ->
                "尝试过于频繁，请稍后再试（免费邮件有发送上限）"

            "error sending confirmation email" in raw || "error sending confirmation mail" in raw ||
                "error sending" in raw && "email" in raw ->
                "验证邮件发送失败：多半是 Supabase 默认邮箱限额或未配置 SMTP。" +
                    "请到 Dashboard → Authentication → Emails 配置自定义 SMTP，或稍后再试"

            "requested path is invalid" in raw ->
                "云端 Auth 地址配置有误：请把 Site URL 改成 https://localhost（不要填 *.supabase.co）"

            "redirect" in raw && ("allow" in raw || "not allowed" in raw || "invalid" in raw) ->
                "Redirect URL 未放行：请在 Authentication → URL Configuration 把 Site URL 设为 https://localhost"

            "未配置 supabase" in raw || "supabase.url" in raw ->
                "尚未配置云端服务，请先完成 Supabase 配置"

            "请先登录" in raw -> "请先登录"

            isLikelyTransportError(throwable, raw) ->
                "网络异常，请检查网络后重试"

            else -> {
                if (original.any { it in '\u4e00'..'\u9fff' }) original
                else "操作失败：$original"
            }
        }
    }

    /**
     * Only treat real transport failures as network errors.
     * API failures from Supabase are also wrapped in [IOException], so do not map all IOExceptions here.
     */
    private fun isLikelyTransportError(throwable: Throwable, raw: String): Boolean {
        var t: Throwable? = throwable
        while (t != null) {
            when (t) {
                is UnknownHostException,
                is SocketTimeoutException,
                is ConnectException,
                is SSLException -> return true
            }
            t = t.cause
        }
        return "unable to resolve" in raw ||
            "failed to connect" in raw ||
            "connection refused" in raw ||
            "connection reset" in raw ||
            "network unreachable" in raw ||
            "software caused connection abort" in raw ||
            (raw.contains("timeout") && !raw.contains("otp") && !raw.contains("token"))
    }
}
