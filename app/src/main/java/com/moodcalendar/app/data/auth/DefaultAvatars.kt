package com.moodcalendar.app.data.auth

/**
 * Default avatar for new accounts (remote URL stored on profile).
 * UI falls back to local [com.moodcalendar.app.R.drawable.ic_default_avatar] when blank.
 */
object DefaultAvatars {
    fun urlFor(userId: String): String =
        "https://api.dicebear.com/7.x/thumbs/png?seed=${userId.trim()}&size=128"
}
