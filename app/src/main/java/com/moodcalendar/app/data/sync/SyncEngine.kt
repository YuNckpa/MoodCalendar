package com.moodcalendar.app.data.sync

import com.moodcalendar.app.data.auth.AuthRepository
import com.moodcalendar.app.data.local.CalendarEventEntity
import com.moodcalendar.app.data.local.MoodEntryEntity
import com.moodcalendar.app.data.local.MoodImageEntity
import com.moodcalendar.app.data.model.CalendarSystem
import com.moodcalendar.app.data.model.DisplayMode
import com.moodcalendar.app.data.model.EventType
import com.moodcalendar.app.data.model.MoodVisibility
import com.moodcalendar.app.data.model.Recurrence
import com.moodcalendar.app.data.model.ThemeStyle
import com.moodcalendar.app.data.model.YearlyMode
import com.moodcalendar.app.data.preferences.SettingsRepository
import com.moodcalendar.app.data.remote.CalendarEventDto
import com.moodcalendar.app.data.remote.MoodEntryDto
import com.moodcalendar.app.data.remote.MoodImageDto
import com.moodcalendar.app.data.remote.SupabaseClient
import com.moodcalendar.app.data.remote.UserSettingsDto
import com.moodcalendar.app.data.repository.EventRepository
import com.moodcalendar.app.data.repository.MoodRepository
import com.moodcalendar.app.data.repository.toDomain
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.util.UUID

