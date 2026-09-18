package com.moodcalendar.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AuthTokenResponse(
    @SerialName("access_token") val accessToken: String? = null,
    @SerialName("refresh_token") val refreshToken: String? = null,
    @SerialName("expires_in") val expiresIn: Long = 3600,
    val user: AuthUserDto? = null,
    val message: String? = null
)

@Serializable
data class AuthUserDto(
    val id: String = "",
    val email: String? = null,
    @SerialName("email_confirmed_at") val emailConfirmedAt: String? = null,
    @SerialName("confirmation_sent_at") val confirmationSentAt: String? = null,
    @SerialName("user_metadata") val userMetadata: Map<String, JsonElement>? = null,
    /** Empty on duplicate signup (Supabase anti-enumeration). */
    val identities: List<JsonElement>? = null
)

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("friend_code") val friendCode: String = "",
    val email: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class UserSettingsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("theme_style") val themeStyle: String = "MALE_BLUE",
    @SerialName("custom_moods_json") val customMoodsJson: String = "[]",
    @SerialName("updated_at") val updatedAt: String
)

@Serializable
data class CalendarEventDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: Long? = null,
    val type: String,
    val title: String,
    val note: String = "",
    @SerialName("target_date") val targetDate: String,
    @SerialName("all_day") val allDay: Boolean = true,
    val recurrence: String = "YEARLY",
    @SerialName("remind_on_day") val remindOnDay: Boolean = true,
    @SerialName("remind_time") val remindTime: String = "09:00",
    @SerialName("advance_reminders_json") val advanceRemindersJson: String = "[]",
    @SerialName("display_mode") val displayMode: String = "DAYS_ONLY",
    @SerialName("yearly_mode") val yearlyMode: String = "SAME_DAY",
    @SerialName("yearly_dates_json") val yearlyDatesJson: String = "[]",
    @SerialName("weekly_days_json") val weeklyDaysJson: String = "[]",
    @SerialName("monthly_days_json") val monthlyDaysJson: String = "[]",
    @SerialName("background_image_uri") val backgroundImageUri: String? = null,
    @SerialName("calendar_system") val calendarSystem: String = "SOLAR",
    @SerialName("lunar_year") val lunarYear: Int = 0,
    @SerialName("lunar_month") val lunarMonth: Int = 0,
    @SerialName("lunar_day") val lunarDay: Int = 0,
    @SerialName("sort_order") val sortOrder: Int = 0,
    val archived: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class MoodEntryDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("client_id") val clientId: Long? = null,
    val date: String,
    val text: String = "",
    val emoji: String = "😊",
    @SerialName("emoji_label") val emojiLabel: String = "",
    val visibility: String = "FRIENDS_ALL",
    @SerialName("is_period") val isPeriod: Boolean = false,
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String,
    @SerialName("deleted_at") val deletedAt: String? = null
)

@Serializable
data class MoodImageDto(
    val id: String? = null,
    @SerialName("mood_id") val moodId: String,
    @SerialName("remote_path") val remotePath: String,
    @SerialName("sort_order") val sortOrder: Int = 0
)

@Serializable
data class FriendshipDto(
    val id: String,
    @SerialName("requester_id") val requesterId: String,
    @SerialName("addressee_id") val addresseeId: String,
    val status: String,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class FriendGroupDto(
    val id: String,
    @SerialName("owner_id") val ownerId: String,
    val name: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class FriendGroupMemberDto(
    @SerialName("group_id") val groupId: String,
    @SerialName("member_id") val memberId: String
)

@Serializable
data class MoodLikeDto(
    @SerialName("mood_id") val moodId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class MoodCommentDto(
    val id: String,
    @SerialName("mood_id") val moodId: String,
    @SerialName("user_id") val userId: String,
    val text: String,
    @SerialName("created_at") val createdAt: String? = null
)

/** Lightweight row when only counting / listing ids. */
@Serializable
data class IdOnlyDto(
    val id: String
)

@Serializable
data class MoodIdRefDto(
    @SerialName("mood_id") val moodId: String,
    val id: String? = null
)

@Serializable
data class MoodFavoriteDto(
    @SerialName("mood_id") val moodId: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class AuthErrorBody(
    val error: String? = null,
    @SerialName("error_description") val errorDescription: String? = null,
    val msg: String? = null,
    val message: String? = null
)
