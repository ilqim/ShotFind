package com.ilkimeren.shotfind.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {

    @Query("SELECT * FROM tags ORDER BY sortOrder")
    fun observeAll(): Flow<List<TagEntity>>

    @Query("SELECT * FROM tags")
    suspend fun getAll(): List<TagEntity>

    @Query("SELECT * FROM tags WHERE id = :id")
    fun observeById(id: Long): Flow<TagEntity?>

    @Query("SELECT * FROM tags WHERE id = :id")
    suspend fun getById(id: Long): TagEntity?

    @Query("SELECT * FROM tags WHERE folderName = :folderName LIMIT 1")
    suspend fun getByFolder(folderName: String): TagEntity?

    @Query("SELECT COUNT(*) FROM tags")
    suspend fun count(): Int

    @Query("SELECT * FROM tags WHERE isTemporary = 1")
    suspend fun getTemporary(): List<TagEntity>

    /** -1 when there are no tags yet. */
    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM tags")
    suspend fun maxSortOrder(): Int

    // IGNORE: if two callers try to add the defaults at the same time, duplicates are skipped
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(tags: List<TagEntity>)

    @Insert
    suspend fun insert(tag: TagEntity): Long

    @Update
    suspend fun update(tag: TagEntity)

    @Delete
    suspend fun delete(tag: TagEntity)
}