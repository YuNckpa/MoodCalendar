package com.moodcalendar.app.data.social

import com.moodcalendar.app.data.auth.AuthRepository
import com.moodcalendar.app.data.remote.FriendGroupDto
import com.moodcalendar.app.data.remote.FriendGroupMemberDto
import com.moodcalendar.app.data.remote.FriendshipDto
import com.moodcalendar.app.data.remote.MoodIdRefDto
import com.moodcalendar.app.data.remote.MoodCommentDto
import com.moodcalendar.app.data.remote.MoodEntryDto
import com.moodcalendar.app.data.remote.MoodFavoriteDto
import com.moodcalendar.app.data.remote.MoodImageDto
import com.moodcalendar.app.data.remote.MoodLikeDto
import com.moodcalendar.app.data.remote.ProfileDto
import com.moodcalendar.app.data.remote.SupabaseClient
import com.moodcalendar.app.data.sync.TimeFormats
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class FeedPage(
    val items: List<FeedMood>,
    val hasMore: Boolean,
    val ownerIds: List<String>
)

class SocialRepository(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository
) {
    private suspend fun token() = authRepository.validAccessToken()
    private suspend fun uid() = authRepository.requireSession().userId

    suspend fun searchByFriendCode(code: String): FriendProfile? {
        val normalized = code.trim().uppercase()
        if (normalized.isBlank()) return null
        val list: List<ProfileDto> = client.select(
            table = "profiles",
            accessToken = token(),
            query = "friend_code=eq.$normalized&select=*"
        )
        return list.firstOrNull()?.toFriend()
    }

    suspend fun searchUsers(query: String): List<FriendProfile> {
        val q = query.trim()
        if (q.isBlank()) return emptyList()
        val me = uid()
        val safe = q.replace(Regex("[(),]"), " ").trim()
        if (safe.isBlank()) return emptyList()
        val pattern = "*$safe*"
        val list: List<ProfileDto> = client.select(
            table = "profiles",
            accessToken = token(),
            query = "or=(display_name.ilike.$pattern,email.ilike.$pattern,friend_code.ilike.$pattern)" +
                "&id=neq.$me&select=*&limit=30"
        )
        return list.map { it.toFriend() }
            .sortedWith(
                compareByDescending<FriendProfile> {
                    it.friendCode.equals(q, ignoreCase = true)
                }.thenByDescending {
                    it.email.equals(q, ignoreCase = true)
                }.thenByDescending {
                    it.displayName.equals(q, ignoreCase = true)
                }.thenBy { it.displayName.lowercase() }
            )
    }

    suspend fun sendFriendRequest(targetUserId: String) {
        val me = uid()
        require(targetUserId != me) { "不能添加自己" }
        val existing: List<FriendshipDto> = client.select(
            table = "friendships",
            accessToken = token(),
            query = "or=(and(requester_id.eq.$me,addressee_id.eq.$targetUserId)," +
                "and(requester_id.eq.$targetUserId,addressee_id.eq.$me))&select=*"
        )
        existing.firstOrNull()?.let { row ->
            when (row.status) {
                "accepted" -> error("你们已经是好友了")
                "pending" -> {
                    if (row.requesterId == me) error("已发送过申请，请等待对方处理")
                    else error("对方已向你发起申请，请到「好友」页处理")
                }
                "blocked" -> error("无法添加该用户")
                else -> error("好友关系状态异常：${row.status}")
            }
        }
        val dto = FriendshipDto(
            id = java.util.UUID.randomUUID().toString(),
            requesterId = me,
            addresseeId = targetUserId,
            status = "pending"
        )
        client.insert("friendships", token(), listOf(dto))
    }

    suspend fun respondFriendRequest(friendshipId: String, accept: Boolean) {
        val status = if (accept) "accepted" else "blocked"
        val body = buildJsonObject {
            put("status", status)
            put("updated_at", java.time.Instant.now().toString())
        }
        client.patch(
            table = "friendships",
            accessToken = token(),
            query = "id=eq.$friendshipId",
            bodyJson = client.jsonParser.encodeToString(
                kotlinx.serialization.json.JsonObject.serializer(),
                body
            )
        )
    }

    suspend fun listFriends(): List<FriendProfile> {
        val me = uid()
        val rows: List<FriendshipDto> = client.select(
            table = "friendships",
            accessToken = token(),
            query = "or=(requester_id.eq.$me,addressee_id.eq.$me)&status=eq.accepted&select=*"
        )
        val ids = rows.map { if (it.requesterId == me) it.addresseeId else it.requesterId }.distinct()
        if (ids.isEmpty()) return emptyList()
        return loadProfiles(ids)
    }

    suspend fun listIncomingRequests(): List<FriendRequest> {
        val me = uid()
        val rows: List<FriendshipDto> = client.select(
            table = "friendships",
            accessToken = token(),
            query = "addressee_id=eq.$me&status=eq.pending&select=*&order=created_at.desc"
        )
        val profiles = loadProfiles(rows.map { it.requesterId }.distinct()).associateBy { it.userId }
        return rows.map { row ->
            val from = profiles[row.requesterId] ?: FriendProfile(
                userId = row.requesterId,
                displayName = "未知用户",
                avatarUrl = null,
                friendCode = "",
                email = ""
            )
            FriendRequest(row.id, from, row.addresseeId, row.status)
        }
    }

    suspend fun listGroups(): List<FriendGroup> {
        val me = uid()
        val groups: List<FriendGroupDto> = client.select(
            table = "friend_groups",
            accessToken = token(),
            query = "owner_id=eq.$me&select=*"
        )
        return groups.map { g ->
            val members: List<FriendGroupMemberDto> = client.select(
                table = "friend_group_members",
                accessToken = token(),
                query = "group_id=eq.${g.id}&select=group_id,member_id"
            )
            FriendGroup(
                id = g.id,
                name = g.name,
                memberIds = members.map { it.memberId }
            )
        }
    }

    suspend fun createGroup(name: String, memberIds: List<String>): FriendGroup {
        val me = uid()
        val id = java.util.UUID.randomUUID().toString()
        val group = FriendGroupDto(id = id, ownerId = me, name = name.trim())
        client.insert("friend_groups", token(), listOf(group))
        if (memberIds.isNotEmpty()) {
            val rows = memberIds.map { FriendGroupMemberDto(groupId = id, memberId = it) }
            client.insert("friend_group_members", token(), rows)
        }
        return FriendGroup(id, name.trim(), memberIds)
    }

    suspend fun loadFeed(limit: Int = 12, offset: Int = 0, ownerIds: List<String>? = null): FeedPage {
        val me = uid()
        val owners = ownerIds ?: run {
            val friends = listFriends()
            (friends.map { it.userId } + me).distinct()
        }
        if (owners.isEmpty()) return FeedPage(emptyList(), hasMore = false, ownerIds = owners)
        val inList = owners.joinToString(",")
        // Fetch one extra to know if there is another page.
        val moods: List<MoodEntryDto> = client.select(
            table = "mood_entries",
            accessToken = token(),
            query = "user_id=in.($inList)&deleted_at=is.null&order=created_at.desc" +
                "&limit=${limit + 1}&offset=$offset&select=*"
        )
        val hasMore = moods.size > limit
        val pageMoods = moods.take(limit)
        val profiles = loadProfiles(pageMoods.map { it.userId }.distinct()).associateBy { it.userId }
        val session = authRepository.requireSession()
        val profileMap = profiles + mapOf(
            session.userId to FriendProfile(
                session.userId,
                session.displayName,
                session.avatarUrl,
                session.friendCode,
                session.email
            )
        )
        val items = enrichFeedMoods(pageMoods, profileMap)
        return FeedPage(items = items, hasMore = hasMore, ownerIds = owners)
    }

    suspend fun loadFavorites(limit: Int = 30): List<FeedMood> {
        val me = uid()
        val favs: List<MoodFavoriteDto> = client.select(
            table = "mood_favorites",
            accessToken = token(),
            query = "user_id=eq.$me&select=*&order=created_at.desc&limit=$limit"
        )
        if (favs.isEmpty()) return emptyList()
        val ids = favs.map { it.moodId }.joinToString(",")
        val moods: List<MoodEntryDto> = client.select(
            table = "mood_entries",
            accessToken = token(),
            query = "id=in.($ids)&deleted_at=is.null&select=*"
        )
        val owners = loadProfiles(moods.map { it.userId }.distinct()).associateBy { it.userId }
        return enrichFeedMoods(moods, owners)
    }

    suspend fun toggleLike(moodId: String, liked: Boolean) {
        val me = uid()
        if (liked) {
            client.delete("mood_likes", token(), "mood_id=eq.$moodId&user_id=eq.$me")
        } else {
            client.insert(
                "mood_likes",
                token(),
                listOf(MoodLikeDto(moodId = moodId, userId = me))
            )
        }
    }

    suspend fun toggleFavorite(moodId: String, favorited: Boolean) {
        val me = uid()
        if (favorited) {
            client.delete("mood_favorites", token(), "mood_id=eq.$moodId&user_id=eq.$me")
        } else {
            client.insert(
                "mood_favorites",
                token(),
                listOf(MoodFavoriteDto(moodId = moodId, userId = me))
            )
        }
    }

    suspend fun listComments(moodId: String): List<MoodCommentItem> {
        val comments: List<MoodCommentDto> = client.select(
            table = "mood_comments",
            accessToken = token(),
            query = "mood_id=eq.$moodId&select=*&order=created_at.asc"
        )
        val profiles = loadProfiles(comments.map { it.userId }.distinct()).associateBy { it.userId }
        return comments.map {
            MoodCommentItem(
                id = it.id,
                userId = it.userId,
                userName = profiles[it.userId]?.displayName ?: "用户",
                text = it.text,
                createdAt = TimeFormats.isoToMillis(it.createdAt)
            )
        }
    }

    suspend fun addComment(moodId: String, text: String) {
        val trimmed = text.trim()
        require(trimmed.isNotEmpty()) { "评论不能为空" }
        require(trimmed.length <= 500) { "评论最多 500 字" }
        val dto = MoodCommentDto(
            id = java.util.UUID.randomUUID().toString(),
            moodId = moodId,
            userId = uid(),
            text = trimmed
        )
        client.insert("mood_comments", token(), listOf(dto))
    }

    /**
     * Enrich many moods with only ~4 HTTP calls total (not 4 per mood).
     */
    private suspend fun enrichFeedMoods(
        moods: List<MoodEntryDto>,
        profiles: Map<String, FriendProfile>
    ): List<FeedMood> = coroutineScope {
        if (moods.isEmpty()) return@coroutineScope emptyList()
        val me = uid()
        val access = token()
        val inList = moods.map { it.id }.joinToString(",")

        val imagesDeferred = async {
            client.select<List<MoodImageDto>>(
                table = "mood_images",
                accessToken = access,
                query = "mood_id=in.($inList)&select=*&order=sort_order.asc"
            )
        }
        val likesDeferred = async {
            client.select<List<MoodLikeDto>>(
                table = "mood_likes",
                accessToken = access,
                query = "mood_id=in.($inList)&select=*"
            )
        }
        val commentsDeferred = async {
            client.select<List<MoodIdRefDto>>(
                table = "mood_comments",
                accessToken = access,
                query = "mood_id=in.($inList)&select=mood_id,id"
            )
        }
        val favsDeferred = async {
            client.select<List<MoodFavoriteDto>>(
                table = "mood_favorites",
                accessToken = access,
                query = "mood_id=in.($inList)&user_id=eq.$me&select=*"
            )
        }

        val imagesByMood = imagesDeferred.await().groupBy { it.moodId }
        val likesByMood = likesDeferred.await().groupBy { it.moodId }
        val commentCountByMood = commentsDeferred.await().groupingBy { it.moodId }.eachCount()
        val favMoodIds = favsDeferred.await().map { it.moodId }.toHashSet()

        moods.map { mood ->
            val likes = likesByMood[mood.id].orEmpty()
            val profile = profiles[mood.userId]
            FeedMood(
                syncId = mood.id,
                ownerId = mood.userId,
                ownerName = profile?.displayName ?: "好友",
                ownerAvatarUrl = profile?.avatarUrl,
                date = mood.date,
                text = mood.text,
                emoji = mood.emoji,
                emojiLabel = mood.emojiLabel,
                imageUrls = imagesByMood[mood.id].orEmpty().map { client.publicUrl(it.remotePath) },
                createdAt = TimeFormats.isoToMillis(mood.createdAt),
                likeCount = likes.size,
                commentCount = commentCountByMood[mood.id] ?: 0,
                likedByMe = likes.any { it.userId == me },
                favoritedByMe = mood.id in favMoodIds
            )
        }
    }

    private suspend fun loadProfiles(ids: List<String>): List<FriendProfile> {
        if (ids.isEmpty()) return emptyList()
        val inList = ids.joinToString(",")
        val list: List<ProfileDto> = client.select(
            table = "profiles",
            accessToken = token(),
            query = "id=in.($inList)&select=*"
        )
        return list.map { it.toFriend() }
    }

    private fun ProfileDto.toFriend() = FriendProfile(
        userId = id,
        displayName = displayName.ifBlank { friendCode },
        avatarUrl = avatarUrl,
        friendCode = friendCode,
        email = email.orEmpty()
    )
}
