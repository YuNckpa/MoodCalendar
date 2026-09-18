package com.moodcalendar.app.data.sync

enum class SyncStatus {
    Idle,
    Syncing,
    Success,
    Error
}

data class SyncState(
    val status: SyncStatus = SyncStatus.Idle,
    val message: String = "",
    val lastSyncedAt: Long? = null
)
