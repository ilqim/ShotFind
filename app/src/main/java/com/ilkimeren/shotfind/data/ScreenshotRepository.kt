package com.ilkimeren.shotfind.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import androidx.sqlite.db.SimpleSQLiteQuery
import kotlinx.coroutines.flow.flowOf
import android.content.ContentValues
import android.content.IntentSender

class ScreenshotRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = ShotFindDatabase.getInstance(appContext).screenshotDao()
    private val tagDao = ShotFindDatabase.getInstance(appContext).tagDao()

    /** Screenshots directly in the system screenshot folder, updated live from the database. */
    fun observeInbox(): Flow<List<Screenshot>> =
        dao.observeByFolders(INBOX_FOLDERS).map { entities ->
            entities.map { it.toScreenshot() }
        }

    fun observeTotalCount(): Flow<Int> = dao.observeCount()
    fun observePendingOcrCount(): Flow<Int> = dao.observePendingOcrCount()

    fun observeScreenshot(mediaId: Long): Flow<Screenshot?> =
        dao.observeById(mediaId).map { it?.toScreenshot() }

    /** Saves the note and refreshes the search text so the note becomes searchable. */
    suspend fun saveNote(mediaId: Long, note: String) {
        val entity = dao.getById(mediaId) ?: return
        val cleaned = note.trim().ifBlank { null }
        val searchText = SearchNormalizer.buildSearchText(entity.ocrText, cleaned, entity.sourceApp)
        dao.updateNote(mediaId, cleaned, searchText)
    }

    suspend fun markTrashed(mediaId: Long) {
        dao.markTrashed(mediaId, System.currentTimeMillis())
    }

    suspend fun markRestored(mediaId: Long) {
        dao.clearTrashed(listOf(mediaId))
    }

    /** Screenshots we moved to the trash, most recently trashed first. */
    fun observeTrashed(): Flow<List<Screenshot>> =
        dao.observeTrashed().map { entities -> entities.map { it.toScreenshot() } }

    fun observeFolderSummaries(): Flow<List<FolderSummary>> =
        dao.observeFolderSummaries(INBOX_FOLDERS)

    fun observeKeptCount(): Flow<Int> = dao.observeKeptCount(INBOX_FOLDERS)

    fun observeTrashedCount(): Flow<Int> = dao.observeTrashedCount()

    /** Screenshots in a tag folder, e.g. "Ders" -> DCIM/Screenshots/Ders/ */
    fun observeTagAlbum(folderName: String): Flow<List<Screenshot>> =
        dao.observeInFolders(INBOX_FOLDERS.map { "$it$folderName/" })
            .map { entities -> entities.map { it.toScreenshot() } }

    /** Screenshots the user kept while sorting (still in the screenshot folder). */
    fun observeKept(): Flow<List<Screenshot>> =
        dao.observeKept(INBOX_FOLDERS).map { entities -> entities.map { it.toScreenshot() } }

    /** Screenshots that have been in a temporary tag for longer than the retention time. */
    suspend fun findExpiredTemporary(): List<Screenshot> {
        val temporaryTags = tagDao.getTemporary()
        if (temporaryTags.isEmpty()) return emptyList()
        val folders = temporaryTags.flatMap { tag -> INBOX_FOLDERS.map { "$it${tag.folderName}/" } }
        val before = System.currentTimeMillis() - TEMP_RETENTION_MS
        return dao.getExpiredTemp(folders, before).map { it.toScreenshot() }
    }

    suspend fun firstTemporaryTagId(): Long? = tagDao.getTemporary().firstOrNull()?.id

    /** System dialog to take several screenshots out of the trash at once. */
    fun createRestoreRequest(uris: List<android.net.Uri>): IntentSender =
        MediaStore.createTrashRequest(appContext.contentResolver, uris, false).intentSender

    suspend fun markRestored(ids: List<Long>) {
        dao.clearTrashed(ids)
    }

    suspend fun markKept(mediaId: Long) {
        dao.markKept(mediaId, System.currentTimeMillis())
    }

    suspend fun clearKept(mediaId: Long) {
        dao.clearKept(mediaId)
    }

    /** System dialog to move screenshots to the trash (skipped with media management). */
    fun createTrashRequest(uris: List<android.net.Uri>): IntentSender =
        MediaStore.createTrashRequest(appContext.contentResolver, uris, true).intentSender

    suspend fun getScreenshot(mediaId: Long): Screenshot? =
        dao.getById(mediaId)?.toScreenshot()

    /**
     * Guesses the most likely tag: the one most used for this app, else the one most
     * used overall, else "Sonra Bak". Learns from where the user files screenshots.
     */
    suspend fun suggestTag(screenshot: Screenshot): TagEntity? {
        val tags = TagRepository(appContext)
        tags.ensureDefaultTags()

        val folderPath = screenshot.sourceApp?.let { dao.mostUsedFolderForApp(it, INBOX_FOLDERS) }
            ?: dao.mostUsedFolder(INBOX_FOLDERS)

        // Only suggest folders that are real tags (the user may have made other subfolders)
        val fromHistory = folderPath
            ?.let { tagFolderOf(it) }
            ?.let { tags.getByFolder(it) }

        return fromHistory ?: tags.getByFolder(FALLBACK_TAG_FOLDER)
    }

    /** True if the user granted media management, so moves need no confirmation. */
    fun canMoveWithoutAsking(): Boolean = MediaStore.canManageMedia(appContext)

    /** System dialog asking the user to let us change this file. */
    fun createWriteRequest(uri: android.net.Uri): IntentSender =
        MediaStore.createWriteRequest(appContext.contentResolver, listOf(uri)).intentSender

    /** One system dialog for several screenshots at once. */
    fun createWriteRequest(uris: List<android.net.Uri>): IntentSender =
        MediaStore.createWriteRequest(appContext.contentResolver, uris).intentSender

    /**
     * Moves the screenshot into a tag folder (e.g. DCIM/Screenshots/Ders/),
     * or back to the inbox when [tagFolder] is null. Returns false if it failed.
     */
    suspend fun moveToFolder(screenshot: Screenshot, tagFolder: String?): Boolean =
        withContext(Dispatchers.IO) {
            val base = baseFolderOf(screenshot.relativePath)
            val target = if (tagFolder == null) base else "$base$tagFolder/"
            if (target == screenshot.relativePath) return@withContext true

            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.RELATIVE_PATH, target)
            }
            val updatedRows = try {
                appContext.contentResolver.update(screenshot.uri, values, null, null)
            } catch (e: SecurityException) {
                0 // no write access
            }

            if (updatedRows > 0) {
                dao.updateLocation(screenshot.id, target, screenshot.displayName)
                // Start the clock when it enters a temporary tag; clear it otherwise
                val isTemporary = tagFolder != null && tagDao.getByFolder(tagFolder)?.isTemporary == true
                dao.setTempSince(screenshot.id, if (isTemporary) System.currentTimeMillis() else null)
                true
            } else {
                false
            }
        }

    /**
     * Screenshots whose text contains every word of the query (in any order),
     * newest first. Updates live as OCR processes more screenshots.
     */
    fun search(query: String): Flow<List<SearchResult>> {
        val words = SearchNormalizer.normalize(query)
            .split(Regex("\\s+"))
            .filter { it.isNotBlank() }
            .take(MAX_SEARCH_WORDS)
        if (words.isEmpty()) return flowOf(emptyList())

        // One "searchText LIKE ?" per word, joined with AND, excluding trashed items
        val where = "trashedAt IS NULL AND " + words.joinToString(" AND ") { "searchText LIKE ? ESCAPE '\\'" }
        val args: Array<Any> = words.map { "%${escapeLike(it)}%" }.toTypedArray()
        val sql = SimpleSQLiteQuery(
            "SELECT * FROM screenshots WHERE $where ORDER BY capturedAt DESC LIMIT $MAX_RESULTS",
            args
        )

        return dao.observeSearch(sql).map { entities ->
            entities.map { SearchResult(it.toScreenshot(), it.ocrText) }
        }
    }

    /** % and _ are wildcards in LIKE; escape them so they are matched literally. */
    private fun escapeLike(text: String): String =
        text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")

    /**
     * Makes the database match MediaStore: adds new screenshots, updates moved or
     * renamed ones, removes deleted ones. Notes and OCR text are kept.
     * Returns the screenshots that were newly added.
     */
    suspend fun syncFromMediaStore(): List<Screenshot> = withContext(Dispatchers.IO) {
        val fromStore = queryAllScreenshots()
        val existing = dao.getAllLocations().associateBy { it.mediaId }

        val toInsert = fromStore.filter { it.mediaId !in existing }

        val moved = fromStore.mapNotNull { item ->
            val old = existing[item.mediaId] ?: return@mapNotNull null
            if (old.relativePath != item.relativePath || old.displayName != item.displayName) {
                ScreenshotLocation(item.mediaId, item.relativePath, item.displayName)
            } else {
                null
            }
        }

        val storeIds = fromStore.mapTo(HashSet()) { it.mediaId }

        // Trashed items are hidden from MediaStore queries; don't treat them as deleted
        val trashedIds = dao.getTrashedIds().toSet()
        val removedIds = existing.keys.filter { it !in storeIds && it !in trashedIds }

        // Visible again means it was restored outside the app (e.g. from a file manager)
        val restoredIds = trashedIds.filter { it in storeIds }

        dao.applySync(toInsert, moved, removedIds, restoredIds)
        dao.deleteExpiredTrash(System.currentTimeMillis() - TRASH_RETENTION_MS)

        toInsert.map { it.toScreenshot() }
    }

    /**
     * Cheap check for the background watcher: only looks at screenshots added after the
     * newest one we know. Moves and deletions are handled by the full sync on app start.
     */
    suspend fun syncNewScreenshots(): List<Screenshot> = withContext(Dispatchers.IO) {
        val maxKnown = dao.maxMediaId()
            ?: return@withContext syncFromMediaStore() // empty database: do a full sync

        // A small overlap catches screenshots that got their id slightly earlier
        // but became visible later (e.g. two screenshots saved at the same time)
        val candidates = queryAllScreenshots(minIdExclusive = maxKnown - ID_OVERLAP)
        if (candidates.isEmpty()) return@withContext emptyList()

        val known = dao.existingIds(candidates.map { it.mediaId }).toSet()
        val toInsert = candidates.filter { it.mediaId !in known }
        if (toInsert.isNotEmpty()) dao.insertAll(toInsert)
        toInsert.map { it.toScreenshot() }
    }

    /** All screenshots, including those in our tag subfolders (e.g. DCIM/Screenshots/Ders/). */
    private fun queryAllScreenshots(minIdExclusive: Long? = null): List<ScreenshotEntity> {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.DATE_TAKEN,
            MediaStore.Images.Media.SIZE,
            MediaStore.Images.Media.WIDTH,
            MediaStore.Images.Media.HEIGHT,
            MediaStore.Images.Media.RELATIVE_PATH
        )

        val path = MediaStore.Images.Media.RELATIVE_PATH
        var selection = "($path LIKE ? OR $path LIKE ?)"
        var selectionArgs = arrayOf("DCIM/Screenshots/%", "Pictures/Screenshots/%")
        if (minIdExclusive != null) {
            selection += " AND ${MediaStore.Images.Media._ID} > ?"
            selectionArgs += minIdExclusive.toString()
        }

        val result = mutableListOf<ScreenshotEntity>()

        appContext.contentResolver.query(
            collection, projection, selection, selectionArgs, null
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val takenCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_TAKEN)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)
            val widthCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
            val heightCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.RELATIVE_PATH)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: ""
                val sourceApp = ScreenshotNameParser.parseSourceApp(name)
                val dateAddedMillis = cursor.getLong(dateCol) * 1000 // stored in seconds
                val dateTaken = cursor.getLong(takenCol)             // already in ms, 0 if missing
                val capturedAt = ScreenshotNameParser.parseCapturedAt(name)
                    ?: dateTaken.takeIf { it > 0 }
                    ?: dateAddedMillis
                result += ScreenshotEntity(
                    mediaId = id,
                    uri = ContentUris.withAppendedId(collection, id).toString(),
                    displayName = name,
                    dateAdded = dateAddedMillis,
                    capturedAt = capturedAt,
                    sizeBytes = cursor.getLong(sizeCol),
                    width = cursor.getInt(widthCol),
                    height = cursor.getInt(heightCol),
                    relativePath = cursor.getString(pathCol) ?: "",
                    sourceApp = sourceApp,
                    searchText = sourceApp?.let { SearchNormalizer.normalize(it) },
                )
            }
        }

        return result
    }

    companion object {
        // Samsung uses DCIM/Screenshots/, stock Android uses Pictures/Screenshots/
        val INBOX_FOLDERS = listOf("DCIM/Screenshots/", "Pictures/Screenshots/")
        private const val MAX_SEARCH_WORDS = 8
        const val TEMP_RETENTION_MS = 7L * 24 * 60 * 60 * 1000 // 7 days
        private const val ID_OVERLAP = 100L
        private const val MAX_RESULTS = 300

        private const val TRASH_RETENTION_MS = 30L * 24 * 60 * 60 * 1000 // 30 days
        private const val FALLBACK_TAG_FOLDER = "Sonra Bak"

        /** "DCIM/Screenshots/Ders/" -> "DCIM/Screenshots/" */
        fun baseFolderOf(relativePath: String): String =
            INBOX_FOLDERS.firstOrNull { relativePath.startsWith(it) } ?: INBOX_FOLDERS.first()

        /** "DCIM/Screenshots/Ders/" -> "Ders", "DCIM/Screenshots/" -> null */
        fun tagFolderOf(relativePath: String): String? {
            val base = INBOX_FOLDERS.firstOrNull { relativePath.startsWith(it) } ?: return null
            return relativePath.removePrefix(base)
                .trimEnd('/')
                .substringBefore('/')
                .ifBlank { null }
        }
    }
}