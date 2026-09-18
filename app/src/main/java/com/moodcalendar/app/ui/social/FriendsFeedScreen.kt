package com.moodcalendar.app.ui.social

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.outlined.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusEvent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.moodcalendar.app.data.social.FeedMood
import com.moodcalendar.app.data.social.MoodCommentItem
import com.moodcalendar.app.ui.components.UserAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class SocialTab { Feed, Friends, Favorites, Groups }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsFeedScreen(
    viewModel: SocialViewModel,
    onBack: () -> Unit,
    onNeedLogin: () -> Unit
) {
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(SocialTab.Feed) }

    LaunchedEffect(Unit) {
        if (!viewModel.isLoggedIn()) {
            onNeedLogin()
        } else {
            viewModel.refreshAll()
        }
    }

    LaunchedEffect(tab) {
        if (tab != SocialTab.Feed && tab != SocialTab.Favorites) {
            viewModel.closeComments()
        }
        if (tab == SocialTab.Friends && viewModel.isLoggedIn()) {
            viewModel.refreshFriends()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("好友与动态") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            if (tab == SocialTab.Friends) viewModel.refreshFriends()
                            else viewModel.refreshAll()
                        },
                        enabled = !ui.loading
                    ) {
                        Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SocialTab.entries.forEach { t ->
                    FilterChip(
                        selected = tab == t,
                        onClick = { tab = t },
                        label = {
                            Text(
                                when (t) {
                                    SocialTab.Feed -> "动态"
                                    SocialTab.Friends -> {
                                        if (ui.requests.isNotEmpty()) {
                                            "好友(${ui.requests.size})"
                                        } else {
                                            "好友"
                                        }
                                    }
                                    SocialTab.Favorites -> "收藏"
                                    SocialTab.Groups -> "分组"
                                }
                            )
                        }
                    )
                }
            }
            ui.error?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.error)
            }
            ui.info?.let { msg ->
                Text(text = msg, color = MaterialTheme.colorScheme.primary)
            }
            when (tab) {
                SocialTab.Feed -> FeedList(
                    modifier = Modifier.weight(1f),
                    items = ui.feed,
                    loading = ui.loading,
                    loadingMore = ui.feedLoadingMore,
                    hasMore = ui.feedHasMore,
                    expandedMoodId = ui.commentsMoodId,
                    comments = ui.comments,
                    commentDraft = ui.commentDraft,
                    onLike = viewModel::toggleLike,
                    onFavorite = viewModel::toggleFavorite,
                    onToggleComments = viewModel::openComments,
                    onDraftChange = { v -> viewModel.update { it.copy(commentDraft = v) } },
                    onSendComment = viewModel::sendComment,
                    onLoadMore = viewModel::loadMoreFeed,
                    onImageClick = viewModel::openImagePreview
                )
                SocialTab.Friends -> FriendsPanel(
                    modifier = Modifier.weight(1f),
                    ui = ui,
                    viewModel = viewModel
                )
                SocialTab.Favorites -> FeedList(
                    modifier = Modifier.weight(1f),
                    items = ui.favorites,
                    loading = ui.loading,
                    loadingMore = false,
                    hasMore = false,
                    expandedMoodId = ui.commentsMoodId,
                    comments = ui.comments,
                    commentDraft = ui.commentDraft,
                    onLike = viewModel::toggleLike,
                    onFavorite = viewModel::toggleFavorite,
                    onToggleComments = viewModel::openComments,
                    onDraftChange = { v -> viewModel.update { it.copy(commentDraft = v) } },
                    onSendComment = viewModel::sendComment,
                    onLoadMore = {},
                    onImageClick = viewModel::openImagePreview
                )
                SocialTab.Groups -> GroupsPanel(
                    modifier = Modifier.weight(1f),
                    ui = ui,
                    viewModel = viewModel
                )
            }
        }
    }

    ui.previewImageUrl?.let { url ->
        ImagePreviewDialog(url = url, onDismiss = viewModel::closeImagePreview)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FeedList(
    modifier: Modifier = Modifier,
    items: List<FeedMood>,
    loading: Boolean,
    loadingMore: Boolean,
    hasMore: Boolean,
    expandedMoodId: String?,
    comments: List<MoodCommentItem>,
    commentDraft: String,
    onLike: (FeedMood) -> Unit,
    onFavorite: (FeedMood) -> Unit,
    onToggleComments: (String) -> Unit,
    onDraftChange: (String) -> Unit,
    onSendComment: () -> Unit,
    onLoadMore: () -> Unit,
    onImageClick: (String) -> Unit
) {
    if (loading && items.isEmpty()) {
        Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    if (items.isEmpty()) {
        Text(
            "暂无动态。添加好友并分享可见心情后会出现在这里。",
            modifier = modifier
        )
        return
    }

    val listState = rememberLazyListState()
    val imeVisible = WindowInsets.isImeVisible
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    val shouldLoadMore by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            val last = info.visibleItemsInfo.lastOrNull()?.index ?: 0
            hasMore && !loadingMore && last >= items.lastIndex - 2
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }

    LaunchedEffect(expandedMoodId, comments.size, imeBottomPx) {
        val moodId = expandedMoodId ?: return@LaunchedEffect
        val index = items.indexOfFirst { it.syncId == moodId }
        if (index < 0) return@LaunchedEffect
        delay(if (imeVisible || imeBottomPx > 0) 100 else 50)
        listState.animateScrollToItem(index)
        delay(16)
        val itemInfo = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
        if (itemInfo != null) {
            val overflow = (itemInfo.offset + itemInfo.size) - listState.layoutInfo.viewportEndOffset
            if (overflow > 0) {
                listState.animateScrollBy(overflow.toFloat())
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(items, key = { it.syncId }) { mood ->
            val expanded = expandedMoodId == mood.syncId
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        UserAvatar(avatarUrl = mood.ownerAvatarUrl, size = 36.dp)
                        Text(
                            "${mood.emoji} ${mood.ownerName} · ${mood.date}",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                    if (mood.emojiLabel.isNotBlank()) {
                        Text(mood.emojiLabel, style = MaterialTheme.typography.labelMedium)
                    }
                    if (mood.text.isNotBlank()) {
                        Text(mood.text, style = MaterialTheme.typography.bodyMedium)
                    }
                    if (mood.imageUrls.isNotEmpty()) {
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(mood.imageUrls) { url ->
                                AsyncImage(
                                    model = url,
                                    contentDescription = "动态图片",
                                    modifier = Modifier
                                        .size(96.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onImageClick(url) },
                                    contentScale = ContentScale.Crop
                                )
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onLike(mood) }) {
                            Icon(
                                if (mood.likedByMe) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = "点赞"
                            )
                        }
                        Text("${mood.likeCount}")
                        IconButton(onClick = { onToggleComments(mood.syncId) }) {
                            Icon(
                                Icons.Outlined.ChatBubbleOutline,
                                contentDescription = "评论",
                                tint = if (expanded) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                        Text("${mood.commentCount}")
                        IconButton(onClick = { onFavorite(mood) }) {
                            Icon(
                                if (mood.favoritedByMe) Icons.Outlined.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "收藏"
                            )
                        }
                    }
                    if (expanded) {
                        InlineCommentPanel(
                            comments = comments,
                            draft = commentDraft,
                            onDraftChange = onDraftChange,
                            onSend = onSendComment
                        )
                    }
                }
            }
        }
        if (loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp))
                }
            }
        }
    }
}

