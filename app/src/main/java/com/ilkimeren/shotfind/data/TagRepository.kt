package com.ilkimeren.shotfind.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class TagRepository(context: Context) {

    private val dao = ShotFindDatabase.getInstance(context.applicationContext).tagDao()

    /** All tags in display order. Adds the default tags the first time. */
    fun observeTags(): Flow<List<TagEntity>> =
        dao.observeAll().onStart { ensureDefaultTags() }

    private suspend fun ensureDefaultTags() {
        if (dao.count() == 0) dao.insertAll(DEFAULT_TAGS)
    }

    private companion object {
        val DEFAULT_TAGS = listOf(
            TagEntity(name = "Ders", colorArgb = 0xFF3F51B5.toInt(), folderName = "Ders", sortOrder = 0),
            TagEntity(name = "Alışveriş", colorArgb = 0xFFE91E63.toInt(), folderName = "Alışveriş", sortOrder = 1),
            TagEntity(name = "Tarif", colorArgb = 0xFFFF9800.toInt(), folderName = "Tarif", sortOrder = 2),
            TagEntity(name = "Sosyal", colorArgb = 0xFF9C27B0.toInt(), folderName = "Sosyal", sortOrder = 3),
            TagEntity(name = "Belge", colorArgb = 0xFF009688.toInt(), folderName = "Belge", sortOrder = 4),
            TagEntity(name = "Sonra Bak", colorArgb = 0xFF2196F3.toInt(), folderName = "Sonra Bak", sortOrder = 5),
            TagEntity(name = "Geçici", colorArgb = 0xFF795548.toInt(), folderName = "Geçici", sortOrder = 6)
        )
    }
}