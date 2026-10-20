package com.ilkimeren.shotfind.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** A tag is a real folder: DCIM/Screenshots/<folderName>/ */
@Entity(
    tableName = "tags",
    indices = [Index(value = ["folderName"], unique = true)]
)
data class TagEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val colorArgb: Int,
    val folderName: String,
    val sortOrder: Int
)