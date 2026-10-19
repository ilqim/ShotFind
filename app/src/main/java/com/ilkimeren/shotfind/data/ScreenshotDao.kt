package com.ilkimeren.shotfind.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScreenshotDao {

    @Query("SELECT * FROM screenshots WHERE relativePath IN (:folders) ORDER BY capturedAt DESC")
    abstract fun observeByFolders(folders: List<String>): Flow<List<ScreenshotEntity>>

    @Query("SELECT COUNT(*) FROM screenshots")
    abstract fun observeCount(): Flow<Int>

    @Query("SELECT mediaId, relativePath, displayName FROM screenshots")
    abstract suspend fun getAllLocations(): List<ScreenshotLocation>

    // IGNORE keeps existing rows untouched, so notes and OCR text are never overwritten
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insertAll(items: List<ScreenshotEntity>)

    @Query("UPDATE screenshots SET relativePath = :relativePath, displayName = :displayName WHERE mediaId = :mediaId")
    abstract suspend fun updateLocation(mediaId: Long, relativePath: String, displayName: String)

    @Query("DELETE FROM screenshots WHERE mediaId IN (:ids)")
    abstract suspend fun deleteByIds(ids: List<Long>)

    // ---- OCR ----

    @Query("SELECT * FROM screenshots WHERE ocrDone = 0 ORDER BY capturedAt DESC LIMIT :limit")
    abstract suspend fun getPendingOcr(limit: Int): List<ScreenshotEntity>

    @Query("UPDATE screenshots SET ocrText = :text, ocrDone = 1 WHERE mediaId = :mediaId")
    abstract suspend fun saveOcrResult(mediaId: Long, text: String?)

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrDone = 0")
    abstract fun observePendingOcrCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrDone = 0")
    abstract suspend fun countPendingOcr(): Int

    /** Applies all sync changes at once: either everything succeeds or nothing changes. */
    @Transaction
    open suspend fun applySync(
        toInsert: List<ScreenshotEntity>,
        moved: List<ScreenshotLocation>,
        removedIds: List<Long>
    ) {
        if (toInsert.isNotEmpty()) insertAll(toInsert)
        moved.forEach { updateLocation(it.mediaId, it.relativePath, it.displayName) }
        // Delete in chunks to stay under SQLite's limit on query parameters
        removedIds.chunked(500).forEach { deleteByIds(it) }
    }
}