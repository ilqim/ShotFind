package com.ilkimeren.shotfind.data

/** Count and cover image of one folder, computed by the database. */
data class FolderSummary(
    val relativePath: String,
    val count: Int,
    val coverUri: String?
)