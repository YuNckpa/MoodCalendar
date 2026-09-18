package com.moodcalendar.app.ui.social

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.moodcalendar.app.data.auth.AuthRepository
import com.moodcalendar.app.data.social.FeedMood
import com.moodcalendar.app.data.social.FriendGroup
import com.moodcalendar.app.data.social.FriendProfile
import com.moodcalendar.app.data.social.FriendRequest
import com.moodcalendar.app.data.social.MoodCommentItem
import com.moodcalendar.app.data.social.SocialRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SocialUiState(
    val loading: Boolean = false,
    val feedLoadingMore: Boolean = false,
    val feedHasMore: Boolean = false,
    val error: String? = null,
    val info: String? = null,
    val feed: List<FeedMood> = emptyList(),
    val friends: List<FriendProfile> = emptyList(),
    val requests: List<FriendRequest> = emptyList(),
    val favorites: List<FeedMood> = emptyList(),
    val groups: List<FriendGroup> = emptyList(),
    val searchInput: String = "",
    val searchResults: List<FriendProfile> = emptyList(),
    val commentsMoodId: String? = null,
    val comments: List<MoodCommentItem> = emptyList(),
    val commentDraft: String = "",
    val newGroupName: String = "",
    val previewImageUrl: String? = null
)

class SocialViewModel(
    private val authRepository: AuthRepository,
    private val socialRepository: SocialRepository
) : ViewModel() {
    private val _ui = MutableStateFlow(SocialUiState())
    val ui: StateFlow<SocialUiState> = _ui.asStateFlow()

    private var feedOwnerIds: List<String> = emptyList()
    private val pageSize = 12

    fun isLoggedIn(): Boolean = authRepository.isLoggedIn()

    fun update(transform: (SocialUiState) -> SocialUiState) = _ui.update(transform)

    fun openImagePreview(url: String) = _ui.update { it.copy(previewImageUrl = url) }
    fun closeImagePreview() = _ui.update { it.copy(previewImageUrl = null) }

    fun refreshAll() {
        if (!isLoggedIn()) return
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    loading = true,
                    error = null,
                    feedHasMore = false,
                    feedLoadingMore = false
                )
            }
            val errors = mutableListOf<String>()

            // Show first feed page ASAP; load other tabs in parallel after.
            runCatching { socialRepository.loadFeed(limit = pageSize, offset = 0) }
                .onSuccess { page ->
                    feedOwnerIds = page.ownerIds
                    _ui.update {
                        it.copy(
                            feed = page.items,
                            feedHasMore = page.hasMore,
                            loading = false
                        )
                    }
                }
                .onFailure { e ->
                    errors += "动态：${e.message ?: "加载失败"}"
                    _ui.update { it.copy(loading = false) }
                }

            coroutineScope {
                val friendsJob = async {
                    runCatching { socialRepository.listFriends() }
                }
                val requestsJob = async {
                    runCatching { socialRepository.listIncomingRequests() }
                }
                val favoritesJob = async {
                    runCatching { socialRepository.loadFavorites() }
                }
                val groupsJob = async {
                    runCatching { socialRepository.listGroups() }
                }

                friendsJob.await()
                    .onSuccess { friends -> _ui.update { it.copy(friends = friends) } }
                    .onFailure { e -> errors += "好友：${e.message ?: "加载失败"}" }
                requestsJob.await()
                    .onSuccess { requests -> _ui.update { it.copy(requests = requests) } }
                    .onFailure { e -> errors += "申请：${e.message ?: "加载失败"}" }
                favoritesJob.await()
                    .onSuccess { favorites -> _ui.update { it.copy(favorites = favorites) } }
                    .onFailure { e -> errors += "收藏：${e.message ?: "加载失败"}" }
                groupsJob.await()
                    .onSuccess { groups -> _ui.update { it.copy(groups = groups) } }
                    .onFailure { e -> errors += "分组：${e.message ?: "加载失败"}" }
            }

            _ui.update {
                it.copy(
                    error = errors.firstOrNull(),
                    info = if (errors.isEmpty() && it.requests.isNotEmpty()) {
                        "有 ${it.requests.size} 条待处理好友申请"
                    } else {
                        it.info
                    }
                )
            }
        }
    }

    fun loadMoreFeed() {
        if (!isLoggedIn()) return
        val state = _ui.value
        if (!state.feedHasMore || state.feedLoadingMore || state.loading) return
        viewModelScope.launch {
            _ui.update { it.copy(feedLoadingMore = true, error = null) }
            runCatching {
                socialRepository.loadFeed(
                    limit = pageSize,
                    offset = _ui.value.feed.size,
                    ownerIds = feedOwnerIds.ifEmpty { null }
                )
            }.onSuccess { page ->
                if (page.ownerIds.isNotEmpty()) feedOwnerIds = page.ownerIds
                _ui.update {
                    it.copy(
                        feedLoadingMore = false,
                        feed = it.feed + page.items,
                        feedHasMore = page.hasMore
                    )
                }
            }.onFailure { e ->
                _ui.update {
                    it.copy(feedLoadingMore = false, error = e.message ?: "加载更多失败")
                }
            }
        }
    }

    fun refreshFriends() {
        if (!isLoggedIn()) return
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null) }
            runCatching {
                val friends = socialRepository.listFriends()
                val requests = socialRepository.listIncomingRequests()
                _ui.update {
                    it.copy(
                        loading = false,
                        friends = friends,
                        requests = requests,
                        info = if (requests.isNotEmpty()) {
                            "有 ${requests.size} 条待处理好友申请"
                        } else {
                            null
                        }
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.message ?: "刷新失败") }
            }
        }
    }

    fun searchFriend() {
        val q = _ui.value.searchInput.trim()
        if (q.isBlank()) {
            _ui.update { it.copy(error = "请输入好友码、昵称或邮箱") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(loading = true, error = null, info = null, searchResults = emptyList()) }
            runCatching {
                socialRepository.searchUsers(q)
            }.onSuccess { found ->
                _ui.update {
                    it.copy(
                        loading = false,
                        searchResults = found,
                        error = if (found.isEmpty()) "未找到匹配的用户" else null,
                        info = if (found.isNotEmpty()) "找到 ${found.size} 人" else null
                    )
                }
            }.onFailure { e ->
                _ui.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    fun sendRequest(userId: String) {
        viewModelScope.launch {
            runCatching { socialRepository.sendFriendRequest(userId) }
                .onSuccess {
                    _ui.update {
                        it.copy(
                            error = null,
                            info = "好友申请已发送",
                            searchResults = it.searchResults.filterNot { u -> u.userId == userId }
                        )
                    }
                    refreshFriends()
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun respond(requestId: String, accept: Boolean) {
        viewModelScope.launch {
            runCatching { socialRepository.respondFriendRequest(requestId, accept) }
                .onSuccess { refreshFriends() }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun toggleLike(mood: FeedMood) {
        viewModelScope.launch {
            runCatching { socialRepository.toggleLike(mood.syncId, mood.likedByMe) }
                .onSuccess {
                    _ui.update { state ->
                        state.copy(
                            feed = state.feed.map { patchLike(it, mood.syncId) },
                            favorites = state.favorites.map { patchLike(it, mood.syncId) }
                        )
                    }
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun toggleFavorite(mood: FeedMood) {
        viewModelScope.launch {
            runCatching { socialRepository.toggleFavorite(mood.syncId, mood.favoritedByMe) }
                .onSuccess {
                    _ui.update { state ->
                        val updatedFeed = state.feed.map { patchFavorite(it, mood.syncId) }
                        state.copy(
                            feed = updatedFeed,
                            favorites = if (mood.favoritedByMe) {
                                state.favorites.filterNot { it.syncId == mood.syncId }
                            } else {
                                state.favorites
                            }
                        )
                    }
                    if (!mood.favoritedByMe) {
                        runCatching { socialRepository.loadFavorites() }
                            .onSuccess { favs -> _ui.update { it.copy(favorites = favs) } }
                    }
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun openComments(moodId: String) {
        if (_ui.value.commentsMoodId == moodId) {
            closeComments()
            return
        }
        viewModelScope.launch {
            _ui.update {
                it.copy(
                    commentsMoodId = moodId,
                    commentDraft = "",
                    comments = emptyList(),
                    error = null
                )
            }
            runCatching { socialRepository.listComments(moodId) }
                .onSuccess { list -> _ui.update { it.copy(comments = list) } }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun closeComments() {
        _ui.update {
            it.copy(commentsMoodId = null, comments = emptyList(), commentDraft = "")
        }
    }

    fun sendComment() {
        val moodId = _ui.value.commentsMoodId ?: return
        val text = _ui.value.commentDraft
        if (text.isBlank()) return
        viewModelScope.launch {
            runCatching { socialRepository.addComment(moodId, text) }
                .onSuccess {
                    _ui.update { state ->
                        state.copy(
                            commentDraft = "",
                            feed = state.feed.map {
                                if (it.syncId == moodId) it.copy(commentCount = it.commentCount + 1) else it
                            },
                            favorites = state.favorites.map {
                                if (it.syncId == moodId) it.copy(commentCount = it.commentCount + 1) else it
                            }
                        )
                    }
                    runCatching { socialRepository.listComments(moodId) }
                        .onSuccess { list -> _ui.update { it.copy(comments = list, error = null) } }
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun createGroup(memberIds: List<String>) {
        val name = _ui.value.newGroupName
        viewModelScope.launch {
            runCatching { socialRepository.createGroup(name, memberIds) }
                .onSuccess {
                    _ui.update { it.copy(newGroupName = "") }
                    runCatching { socialRepository.listGroups() }
                        .onSuccess { groups -> _ui.update { it.copy(groups = groups) } }
                }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    private fun patchLike(mood: FeedMood, syncId: String): FeedMood {
        if (mood.syncId != syncId) return mood
        return if (mood.likedByMe) {
            mood.copy(likedByMe = false, likeCount = (mood.likeCount - 1).coerceAtLeast(0))
        } else {
            mood.copy(likedByMe = true, likeCount = mood.likeCount + 1)
        }
    }

    private fun patchFavorite(mood: FeedMood, syncId: String): FeedMood {
        if (mood.syncId != syncId) return mood
        return mood.copy(favoritedByMe = !mood.favoritedByMe)
    }

    companion object {
        fun factory(authRepository: AuthRepository, socialRepository: SocialRepository) =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return SocialViewModel(authRepository, socialRepository) as T
                }
            }
    }
}
