package com.moodcalendar.app.data.repository

import com.moodcalendar.app.data.local.MoodEntryDao
import com.moodcalendar.app.data.local.MoodEntryEntity
import com.moodcalendar.app.data.local.MoodImageDao
import com.moodcalendar.app.data.local.MoodImageEntity
import com.moodcalendar.app.data.model.MoodVisibility
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import java.util.UUID

data class MoodImage(
    val id: Long = 0,
    val localUri: String,
    val sortOrder: Int = 0,
    val remoteUrl: String? = null
)

data class MoodEntry(
    val id: Long = 0,
    val date: String,
    val text: String = "",
    val emoji: String = "😊",
    val emojiLabel: String = "",
    val visibility: MoodVisibility = MoodVisibility.FRIENDS_ALL,
    val isPeriod: Boolean = false,
    val groupId: Long? = null,
    val images: List<MoodImage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val ownerId: String? = null,
    val syncId: String? = null
)

class MoodRepository(
    private val entryDao: MoodEntryDao,
    private val imageDao: MoodImageDao,
    private var onLocalChanged: (suspend () -> Unit)? = null
) {
    fun setOnLocalChanged(listener: (suspend () -> Unit)?) {
        onLocalChanged = listener
    }

    fun observeAll(): Flow<List<MoodEntryEntity>> = entryDao.observeAll()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeAllWithImages(): Flow<List<MoodEntry>> =
        entryDao.observeAll().mapLatest { entries ->
            entries.map { entity ->
                val images = imageDao.getForEntry(entity.id).map {
                    MoodImage(it.id, it.localUri, it.sortOrder, it.remoteUrl)
                }
                entity.toDomain(images)
            }
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun observeByDate(date: String): Flow<List<MoodEntry>> =
        entryDao.observeByDate(date).mapLatest { entries ->
            entries.map { entity ->
                val images = imageDao.getForEntry(entity.id).map {
                    MoodImage(it.id, it.localUri, it.sortOrder, it.remoteUrl)
                }
                entity.toDomain(images)
            }
        }

    fun observeDatesWithMood(): Flow<Set<String>> =
        entryDao.observeDatesWithMood().map { it.toSet() }

    fun observeDatesWithPeriod(): Flow<Set<String>> =
        entryDao.observeDatesWithPeriod().map { it.toSet() }

    fun observeMoodCoverByDate(): Flow<Map<String, String>> =
        entryDao.observeMoodCoverImages().map { rows ->
            val map = linkedMapOf<String, String>()
            rows.forEach { row ->
                val uri = row.localUri
                if (!uri.isNullOrBlank() && row.date !in map) {
                    map[row.date] = uri
                }
            }
            map
        }

    suspend fun getById(id: Long): MoodEntry? {
        val entity = entryDao.getById(id) ?: return null
        val images = imageDao.getForEntry(id).map {
            MoodImage(it.id, it.localUri, it.sortOrder, it.remoteUrl)
        }
        return entity.toDomain(images)
    }

    suspend fun getAllWithImages(): List<MoodEntry> =
        entryDao.getAll().map { entity ->
            val images = imageDao.getForEntry(entity.id).map {
                MoodImage(it.id, it.localUri, it.sortOrder, it.remoteUrl)
            }
            entity.toDomain(images)
        }

    suspend fun getEntityBySyncId(syncId: String): MoodEntryEntity? = entryDao.getBySyncId(syncId)

    suspend fun save(
        entry: MoodEntry,
        ownerIdOverride: String? = null,
        notifySync: Boolean = true
    ): Long {
        val now = System.currentTimeMillis()
        val existing = if (entry.id != 0L) entryDao.getById(entry.id) else null
        val syncId = entry.syncId ?: existing?.syncId ?: UUID.randomUUID().toString()
        val ownerId = ownerIdOverride ?: entry.ownerId ?: existing?.ownerId
        val entity = MoodEntryEntity(
            id = entry.id,
            date = entry.date,
            text = entry.text,
            emoji = entry.emoji,
            emojiLabel = entry.emojiLabel,
            visibility = entry.visibility,
            isPeriod = entry.isPeriod,
            groupId = entry.groupId,
            createdAt = if (entry.id == 0L) now else entry.createdAt,
            updatedAt = now,
            ownerId = ownerId,
            syncId = syncId
        )
        val id = if (entity.id == 0L) {
            entryDao.insert(entity)
        } else {
            entryDao.update(entity)
            entity.id
        }
        imageDao.deleteForEntry(id)
        if (entry.images.isNotEmpty()) {
            imageDao.insertAll(
                entry.images.mapIndexed { index, img ->
                    MoodImageEntity(
                        id = 0,
                        moodEntryId = id,
                        localUri = img.localUri,
                        sortOrder = index,
                        remoteUrl = img.remoteUrl
                    )
                }
            )
        }
        if (notifySync) runCatching { onLocalChanged?.invoke() }
        return id
    }

    suspend fun upsertFromCloud(entity: MoodEntryEntity, images: List<MoodImageEntity>) {
        val existing = entity.syncId?.let { entryDao.getBySyncId(it) }
        val localId = if (existing != null) {
            entryDao.update(entity.copy(id = existing.id))
            existing.id
        } else {
            entryDao.insert(entity.copy(id = 0))
        }
        imageDao.deleteForEntry(localId)
        if (images.isNotEmpty()) {
            imageDao.insertAll(images.map { it.copy(id = 0, moodEntryId = localId) })
        }
    }

    suspend fun delete(id: Long, notifySync: Boolean = true) {
        val existing = entryDao.getById(id)
        imageDao.deleteForEntry(id)
        entryDao.deleteById(id)
        if (notifySync) runCatching { onLocalChanged?.invoke() }
        return Unit.also { existing }
    }

    suspend fun deleteBySyncId(syncId: String) {
        val existing = entryDao.getBySyncId(syncId) ?: return
        imageDao.deleteForEntry(existing.id)
        entryDao.deleteById(existing.id)
    }

    suspend fun getImages(entryId: Long): List<MoodImageEntity> = imageDao.getForEntry(entryId)
}

private fun MoodEntryEntity.toDomain(images: List<MoodImage>) = MoodEntry(
    id = id,
    date = date,
    text = text,
    emoji = emoji,
    emojiLabel = emojiLabel,
    visibility = visibility,
    isPeriod = isPeriod,
    groupId = groupId,
    images = images,
    createdAt = createdAt,
    updatedAt = updatedAt,
    ownerId = ownerId,
    syncId = syncId
)
