package com.ilkimeren.shotfind.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Transaction
import androidx.sqlite.db.SupportSQLiteQuery
import kotlinx.coroutines.flow.Flow

@Dao
abstract class ScreenshotDao {

    // ---- Inbox and counts ----

    @Query("SELECT * FROM screenshots WHERE relativePath IN (:folders) AND trashedAt IS NULL AND keptAt IS NULL ORDER BY capturedAt DESC")
    abstract fun observeByFolders(folders: List<String>): Flow<List<ScreenshotEntity>>

    @Query("SELECT COUNT(*) FROM screenshots WHERE trashedAt IS NULL")
    abstract fun observeCount(): Flow<Int>

    // ---- Sync ----

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

    @Query("SELECT * FROM screenshots WHERE ocrDone = 0 AND trashedAt IS NULL ORDER BY capturedAt DESC LIMIT :limit")
    abstract suspend fun getPendingOcr(limit: Int): List<ScreenshotEntity>

    @Query("UPDATE screenshots SET ocrText = :text, searchText = :searchText, ocrDone = 1 WHERE mediaId = :mediaId")
    abstract suspend fun saveOcrResult(mediaId: Long, text: String?, searchText: String?)

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrDone = 0 AND trashedAt IS NULL")
    abstract fun observePendingOcrCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM screenshots WHERE ocrDone = 0 AND trashedAt IS NULL")
    abstract suspend fun countPendingOcr(): Int

    // ---- Search ----

    // The query is built at runtime because the number of search words varies
    @RawQuery(observedEntities = [ScreenshotEntity::class])
    abstract fun observeSearch(query: SupportSQLiteQuery): Flow<List<ScreenshotEntity>>

    // ---- Incremental sync ----

    @Query("SELECT MAX(mediaId) FROM screenshots")
    abstract suspend fun maxMediaId(): Long?

    @Query("SELECT mediaId FROM screenshots WHERE mediaId IN (:ids)")
    abstract suspend fun existingIds(ids: List<Long>): List<Long>

    // ---- Tag suggestions ----

    /** The tag folder the user most often uses for screenshots from this app. */
    @Query(
        """
        SELECT relativePath FROM screenshots
        WHERE sourceApp = :sourceApp AND relativePath NOT IN (:inboxFolders) AND trashedAt IS NULL
        GROUP BY relativePath
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """
    )
    abstract suspend fun mostUsedFolderForApp(sourceApp: String, inboxFolders: List<String>): String?

    /** The tag folder used most often overall. */
    @Query(
        """
        SELECT relativePath FROM screenshots
        WHERE relativePath NOT IN (:inboxFolders) AND trashedAt IS NULL
        GROUP BY relativePath
        ORDER BY COUNT(*) DESC
        LIMIT 1
        """
    )
    abstract suspend fun mostUsedFolder(inboxFolders: List<String>): String?

    // ---- Sorting ----

    @Query("UPDATE screenshots SET keptAt = :keptAt WHERE mediaId = :mediaId")
    abstract suspend fun markKept(mediaId: Long, keptAt: Long)

    @Query("UPDATE screenshots SET keptAt = NULL WHERE mediaId = :mediaId")
    abstract suspend fun clearKept(mediaId: Long)

    // ---- Trash ----

    @Query("SELECT * FROM screenshots WHERE trashedAt IS NOT NULL ORDER BY trashedAt DESC")
    abstract fun observeTrashed(): Flow<List<ScreenshotEntity>>
    @Query("UPDATE screenshots SET trashedAt = :trashedAt WHERE mediaId = :mediaId")
    abstract suspend fun markTrashed(mediaId: Long, trashedAt: Long)

    @Query("UPDATE screenshots SET trashedAt = NULL WHERE mediaId IN (:ids)")
    abstract suspend fun clearTrashed(ids: List<Long>)

    @Query("SELECT mediaId FROM screenshots WHERE trashedAt IS NOT NULL")
    abstract suspend fun getTrashedIds(): List<Long>

    /** Android permanently deletes trashed items after 30 days; forget them too. */
    @Query("DELETE FROM screenshots WHERE trashedAt IS NOT NULL AND trashedAt < :before")
    abstract suspend fun deleteExpiredTrash(before: Long)

    // ---- Single screenshot ----

    @Query("SELECT * FROM screenshots WHERE mediaId = :mediaId")
    abstract fun observeById(mediaId: Long): Flow<ScreenshotEntity?>

    @Query("SELECT * FROM screenshots WHERE mediaId = :mediaId")
    abstract suspend fun getById(mediaId: Long): ScreenshotEntity?

    @Query("UPDATE screenshots SET note = :note, searchText = :searchText WHERE mediaId = :mediaId")
    abstract suspend fun updateNote(mediaId: Long, note: String?, searchText: String?)

    // ---- Sync transaction ----

    /** Applies all sync changes at once: either everything succeeds or nothing changes. */
    @Transaction
    open suspend fun applySync(
        toInsert: List<ScreenshotEntity>,
        moved: List<ScreenshotLocation>,
        removedIds: List<Long>,
        restoredIds: List<Long>
    ) {
        if (toInsert.isNotEmpty()) insertAll(toInsert)
        moved.forEach { updateLocation(it.mediaId, it.relativePath, it.displayName) }
        // Delete in chunks to stay under SQLite's limit on query parameters
        removedIds.chunked(500).forEach { deleteByIds(it) }
        restoredIds.chunked(500).forEach { clearTrashed(it) }
    }
}