package com.ilkimeren.shotfind.data

import androidx.core.net.toUri
import androidx.room.Entity
import androidx.room.PrimaryKey

/** One row in the "screenshots" table. */
@Entity(tableName = "screenshots")
data class ScreenshotEntity(
    @PrimaryKey val mediaId: Long,     // same id as in MediaStore
    val uri: String,
    val displayName: String,
    val dateAdded: Long,               // epoch milliseconds
    val capturedAt: Long,              // best known capture time, used for sorting
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,
    val sourceApp: String?,
    val note: String? = null,          // filled in a later step
    val ocrText: String? = null,       // filled by OCR in the next step
    val ocrDone: Boolean = false,
    val searchText: String? = null,     // normalized OCR text + note + source app, used for search
    val trashedAt: Long? = null,        // set when we moved it to the system trash
    val keptAt: Long? = null           // set when the user chose "keep" while sorting
)

/** Just the columns needed to detect moved or renamed files during sync. */
data class ScreenshotLocation(
    val mediaId: Long,
    val relativePath: String,
    val displayName: String
)

fun ScreenshotEntity.toScreenshot() = Screenshot(
    id = mediaId,
    uri = uri.toUri(),
    displayName = displayName,
    dateAddedMillis = dateAdded,
    capturedAtMillis = capturedAt,
    sizeBytes = sizeBytes,
    width = width,
    height = height,
    relativePath = relativePath,
    sourceApp = sourceApp,
    note = note,
    ocrText = ocrText,
    trashedAt = trashedAt
)