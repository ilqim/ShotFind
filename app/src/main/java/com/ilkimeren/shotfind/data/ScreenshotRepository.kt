package com.ilkimeren.shotfind.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ScreenshotRepository(context: Context) {

    private val appContext = context.applicationContext
    private val dao = ShotFindDatabase.getInstance(appContext).screenshotDao()

    /** Screenshots directly in the system screenshot folder, updated live from the database. */
    fun observeInbox(): Flow<List<Screenshot>> =
        dao.observeByFolders(INBOX_FOLDERS).map { entities ->
            entities.map { it.toScreenshot() }
        }

    fun observeTotalCount(): Flow<Int> = dao.observeCount()

    /**
     * Makes the database match MediaStore: adds new screenshots, updates moved or
     * renamed ones, removes deleted ones. Notes and OCR text are kept.
     */
    suspend fun syncFromMediaStore() {
        withContext(Dispatchers.IO) {
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
            val removedIds = existing.keys.filter { it !in storeIds }

            dao.applySync(toInsert, moved, removedIds)
        }
    }

    /** All screenshots, including those in our tag subfolders (e.g. DCIM/Screenshots/Ders/). */
    private fun queryAllScreenshots(): List<ScreenshotEntity> {
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
        val selection = "$path LIKE ? OR $path LIKE ?"
        val selectionArgs = arrayOf("DCIM/Screenshots/%", "Pictures/Screenshots/%")

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
                    sourceApp = ScreenshotNameParser.parseSourceApp(name)
                )
            }
        }

        return result
    }

    companion object {
        // Samsung uses DCIM/Screenshots/, stock Android uses Pictures/Screenshots/
        val INBOX_FOLDERS = listOf("DCIM/Screenshots/", "Pictures/Screenshots/")
    }
}