@Composable
private fun ImagePreviewDialog(url: String, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.94f))
                .clickable(onClick = onDismiss)
        ) {
            AsyncImage(
                model = url,
                contentDescription = "图片预览",
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                contentScale = ContentScale.Fit
            )
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "关闭",
                    tint = Color.White
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun InlineCommentPanel(
    comments: List<MoodCommentItem>,
    draft: String,
    onDraftChange: (String) -> Unit,
    onSend: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    val bringIntoViewRequester = remember { BringIntoViewRequester() }
    val scope = rememberCoroutineScope()
    val imeVisible = WindowInsets.isImeVisible

    LaunchedEffect(Unit) {
        delay(50)
        focusRequester.requestFocus()
        delay(120)
        bringIntoViewRequester.bringIntoView()
    }

    LaunchedEffect(imeVisible, comments.size) {
        if (imeVisible) {
            delay(100)
            bringIntoViewRequester.bringIntoView()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (comments.isEmpty()) {
            Text(
                "暂无评论，来说两句吧",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            comments.forEach { c ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = c.userName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = c.text,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .bringIntoViewRequester(bringIntoViewRequester),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            OutlinedTextField(
                value = draft,
                onValueChange = onDraftChange,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(focusRequester)
                    .onFocusEvent { state ->
                        if (state.isFocused) {
                            scope.launch {
                                delay(120)
                                bringIntoViewRequester.bringIntoView()
                            }
                        }
                    },
                placeholder = { Text("写评论…") },
                singleLine = true,
                maxLines = 1,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = { if (draft.isNotBlank()) onSend() }
                )
            )
            IconButton(
                onClick = onSend,
                enabled = draft.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Outlined.Send,
                    contentDescription = "发送",
                    tint = if (draft.isNotBlank()) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@Composable
private fun FriendsPanel(
    modifier: Modifier = Modifier,
    ui: SocialUiState,
    viewModel: SocialViewModel
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "当前没有推送通知。对方需打开「好友与动态 → 好友」并刷新才能看到申请。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text("待处理申请", style = MaterialTheme.typography.titleMedium)
        if (ui.requests.isEmpty()) {
            Text(
                text = if (ui.loading) "正在刷新…" else "暂无待处理申请",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            ui.requests.forEach { req ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatar(avatarUrl = req.fromUser.avatarUrl, size = 36.dp)
                        Column {
                            Text(req.fromUser.displayName)
                            if (req.fromUser.friendCode.isNotBlank()) {
                                Text(
                                    req.fromUser.friendCode,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Row {
                        TextButton(onClick = { viewModel.respond(req.friendshipId, true) }) { Text("接受") }
                        TextButton(onClick = { viewModel.respond(req.friendshipId, false) }) { Text("拒绝") }
                    }
                }
            }
        }

        Text("添加好友", style = MaterialTheme.typography.titleMedium)
        Text(
            text = "支持好友码、昵称、邮箱模糊搜索",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        OutlinedTextField(
            value = ui.searchInput,
            onValueChange = { v -> viewModel.update { it.copy(searchInput = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("好友码 / 昵称 / 邮箱") },
            singleLine = true
        )
        Button(onClick = viewModel::searchFriend, enabled = !ui.loading) {
            Text(if (ui.loading) "搜索中…" else "搜索")
        }
        if (ui.searchResults.isNotEmpty()) {
            Text("搜索结果", style = MaterialTheme.typography.titleSmall)
            ui.searchResults.forEach { user ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        UserAvatar(avatarUrl = user.avatarUrl, size = 40.dp)
                        Column {
                            Text(user.displayName, style = MaterialTheme.typography.titleSmall)
                            Text(
                                buildString {
                                    append(user.friendCode)
                                    if (user.email.isNotBlank()) append(" · ${user.email}")
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    OutlinedButton(onClick = { viewModel.sendRequest(user.userId) }) {
                        Text("申请")
                    }
                }
            }
        }

        Text("我的好友", style = MaterialTheme.typography.titleMedium)
        if (ui.friends.isEmpty()) {
            Text("还没有好友")
        } else {
            ui.friends.forEach { f ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    UserAvatar(avatarUrl = f.avatarUrl, size = 36.dp)
                    Text("${f.displayName} · ${f.friendCode}")
                }
            }
        }
    }
}

@Composable
private fun GroupsPanel(
    modifier: Modifier = Modifier,
    ui: SocialUiState,
    viewModel: SocialViewModel
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text("好友分组（用于「指定分组」可见性）", style = MaterialTheme.typography.titleMedium)
        OutlinedTextField(
            value = ui.newGroupName,
            onValueChange = { v -> viewModel.update { it.copy(newGroupName = v) } },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("新分组名称") },
            singleLine = true
        )
        Button(
            onClick = { viewModel.createGroup(ui.friends.map { it.userId }) },
            enabled = ui.newGroupName.isNotBlank()
        ) { Text("创建分组（默认加入全部好友）") }
        if (ui.groups.isEmpty()) {
            Text("暂无分组。创建后可在心情编辑中选择「指定分组」。")
        } else {
            ui.groups.forEach { g ->
                Text("${g.name}（${g.memberIds.size} 人）")
            }
        }
    }
}