class SyncEngine(
    private val client: SupabaseClient,
    private val authRepository: AuthRepository,
    private val moodRepository: MoodRepository,
    private val eventRepository: EventRepository,
    private val settingsRepository: SettingsRepository
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()
    private val _state = MutableStateFlow(SyncState())
    val state: StateFlow<SyncState> = _state.asStateFlow()

    /** Fire-and-forget sync on app-scoped coroutine (survives screen leave). */
    fun syncInBackground(silent: Boolean = true) {
        scope.launch {
            runCatching { syncAll(silent = silent) }
                .onFailure { e ->
                    if (e is CancellationException) throw e
                }
        }
    }

    fun requestIncrementalSync() {
        syncInBackground(silent = true)
    }

    /** After cold start: wait briefly for persisted session then sync. */
    fun syncAfterAppStart() {
        scope.launch {
            val session = withTimeoutOrNull(3_000) {
                authRepository.session.first { it != null }
            } ?: return@launch
            runCatching { syncAll(silent = true) }
                .onFailure { e -> if (e is CancellationException) throw e }
        }
    }

    suspend fun syncAll(silent: Boolean = false) = mutex.withLock {
        if (!client.isConfigured) {
            if (!silent) {
                _state.value = SyncState(SyncStatus.Error, "未配置云端服务")
            }
            return
        }
        if (!authRepository.isLoggedIn()) {
            if (!silent) {
                _state.value = SyncState(SyncStatus.Idle, "未登录")
            }
            return
        }
        _state.value = SyncState(
            status = SyncStatus.Syncing,
            message = if (silent) "后台同步中…" else "同步中…",
            lastSyncedAt = _state.value.lastSyncedAt
        )
        try {
            val token = authRepository.validAccessToken()
            val userId = authRepository.requireSession().userId
            // Settings first (tiny), then events & moods can run sequentially but skip heavy re-uploads.
            pushSettings(token, userId)
            pullSettings(token, userId)
            pushEvents(token, userId)
            pullEvents(token, userId)
            pushMoods(token, userId)
            pullMoods(token, userId)
            _state.value = SyncState(
                status = SyncStatus.Success,
                message = if (silent) "已在后台同步完成" else "同步完成",
                lastSyncedAt = System.currentTimeMillis()
            )
        } catch (e: CancellationException) {
            // Keep last success/idle; do not surface "Job was cancelled"
            if (_state.value.status == SyncStatus.Syncing) {
                _state.value = SyncState(
                    status = SyncStatus.Idle,
                    message = "同步已暂停",
                    lastSyncedAt = _state.value.lastSyncedAt
                )
            }
            throw e
        } catch (e: Exception) {
            _state.value = SyncState(
                status = SyncStatus.Error,
                message = friendlySyncError(e),
                lastSyncedAt = _state.value.lastSyncedAt
            )
            if (!silent) throw e
        }
    }

    private fun friendlySyncError(e: Throwable): String {
        val raw = e.message.orEmpty()
        val lower = raw.lowercase()
        return when {
            "Job was cancelled" in raw || e is CancellationException -> "同步已取消"
            "Unable to resolve host" in raw || "failed to connect" in lower ||
                "timeout" in lower || "network unreachable" in lower ->
                "网络异常，同步失败"

            "mood_images" in lower && "null value" in lower ->
                "同步图片失败，请稍后重试"

            // Supabase Storage / free-tier quota style errors
            "exceed_storage_size_quota" in lower ||
                "storage" in lower && ("quota" in lower || "exceed" in lower || "limit" in lower) ||
                "payload too large" in lower ||
                "entity too large" in lower ||
                "413" in lower ||
                "402" in lower ||
                "disk full" in lower ||
                "insufficient storage" in lower ||
                "maximum storage" in lower ||
                "space left" in lower ->
                "云端存储空间可能已满或单文件过大，请到 Supabase → Storage 查看用量后清理或扩容"

            "row-level security" in lower || "permission denied" in lower || "42501" in lower ->
                "云端权限不足（RLS），请检查登录状态与策略"

            "jwt" in lower || "expired" in lower || "not authenticated" in lower ->
                "登录已过期，请重新登录后再同步"

            "rate limit" in lower || "too many requests" in lower ->
                "请求过于频繁，请稍后再试"

            raw.isBlank() -> "同步失败，请稍后重试"
            raw.any { it in '\u4e00'..'\u9fff' } -> raw
            // Keep a short original hint so Storage/API errors are diagnosable in the UI.
            else -> "同步失败：${raw.take(160)}"
        }
    }

    private suspend fun pushSettings(token: String, userId: String) {
        val local = settingsRepository.snapshot()
        val remoteList: List<UserSettingsDto> = runCatching {
            client.select<List<UserSettingsDto>>(
                table = "user_settings",
                accessToken = token,
                query = "user_id=eq.$userId&select=*"
            )
        }.getOrDefault(emptyList())
        val remote = remoteList.firstOrNull()
        val remoteMs = TimeFormats.isoToMillis(remote?.updatedAt)
        if (remote == null || local.updatedAt >= remoteMs) {
            val dto = UserSettingsDto(
                userId = userId,
                themeStyle = local.themeStyle.name,
                customMoodsJson = local.customMoodsJson,
                updatedAt = TimeFormats.millisToIso(local.updatedAt.coerceAtLeast(1L))
            )
            client.upsert("user_settings", token, listOf(dto), onConflict = "user_id")
        }
    }

    private suspend fun pullSettings(token: String, userId: String) {
        val remoteList: List<UserSettingsDto> = client.select(
            table = "user_settings",
            accessToken = token,
            query = "user_id=eq.$userId&select=*"
        )
        val remote = remoteList.firstOrNull() ?: return
        val local = settingsRepository.snapshot()
        val remoteMs = TimeFormats.isoToMillis(remote.updatedAt)
        if (remoteMs > local.updatedAt) {
            val theme = runCatching { ThemeStyle.valueOf(remote.themeStyle) }.getOrDefault(ThemeStyle.MALE_BLUE)
            settingsRepository.applyFromCloud(theme, remote.customMoodsJson, remoteMs)
        }
    }

    private suspend fun pushEvents(token: String, userId: String) {
        val locals = eventRepository.getAllEntities()
        val rows = locals.map { entity ->
            val syncId = entity.syncId ?: UUID.randomUUID().toString()
            if (entity.syncId == null || entity.ownerId != userId) {
                eventRepository.save(
                    event = entity.toDomain().copy(syncId = syncId, ownerId = userId),
                    ownerIdOverride = userId,
                    notifySync = false
                )
            }
            CalendarEventDto(
                id = syncId,
                userId = userId,
                clientId = entity.id,
                type = entity.type.name,
                title = entity.title,
                note = entity.note,
                targetDate = entity.targetDate,
                allDay = entity.allDay,
                recurrence = entity.recurrence.name,
                remindOnDay = entity.remindOnDay,
                remindTime = entity.remindTime,
                advanceRemindersJson = entity.advanceRemindersJson,
                displayMode = entity.displayMode.name,
                yearlyMode = entity.yearlyMode.name,
                yearlyDatesJson = entity.yearlyDatesJson,
                weeklyDaysJson = entity.weeklyDaysJson,
                monthlyDaysJson = entity.monthlyDaysJson,
                backgroundImageUri = entity.backgroundImageUri,
                calendarSystem = entity.calendarSystem.name,
                lunarYear = entity.lunarYear,
                lunarMonth = entity.lunarMonth,
                lunarDay = entity.lunarDay,
                sortOrder = entity.sortOrder,
                archived = entity.archived,
                createdAt = TimeFormats.millisToIso(entity.createdAt),
                updatedAt = TimeFormats.millisToIso(entity.updatedAt),
                deletedAt = null
            )
        }
        if (rows.isNotEmpty()) {
            rows.chunked(50).forEach { chunk ->
                client.upsert("calendar_events", token, chunk, onConflict = "id")
            }
        }
    }

    private suspend fun pullEvents(token: String, userId: String) {
        val remotes: List<CalendarEventDto> = client.select(
            table = "calendar_events",
            accessToken = token,
            query = "user_id=eq.$userId&select=*&deleted_at=is.null"
        )
        remotes.forEach { dto ->
            if (dto.deletedAt != null) {
                eventRepository.deleteBySyncId(dto.id)
                return@forEach
            }
            val local = eventRepository.getBySyncId(dto.id)
            val remoteMs = TimeFormats.isoToMillis(dto.updatedAt)
            if (local != null && local.updatedAt > remoteMs) return@forEach
            eventRepository.upsertFromCloud(dto.toEntity(local?.id ?: 0L))
        }
    }

    private suspend fun pushMoods(token: String, userId: String) {
        val moods = moodRepository.getAllWithImages()
        moods.forEach { mood ->
            val syncId = mood.syncId ?: UUID.randomUUID().toString()
            val needsImageUpload = mood.images.any { it.remoteUrl.isNullOrBlank() }
            val isNew = mood.syncId.isNullOrBlank()

            val localId = moodRepository.save(
                entry = mood.copy(syncId = syncId, ownerId = userId),
                ownerIdOverride = userId,
                notifySync = false
            )
            val images = moodRepository.getImages(localId)
            val dto = MoodEntryDto(
                id = syncId,
                userId = userId,
                clientId = localId,
                date = mood.date,
                text = mood.text,
                emoji = mood.emoji,
                emojiLabel = mood.emojiLabel,
                visibility = mood.visibility.name,
                isPeriod = mood.isPeriod,
                groupId = null,
                createdAt = TimeFormats.millisToIso(mood.createdAt),
                updatedAt = TimeFormats.millisToIso(System.currentTimeMillis()),
                deletedAt = null
            )
            client.upsert("mood_entries", token, listOf(dto), onConflict = "id")

            // Skip expensive delete+reupload when every image already has a remote URL.
            if (!isNew && !needsImageUpload) return@forEach

            val uploaded = images.mapIndexed { index, img ->
                val remotePath = uploadIfNeeded(token, userId, syncId, img.localUri, img.remoteUrl)
                MoodImageDto(
                    id = UUID.randomUUID().toString(),
                    moodId = syncId,
                    remotePath = remotePath,
                    sortOrder = index
                )
            }
            if (uploaded.isNotEmpty()) {
                // Replace remote image rows only when we actually (re)uploaded paths.
                client.delete("mood_images", token, "mood_id=eq.$syncId")
                client.insert("mood_images", token, uploaded)
                moodRepository.save(
                    entry = mood.copy(
                        id = localId,
                        syncId = syncId,
                        ownerId = userId,
                        images = images.mapIndexed { index, img ->
                            com.moodcalendar.app.data.repository.MoodImage(
                                id = img.id,
                                localUri = img.localUri,
                                sortOrder = index,
                                remoteUrl = uploaded.getOrNull(index)?.let { client.publicUrl(it.remotePath) }
                                    ?: img.remoteUrl
                            )
                        }
                    ),
                    ownerIdOverride = userId,
                    notifySync = false
                )
            }
        }
    }

    private suspend fun pullMoods(token: String, userId: String) {
        val remotes: List<MoodEntryDto> = client.select(
            table = "mood_entries",
            accessToken = token,
            query = "user_id=eq.$userId&select=*&deleted_at=is.null"
        )
        if (remotes.isEmpty()) return

        val toApply = remotes.mapNotNull { dto ->
            val local = moodRepository.getEntityBySyncId(dto.id)
            val remoteMs = TimeFormats.isoToMillis(dto.updatedAt)
            if (local != null && local.updatedAt > remoteMs) null else dto to local
        }
        if (toApply.isEmpty()) return

        val ids = toApply.map { it.first.id }
        val imagesByMood = ids.chunked(40).flatMap { chunk ->
            val inList = chunk.joinToString(",")
            client.select<List<MoodImageDto>>(
                table = "mood_images",
                accessToken = token,
                query = "mood_id=in.($inList)&select=*&order=sort_order.asc"
            )
        }.groupBy { it.moodId }

        toApply.forEach { (dto, local) ->
            val remoteMs = TimeFormats.isoToMillis(dto.updatedAt)
            val images = imagesByMood[dto.id].orEmpty()
            val entity = MoodEntryEntity(
                id = local?.id ?: 0L,
                date = dto.date,
                text = dto.text,
                emoji = dto.emoji,
                emojiLabel = dto.emojiLabel,
                visibility = runCatching { MoodVisibility.valueOf(dto.visibility) }
                    .getOrDefault(MoodVisibility.FRIENDS_ALL),
                isPeriod = dto.isPeriod,
                groupId = null,
                createdAt = TimeFormats.isoToMillis(dto.createdAt).takeIf { it > 0 }
                    ?: System.currentTimeMillis(),
                updatedAt = remoteMs,
                ownerId = dto.userId,
                syncId = dto.id
            )
            val imageEntities = images.map {
                MoodImageEntity(
                    moodEntryId = 0,
                    localUri = client.publicUrl(it.remotePath),
                    sortOrder = it.sortOrder,
                    remoteUrl = client.publicUrl(it.remotePath)
                )
            }
            moodRepository.upsertFromCloud(entity, imageEntities)
        }
    }

    private suspend fun uploadIfNeeded(
        token: String,
        userId: String,
        moodSyncId: String,
        localUri: String,
        existingRemote: String?
    ): String {
        if (!existingRemote.isNullOrBlank() && existingRemote.contains("/mood-images/")) {
            return existingRemote.substringAfter("/mood-images/")
        }
        val path = localUri.removePrefix("file://")
        val file = File(path)
        if (!file.exists()) {
            return existingRemote?.substringAfter("/mood-images/")
                ?: "$userId/$moodSyncId/missing.jpg"
        }
        val url = client.uploadMoodImage(token, userId, moodSyncId, file)
        return url.substringAfter("/mood-images/")
    }

    private fun CalendarEventDto.toEntity(localId: Long) = CalendarEventEntity(
        id = localId,
        type = runCatching { EventType.valueOf(type) }.getOrDefault(EventType.CUSTOM),
        title = title,
        note = note,
        targetDate = targetDate,
        allDay = allDay,
        recurrence = runCatching { Recurrence.valueOf(recurrence) }.getOrDefault(Recurrence.NONE),
        remindOnDay = remindOnDay,
        remindTime = remindTime,
        advanceRemindersJson = advanceRemindersJson,
        displayMode = runCatching { DisplayMode.valueOf(displayMode) }.getOrDefault(DisplayMode.DAYS_ONLY),
        yearlyMode = runCatching { YearlyMode.valueOf(yearlyMode) }.getOrDefault(YearlyMode.SAME_DAY),
        yearlyDatesJson = yearlyDatesJson,
        weeklyDaysJson = weeklyDaysJson,
        monthlyDaysJson = monthlyDaysJson,
        backgroundImageUri = backgroundImageUri,
        calendarSystem = runCatching { CalendarSystem.valueOf(calendarSystem) }
            .getOrDefault(CalendarSystem.SOLAR),
        lunarYear = lunarYear,
        lunarMonth = lunarMonth,
        lunarDay = lunarDay,
        sortOrder = sortOrder,
        archived = archived,
        createdAt = TimeFormats.isoToMillis(createdAt).takeIf { it > 0 } ?: System.currentTimeMillis(),
        updatedAt = TimeFormats.isoToMillis(updatedAt),
        ownerId = userId,
        syncId = id
    )
}
