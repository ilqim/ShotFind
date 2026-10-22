package com.ilkimeren.shotfind.data

import android.content.Context
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onStart

class TagRepository(context: Context) {

    private val dao = ShotFindDatabase.getInstance(context.applicationContext).tagDao()

    /** All tags in display order. Adds the default tags the first time. */
    fun observeTags(): Flow<List<TagEntity>> =
        dao.observeAll().onStart { ensureDefaultTags() }

    fun observeById(id: Long): Flow<TagEntity?> = dao.observeById(id)

    suspend fun ensureDefaultTags() {
        if (dao.count() == 0) dao.insertAll(DEFAULT_TAGS)
    }

    suspend fun getById(id: Long): TagEntity? = dao.getById(id)

    suspend fun getByFolder(folderName: String): TagEntity? = dao.getByFolder(folderName)

    /**
     * Returns an error message if [name] can't be used, or null if it's fine.
     * [excludingId] skips the tag being renamed, so keeping its own name is allowed.
     */
    suspend fun checkName(name: String, excludingId: Long? = null): String? {
        val folder = folderNameFor(name)
        if (folder.isBlank()) return "Etiket adı boş olamaz"
        // Phone storage ignores upper/lower case: "Ders" and "ders" are the same folder
        val taken = dao.getAll().any { it.id != excludingId && it.folderName.equals(folder, ignoreCase = true) }
        return if (taken) "Bu adda bir etiket zaten var" else null
    }

    /** Creates a tag. Returns an error message, or null on success. */
    suspend fun createTag(name: String, colorArgb: Int): String? {
        checkName(name)?.let { return it }
        dao.insert(
            TagEntity(
                name = name.trim(),
                colorArgb = colorArgb,
                folderName = folderNameFor(name),
                sortOrder = dao.maxSortOrder() + 1
            )
        )
        return null
    }

    suspend fun update(tag: TagEntity) = dao.update(tag)

    suspend fun delete(tag: TagEntity) = dao.delete(tag)

    companion object {
        /** Turns a tag name into a safe folder name. */
        fun folderNameFor(name: String): String =
            name.trim()
                .replace(Regex("""[\\/:*?"<>|]"""), "") // not allowed in folder names
                .replace(Regex("\\s+"), " ")             // collapse repeated spaces
                .trimStart('.')                          // a leading dot would hide the folder
                .take(40)
                .trim()

        val COLOR_PALETTE = listOf(
            0xFF3F51B5.toInt(), // indigo
            0xFFE91E63.toInt(), // pink
            0xFFFF9800.toInt(), // orange
            0xFF9C27B0.toInt(), // purple
            0xFF009688.toInt(), // teal
            0xFF2196F3.toInt(), // blue
            0xFF795548.toInt(), // brown
            0xFF4CAF50.toInt(), // green
            0xFFF44336.toInt(), // red
            0xFF607D8B.toInt()  // blue grey
        )

        private val DEFAULT_TAGS = listOf(
            TagEntity(name = "Ders", colorArgb = COLOR_PALETTE[0], folderName = "Ders", sortOrder = 0),
            TagEntity(name = "Alışveriş", colorArgb = COLOR_PALETTE[1], folderName = "Alışveriş", sortOrder = 1),
            TagEntity(name = "Tarif", colorArgb = COLOR_PALETTE[2], folderName = "Tarif", sortOrder = 2),
            TagEntity(name = "Sosyal", colorArgb = COLOR_PALETTE[3], folderName = "Sosyal", sortOrder = 3),
            TagEntity(name = "Belge", colorArgb = COLOR_PALETTE[4], folderName = "Belge", sortOrder = 4),
            TagEntity(name = "Sonra Bak", colorArgb = COLOR_PALETTE[5], folderName = "Sonra Bak", sortOrder = 5),
            TagEntity(name = "Geçici", colorArgb = COLOR_PALETTE[6], folderName = "Geçici", sortOrder = 6, isTemporary = true)        )
    }
}