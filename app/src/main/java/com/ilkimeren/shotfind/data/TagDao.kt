package com.ilkimeren.shotfind.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY sortOrder")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags WHERE folderName = :folderName LIMIT 1")
    suspend fun getByFolder(folderName: String): TagEntity?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    // IGNORE: if two callers try to add the defaults at the same time, duplicates are skipped
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>)
}