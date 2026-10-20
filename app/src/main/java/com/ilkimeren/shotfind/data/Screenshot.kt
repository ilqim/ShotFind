package com.ilkimeren.shotfind.data

import android.net.Uri

/** A screenshot as read from MediaStore. */
data class Screenshot(
    val id: Long,              // MediaStore row id
    val uri: Uri,              // content://... address used to open the image
    val displayName: String,   // file name
    val dateAddedMillis: Long,
    val capturedAtMillis: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val relativePath: String,  // e.g. "DCIM/Screenshots/"
    val sourceApp: String?,     // parsed from file name, null if unknown
    val note: String?,
    val ocrText: String?,
    val trashedAt: Long?
)