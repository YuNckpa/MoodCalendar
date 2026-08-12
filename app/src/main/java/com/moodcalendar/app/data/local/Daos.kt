package com.moodcalendar.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CalendarEventDao {
    @Query("SELECT * FROM calendar_events WHERE archived = 0 ORDER BY targetDate ASC")
    fun observeActive(): Flow<List<CalendarEventEntity>>

    @Query("SELECT * FROM calendar_events WHERE archived = 0 ORDER BY targetDate ASC")
    suspend fun getActive(): List<CalendarEventEntity>

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    suspend fun getById(id: Long): CalendarEventEntity?

    @Query("SELECT * FROM calendar_events WHERE id = :id")
    fun observeById(id: Long): Flow<CalendarEventEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: CalendarEventEntity): Long

    @Update
    suspend fun update(event: CalendarEventEntity)

    @Update
    suspend fun updateAll(events: List<CalendarEventEntity>)

    @Delete
    suspend fun delete(event: CalendarEventEntity)

    @Query("DELETE FROM calendar_events WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MoodEntryDao {
    @Query("SELECT * FROM mood_entries ORDER BY date DESC, createdAt DESC")
    fun observeAll(): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE date = :date ORDER BY createdAt DESC")
    fun observeByDate(date: String): Flow<List<MoodEntryEntity>>

    @Query("SELECT * FROM mood_entries WHERE id = :id")
    suspend fun getById(id: Long): MoodEntryEntity?

    @Query("SELECT DISTINCT date FROM mood_entries WHERE date BETWEEN :start AND :end")
    suspend fun datesWithMood(start: String, end: String): List<String>

    @Query("SELECT DISTINCT date FROM mood_entries")
    fun observeDatesWithMood(): Flow<List<String>>

    @Query("SELECT DISTINCT date FROM mood_entries WHERE isPeriod = 1")
    fun observeDatesWithPeriod(): Flow<List<String>>

    /**
     * Dates that have at least one mood image, with the first image of the newest mood that day.
     */
    @Query(
        """
        SELECT me.date AS date, (
            SELECT mi.localUri FROM mood_images mi
            WHERE mi.moodEntryId = me.id
            ORDER BY mi.sortOrder ASC
            LIMIT 1
        ) AS localUri
        FROM mood_entries me
        WHERE EXISTS (
            SELECT 1 FROM mood_images mi WHERE mi.moodEntryId = me.id
        )
        ORDER BY me.createdAt DESC
        """
    )
    fun observeMoodCoverImages(): Flow<List<MoodDateCover>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: MoodEntryEntity): Long

    @Update
    suspend fun update(entry: MoodEntryEntity)

    @Query("DELETE FROM mood_entries WHERE id = :id")
    suspend fun deleteById(id: Long)
}

@Dao
interface MoodImageDao {
    @Query("SELECT * FROM mood_images WHERE moodEntryId = :entryId ORDER BY sortOrder ASC")
    fun observeForEntry(entryId: Long): Flow<List<MoodImageEntity>>

    @Query("SELECT * FROM mood_images WHERE moodEntryId = :entryId ORDER BY sortOrder ASC")
    suspend fun getForEntry(entryId: Long): List<MoodImageEntity>

    @Query("SELECT * FROM mood_images WHERE moodEntryId IN (:entryIds) ORDER BY sortOrder ASC")
    suspend fun getForEntries(entryIds: List<Long>): List<MoodImageEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(images: List<MoodImageEntity>)

    @Query("DELETE FROM mood_images WHERE moodEntryId = :entryId")
    suspend fun deleteForEntry(entryId: Long)
}
