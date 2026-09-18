package com.moodcalendar.app.data.remote

import com.moodcalendar.app.BuildConfig

object SupabaseConfig {
    val url: String = BuildConfig.SUPABASE_URL.trim().trimEnd('/')
    val anonKey: String = BuildConfig.SUPABASE_ANON_KEY.trim()

    /**
     * Auth email redirect. Prefer Site URL itself so Dashboard only needs:
     * Site URL = https://localhost
     * Do NOT use the project API host (*.supabase.co) as Site URL.
     */
    const val AUTH_REDIRECT = "https://localhost"

    val isConfigured: Boolean
        get() = url.isNotBlank() && anonKey.isNotBlank()
}