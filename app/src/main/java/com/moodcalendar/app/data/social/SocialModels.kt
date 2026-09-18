package com.moodcalendar.app.data.social

data class FriendProfile(
    val userId: String,
    val displayName: String,
    val avatarUrl: String?,
    val friendCode: String,
    val email: String = ""
)

data class FriendRequest(
    val friendshipId: String,
    val fromUser: FriendProfile,
    val toUserId: String,
    val status: String
)

data class FriendGroup(
    val id: String,
    val name: String,
    val memberIds: List<String> = emptyList()
)

data class FeedMood(
    val syncId: String,
    val ownerId: String,
    val ownerName: String,
    val ownerAvatarUrl: String? = null,
    val date: String,
    val text: String,
    val emoji: String,
    val emojiLabel: String,
    val imageUrls: List<String>,
    val createdAt: Long,
    val likeCount: Int,
    val commentCount: Int,
    val likedByMe: Boolean,
    val favoritedByMe: Boolean
)

data class MoodCommentItem(
    val id: String,
    val userId: String,
    val userName: String,
    val text: String,
    val createdAt: Long
)
